import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { arm64Image, assertSameImage, commandStatus, deploymentArchives, fixtures, harnessArchive, pollCommand, shellQuote, waitForObjectCleanup } from '../runtime/aws.ts';
import { waitForAwsReadiness } from '../runtime/aws-config.ts';
import { selectConfiguration } from '../runtime/configuration.ts';

test('ARM64 image resolution excludes AMD64 and attestation manifests', () => {
    const digest = `sha256:${'a'.repeat(64)}`;
    const resolved = arm64Image('0.14.0', { manifests: [
        { digest: `sha256:${'b'.repeat(64)}`, platform: { architecture: 'amd64', os: 'linux' } },
        { digest: `sha256:${'c'.repeat(64)}`, platform: { architecture: 'unknown', os: 'unknown' } },
        { digest, platform: { architecture: 'arm64', os: 'linux' } },
    ] });
    assert.equal(resolved.reference, `ghcr.io/dmaiken/konifer@${digest}`);
    assert.throws(() => arm64Image('missing', { manifests: [] }), /No linux\/arm64/);
    assert.throws(() => arm64Image('$(id)', { manifests: [] }), /Invalid Docker tag/);
});

test('shell quoting keeps command substitutions and quotes literal', () => {
    assert.equal(shellQuote("a'$(id)\n`whoami`"), "'a'\\''$(id)\n`whoami`'");
});

test('initialized databases reject image changes', () => {
    assert.doesNotThrow(() => assertSameImage('sha256:a', 'sha256:a'));
    assert.throws(() => assertSameImage('sha256:a', 'sha256:b'), /Destroy and recreate/);
});

test('SSM terminal failures and nonzero response codes propagate', async () => {
    for (const status of ['Failed', 'Cancelled', 'TimedOut', 'DeliveryTimedOut', 'Undeliverable', 'Terminated', 'Unknown']) {
        assert.equal(commandStatus(status), 'failure');
        await assert.rejects(pollCommand(async () => ({ Status: status, ResponseCode: 1, StandardOutputContent: '', StandardErrorContent: 'diagnostic' }), async () => undefined), /diagnostic/);
    }
    await assert.rejects(pollCommand(async () => ({ Status: 'Success', ResponseCode: 2, StandardOutputContent: '', StandardErrorContent: 'bad exit' }), async () => undefined), /bad exit/);
});

test('SSM polling handles eventual success and bounded timeout', async () => {
    let calls = 0;
    const output = await pollCommand(async () => ({ Status: ++calls === 3 ? 'Success' : 'InProgress', ResponseCode: calls === 3 ? 0 : -1, StandardOutputContent: 'checkpoint', StandardErrorContent: '' }), async () => undefined, 3);
    assert.equal(output, 'checkpoint');
    assert.equal(calls, 3);
    await assert.rejects(pollCommand(async () => ({ Status: 'Pending', ResponseCode: -1, StandardOutputContent: '', StandardErrorContent: '' }), async () => undefined, 2), /timed out/);
});

test('missing and corrupted fixtures stop preparation', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'konifer-fixture-test-'));
    try {
        await mkdir(path.join(directory, 'assets'));
        await writeFile(path.join(directory, 'assets/manifest.json'), JSON.stringify({ fixtures: { fixture: { files: { jpg: { path: 'missing.jpg', sha256: 'incorrect' } } } } }));
        await assert.rejects(fixtures(directory), /ENOENT/);
        await writeFile(path.join(directory, 'assets/missing.jpg'), 'corrupted fixture');
        await assert.rejects(fixtures(directory), /Fixture hash mismatch/);
    } finally { await rm(directory, { recursive: true, force: true }); }
});

test('scratch preparation refuses mounted EBS and signature-bearing instance storage', async () => {
    const hostScript = fileURLToPath(new URL('../runtime/aws-host.sh', import.meta.url));
    const mountedEbs = `source ${shellQuote(hostScript)}
mountpoint() { return 0; }
findmnt() { if [[ $* == *FSTYPE* ]]; then echo ext4; else echo /dev/ebs; fi; }
lsblk() { echo 'Amazon Elastic Block Store'; }
prepare_scratch`;
    await assert.rejects(promisify(execFile)('bash', ['-c', mountedEbs]), /Unexpected scratch mount/);
    const nonblank = `source ${shellQuote(hostScript)}
mountpoint() { return 1; }
lsblk() { if [[ $* == *--json* ]]; then echo '{"blockdevices":[{"path":"/dev/loop0","model":null},{"path":"/dev/example","model":"Amazon EC2 NVMe Instance Storage"}]}'; else echo /dev/example; fi; }
wipefs() { echo '{"signatures":[{"type":"ext4"}]}'; }
prepare_scratch`;
    await assert.rejects(promisify(execFile)('bash', ['-c', nonblank]), /not blank/);
});

