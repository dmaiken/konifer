// Opt-in: uses the HOCON implementation in a real released Konifer image.
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { chmod, copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { generateAwsConfiguration, loadAwsConfiguration } from '../runtime/aws-config.ts';
import { selectConfiguration } from '../runtime/configuration.ts';

const connectionsFile = new URL('../runtime/aws-connections.example.json', import.meta.url).pathname;
const connections = JSON.parse(await readFile(connectionsFile, 'utf8'));
const { aws } = await loadAwsConfiguration(connections);
const image = process.env.KONIFER_CONFIG_TEST_IMAGE ?? 'ghcr.io/dmaiken/konifer:0.14.0';

test('Konifer HOCON parser preserves arbitrary config layout and overrides stale local settings', async () => {
    // Workspace paths are shared with Docker even when the runner has a private /tmp.
    const results = fileURLToPath(new URL('../results/', import.meta.url));
    await mkdir(results, { recursive: true });
    const temporary = await mkdtemp(path.join(results, '.hocon-integration-'));
    try {
        await chmod(temporary, 0o755);
        await copyFile(new URL('./fixtures/HoconProbe.java', import.meta.url), path.join(temporary, 'HoconProbe.java'));
        const parts = [
            'tuning { width = 31, before = true }',
            'object-store { provider = filesystem, s3 { endpoint-url = "http://garage:3900", region = garage, access-key = "old-key", secret-key = "old-secret", force-path-style = true } }',
            'data-store { provider = inmemory, postgresql { host = old-host, port = 1234, user = old-user, database = old-db, password = "old-password", ssl-mode = "verify-full", ssl-root-cert = "/missing/ca.pem", unrelated-tuning = 8 } }',
            'http.public-url = "http://localhost:9999"',
            'paths { "/**" { object-store.bucket = assets }, "/custom/**" { transform.preprocessing { enabled = true, width = 17 } } }',
            'rule-definitions { custom { prompts = ["hello"], threshold = 0.0 } }',
            'tuning.after = ${tuning.before}\ntuning.from-env = ${?CONFIG_PROBE_NUMBER}',
        ];
        const sources = [
            parts.join('\n'),
            `{\n${[...parts].reverse().join('\n')}\n}`,
            JSON.stringify({ tuning: { width: 31, before: true, after: true }, 'object-store': { s3: { 'endpoint-url': 'http://garage:3900', 'access-key': 'old-key', 'secret-key': 'old-secret' } }, 'data-store': { postgresql: { 'ssl-root-cert': '/missing/ca.pem', password: 'old-password', 'unrelated-tuning': 8 } } }),
            parts.join('\n').replace('"old-secret"', '${MISSING_GARAGE_SECRET}').replace('"old-password"', '${MISSING_GARAGE_PASSWORD}').replace('"/missing/ca.pem"', '${MISSING_GARAGE_CERT}'),
            '',
        ];
        for (const [index, source] of sources.entries()) {
            const file = path.join(temporary, `source ${index}.conf`);
            const generated = path.join(temporary, `generated-${index}`);
            await writeFile(file, source);
            await generateAwsConfiguration(connectionsFile, generated, true, await selectConfiguration(file, true));
            // The image's non-root user must traverse the test bind mount.
            await chmod(generated, 0o755);
            for (const name of ['konifer.conf', 'konifer.source.conf', 'konifer.aws.conf']) await chmod(path.join(generated, name), 0o644);
            const { stdout } = await promisify(execFile)('docker', ['run', '--rm', '--network=none', '--entrypoint', 'java',
                '--mount', `type=bind,src=${temporary},dst=/probe,readonly`, '-e', 'CONFIG_PROBE_NUMBER=17', image,
                '--class-path', '/app/konifer.jar', '--source', '21', '/probe/HoconProbe.java', `/probe/generated-${index}/konifer.conf`]);
            const config = JSON.parse(stdout);
            assert.equal(config['data-store'].provider, 'postgresql');
            assert.equal(config['data-store'].postgresql.host, connections.rdsEndpoint);
            assert.equal(config['data-store'].postgresql.port, 5432);
            assert.equal(config['data-store'].postgresql.user, aws.rds.user);
            assert.equal(config['data-store'].postgresql.database, aws.rds.database);
            assert.equal(config['data-store'].postgresql['ssl-mode'], 'disable');
            if (index !== 4) assert.equal(config['data-store'].postgresql['unrelated-tuning'], 8);
            assert.equal(config['object-store'].provider, 's3');
            assert.equal(config['object-store'].s3.region, connections.region);
            assert.equal(config['object-store'].s3['force-path-style'], false);
            assert.equal(config.http['public-url'], `http://${connections.koniferPrivateIp}:8080`);
            assert.equal(config.paths['/**']['object-store'].bucket, connections.bucketName);
            if (index !== 4) {
                assert.equal(config.tuning.width, 31);
                assert.equal(config.tuning.after, true);
            }
            if (index !== 2 && index !== 4) {
                assert.equal(config.paths['/custom/**'].transform.preprocessing.width, 17);
                assert.equal(config['rule-definitions'].custom.prompts[0], 'hello');
                assert.equal(config.tuning['from-env'], '17');
            }
            assert.equal(await readFile(file, 'utf8'), source);
        }
        const invalidFile = path.join(temporary, 'invalid-source.conf');
        await writeFile(invalidFile, 'broken = {');
        await generateAwsConfiguration(connectionsFile, path.join(temporary, 'invalid'), false, await selectConfiguration(invalidFile));
        await assert.rejects(promisify(execFile)('docker', ['run', '--rm', '--network=none', '--entrypoint', 'java',
            '--mount', `type=bind,src=${temporary},dst=/probe,readonly`, image, '--class-path', '/app/konifer.jar',
            '--source', '21', '/probe/HoconProbe.java', '/probe/invalid/konifer.conf']), /Parse/);
    } finally { await rm(temporary, { recursive: true, force: true }); }
});