test('readiness supports historical routes without bypassing unhealthy modern probes', async () => {
    let modernStatus = 404;
    const requests: string[] = [];
    const server = createServer((request, response) => {
        requests.push(request.url!);
        response.writeHead(request.url === '/health/ready' ? modernStatus : 200).end();
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
        const address = server.address() as { port: number };
        const baseUrl = `http://127.0.0.1:${address.port}`;
        assert.equal(await waitForAwsReadiness(baseUrl, 2, 0), '/health');
        assert.deepEqual(requests, ['/health/ready', '/health']);
        requests.length = 0;
        modernStatus = 503;
        await assert.rejects(waitForAwsReadiness(baseUrl, 2, 0), /did not become ready/);
        assert.deepEqual(requests, ['/health/ready', '/health/ready']);
    } finally { server.closeAllConnections(); await new Promise<void>((resolve) => server.close(() => resolve())); }
});

test('checkpoint waits for asynchronous S3 deletion without requiring existing objects to disappear', async () => {
    let reads = 0;
    const baseline = new Set(['existing-object']);
    await waitForObjectCleanup(baseline, async () => ++reads === 3 ? ['existing-object'] : ['existing-object', 'checkpoint-object'], async () => undefined, 3);
    assert.equal(reads, 3);
    await assert.rejects(waitForObjectCleanup(baseline, async () => ['existing-object', 'checkpoint-object'], async () => undefined, 2), /objects remain in S3/);
});

test('deployment archives keep supplied configuration off the shared k6 bundle', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-deployment-test-'));
    const configDirectory = fileURLToPath(new URL('./', import.meta.url));
    const selectedDirectory = await mkdtemp(path.join(configDirectory, '.selection-test-'));
    try {
        await mkdir(path.join(temporary, 'generated'));
        const file = path.join(selectedDirectory, 'private[old] config.conf');
        await writeFile(file, 'old-garage-password = "not-for-k6"');
        await writeFile(path.join(temporary, 'generated/konifer.source.conf'), 'old-garage-password = "not-for-k6"');
        await writeFile(path.join(temporary, 'generated/konifer.conf'), 'include "konifer.source.conf"');
        await writeFile(path.join(temporary, 'generated/konifer.aws.conf'), 'environment = aws');
        await writeFile(path.join(temporary, 'generated/deployment.json'), '{"configuration":{"sourceSha256":"hash-only"}}');
        await writeFile(path.join(temporary, 'manifest.json'), '{"configuration":{"sourceSha256":"hash-only"}}');
        const { application, metadata } = await deploymentArchives(temporary);
        assert.match(application.key, /^konifer\/deployments\/[a-f0-9]{64}\.tar$/);
        assert.match(metadata.key, /^shared\/deployments\/[a-f0-9]{64}\.tar$/);
        const execute = promisify(execFile);
        assert.match((await execute('tar', ['-tf', application.file])).stdout, /generated\/konifer.source.conf/);
        assert.deepEqual((await execute('tar', ['-tf', metadata.file])).stdout.trim().split('\n'), ['manifest.json', 'generated/deployment.json']);
        const archive = path.join(temporary, 'harness.tar');
        await harnessArchive(archive, await selectConfiguration(file));
        const names = (await execute('tar', ['-tf', archive])).stdout;
        assert.doesNotMatch(names, /private\[old\] config|konifer\.source\.conf|runtime\/konifer\.conf/);
        assert.match(names, /runtime\/configuration.ts/);
        assert.match(names, /config\/environments.json/);
    } finally {
        await rm(temporary, { recursive: true, force: true });
        await rm(selectedDirectory, { recursive: true, force: true });
    }
});

test('host grants the image user ownership of every composed config file', async () => {
    const script = fileURLToPath(new URL('../runtime/aws-host.sh', import.meta.url));
    const { stdout } = await promisify(execFile)('bash', ['-c', `source ${shellQuote(script)}
chown() { printf '%s\\n' "$*"; }
prepare_configuration_permissions 123 456 '/opt/bundle/generated'`]);
    assert.deepEqual(stdout.trim().split('\n'), ['konifer.conf', 'konifer.source.conf', 'konifer.aws.conf'].map((name) => `123:456 /opt/bundle/generated/${name}`));
});

test('bootstrap and reset reject unreadable custom config before touching AWS', async () => {
    const script = fileURLToPath(new URL('../runtime/aws.ts', import.meta.url));
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-config-preflight-'));
    try {
        for (const command of ['bootstrap', 'reset', 'run']) {
            const args = command === 'run' ? ['run', 'smoke', '--config', path.join(temporary, 'missing.conf')] : [command, '--docker-tag', '0.9.0', '--config', path.join(temporary, 'missing.conf')];
            await assert.rejects(promisify(execFile)(process.execPath, [script, ...args], { env: { ...process.env, AWS_PROFILE: 'nonexistent-no-aws-calls' } }), /missing or unreadable/);
        }
    } finally { await rm(temporary, { recursive: true, force: true }); }
});
