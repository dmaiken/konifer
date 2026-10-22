import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { shellQuote } from '../runtime/aws.ts';

const execute = promisify(execFile);
const runtime = fileURLToPath(new URL('../runtime/', import.meta.url));

/** Source production helpers in a child shell; callers replace host-mutating commands with stubs. */
async function shell(script: string, body: string): Promise<{ stdout: string; stderr: string }> {
    return execute('bash', ['-c', `source ${shellQuote(path.join(runtime, script))}\n${body}`]);
}

/** Create a minimal generated deployment, including an empty but valid selected HOCON source. */
async function deployment(directory: string): Promise<void> {
    await mkdir(path.join(directory, 'models/siglip2-base-patch16-224'), { recursive: true });
    for (const file of ['konifer.conf', 'konifer.aws.conf', 'compose.json', 'rds-ca-bundle.pem', 'models/siglip2-base-patch16-224/vision_model.onnx', 'models/siglip2-base-patch16-224/text_model.onnx', 'models/siglip2-base-patch16-224/tokenizer.json']) {
        await writeFile(path.join(directory, file), 'test');
    }
    await writeFile(path.join(directory, 'konifer.source.conf'), '');
    await writeFile(path.join(directory, 'deployment.json'), JSON.stringify({
        connections: { region: 'us-east-2', rdsEndpoint: 'benchmark.invalid' },
        aws: { scratch: { hostPath: '/mnt/konifer-scratch' }, rds: { user: 'konifer_user', database: 'konifer' } },
    }));
}

// Return a mounted instance-store device without inspecting this machine's disks.
const mountedScratch = `
mountpoint() { return 0; }
findmnt() { if [[ $* == *FSTYPE* ]]; then echo ext4; else echo /dev/test-instance-store; fi; }
lsblk() { echo 'Amazon EC2 NVMe Instance Storage'; }
`;

test('image identity validates both numeric IDs and propagates failed image probes', async () => {
    const { stdout } = await shell('aws-host.sh', `
docker() { if [[ \${*: -1} == -u ]]; then echo 123; else echo 456; fi; }
image_identity pinned-image`);
    assert.equal(stdout, '123:456\n');
    for (const ids of [['invalid', '456'], ['123', 'invalid']]) {
        await assert.rejects(shell('aws-host.sh', `
docker() { if [[ \${*: -1} == -u ]]; then echo ${ids[0]}; else echo ${ids[1]}; fi; }
image_identity pinned-image`), /must be numeric/);
    }
    await assert.rejects(shell('aws-host.sh', 'docker() { echo probe-failed >&2; return 23; }; image_identity pinned-image'), { code: 23 });
    await assert.rejects(shell('aws-host.sh', 'docker() { echo 123; return 24; }; identity=$(image_identity pinned-image)'), { code: 24 });
    await assert.rejects(shell('aws-host.sh', `
docker() { echo 123; if [[ \${*: -1} == -g ]]; then return 25; fi; }
identity=$(image_identity pinned-image)`), { code: 25 });
});

test('Konifer bootstrap reuses one image identity for scratch, configs, and models', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-host-shell-'));
    try {
        const generated = path.join(temporary, 'generated');
        await deployment(generated);
        await writeFile(path.join(temporary, 'manifest.json'), JSON.stringify({ image: { reference: 'pinned-image' }, infrastructure: { secretArn: 'test-secret' } }));
        const log = path.join(temporary, 'calls');
        const marker = path.join(temporary, 'initialized-image');
        // Relocate only the fixed host-state prefix. A regression cannot write /opt
        // on the developer's machine; production control flow and commands stay intact.
        const script = path.join(temporary, 'host.sh');
        await writeFile(script, (await readFile(path.join(runtime, 'aws-host.sh'), 'utf8')).replaceAll('/opt/konifer-performance', temporary));
        const stubs = `source ${shellQuote(script)}
${mountedScratch}
docker() {
  echo "docker $*" >> ${shellQuote(log)}
  if [[ $1 == run ]]; then if [[ \${*: -1} == -u ]]; then echo 123; else echo 456; fi; fi
}
chown() { echo "chown $*" >> ${shellQuote(log)}; }
curl() { :; }
aws() { echo '{"password":"test-only-password"}'; }
bash() {
  [[ $PG_PASSWORD == test-only-password && $(< ${shellQuote(marker)}) == pinned-image ]]
  echo start >> ${shellQuote(log)}
}
`;
        const { stdout, stderr } = await execute('bash', ['-c', `${stubs}
prepare_konifer ${shellQuote(temporary)}
[[ -z \${PG_PASSWORD+x} ]]`]);
        const calls = (await readFile(log, 'utf8')).trim().split('\n');
        assert.equal(calls.filter((line) => line.startsWith('docker run ')).length, 2);
        assert.ok(calls.includes('chown 123:456 /mnt/konifer-scratch'));
        for (const name of ['konifer.conf', 'konifer.source.conf', 'konifer.aws.conf']) {
            assert.ok(calls.includes(`chown 123:456 ${generated}/${name}`));
        }
        assert.ok(calls.includes(`chown -R 123:456 ${generated}/models`));
        assert.equal(calls.at(-1), 'start');
        assert.doesNotMatch(stdout + stderr, /test-only-password/);
        await writeFile(marker, 'other-image\n');
        await assert.rejects(execute('bash', ['-c', `${stubs}\nprepare_konifer ${shellQuote(temporary)}`]), /Image digest changed/);
        assert.deepEqual((await readFile(log, 'utf8')).trim().split('\n'), calls);
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
});

test('scratch preparation refuses partitioned or ambiguous devices before formatting', async () => {
    await assert.rejects(shell('aws-host.sh', `
mountpoint() { return 1; }
lsblk() {
  if [[ $* == *--json* ]]; then echo '{"blockdevices":[{"path":"/dev/test","model":"Instance Storage"}]}';
  else printf '/dev/test\\n/dev/test1\\n'; fi
}
mkfs.ext4() { echo 'format-called' >&2; return 99; }
prepare_scratch 123 456`), /has partitions/);
    await assert.rejects(shell('aws-host.sh', `
mountpoint() { return 1; }
lsblk() { echo '{"blockdevices":[{"path":"/dev/a","model":"Instance Storage"},{"path":"/dev/b","model":"Instance Storage"}]}'; }
mkfs.ext4() { echo 'format-called' >&2; return 99; }
prepare_scratch 123 456`), /Expected exactly one/);
});

test('startup validates deployment files and scratch before SQL or container changes', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-start-shell-'));
    try {
        await deployment(temporary);
        const prefix = `${mountedScratch}
export KONIFER_IMAGE=pinned-image PG_PASSWORD=test-only-password
psql() { echo sql-must-not-run >&2; return 99; }
docker() { echo docker-must-not-run >&2; return 99; }
`;
        await shell('aws-start.sh', `${prefix}\nvalidate_deployment ${shellQuote(temporary)}`);
        await rm(path.join(temporary, 'konifer.source.conf'));
        await assert.rejects(shell('aws-start.sh', `${prefix}\nmain ${shellQuote(temporary)}`), /Missing or unreadable selected configuration/);
        await deployment(temporary);
        await writeFile(path.join(temporary, 'konifer.aws.conf'), '');
        await assert.rejects(shell('aws-start.sh', `${prefix}\nmain ${shellQuote(temporary)}`), /Missing deployment file: konifer.aws.conf/);
        await deployment(temporary);
        await assert.rejects(shell('aws-start.sh', `${prefix}
lsblk() { echo 'Amazon Elastic Block Store'; }
main ${shellQuote(temporary)}`), /Scratch must be an ext4 mount/);
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
});

test('startup orders verified SQL before Compose and propagates SQL, startup, and probe failures', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-start-order-'));
    try {
        await deployment(temporary);
        const log = path.join(temporary, 'calls');
        for (const stage of ['success', 'sql', 'start', 'probe'] as const) {
            await writeFile(log, '');
            const body = `${mountedScratch}
export KONIFER_IMAGE=pinned-image PG_PASSWORD=test-only-password
psql() {
  [[ $PGPASSWORD == test-only-password && $PGSSLMODE == verify-full && $PGSSLROOTCERT == ${shellQuote(path.join(temporary, 'rds-ca-bundle.pem'))} ]]
  echo sql >> ${shellQuote(log)}
  ${stage === 'sql' ? 'return 21' : ':'}
}
docker() {
  if [[ $* == *' up '* ]]; then
    echo start >> ${shellQuote(log)}
    ${stage === 'start' ? 'return 22' : ':'}
  else
    echo probe >> ${shellQuote(log)}
    ${stage === 'probe' ? 'return 23' : ':'}
  fi
}
main ${shellQuote(temporary)}`;
            if (stage === 'success') {
                const { stdout, stderr } = await shell('aws-start.sh', body);
                assert.match(stdout, /Container started/);
                assert.doesNotMatch(stdout + stderr, /test-only-password/);
            } else {
                await assert.rejects(shell('aws-start.sh', body), { code: { sql: 21, start: 22, probe: 23 }[stage] });
            }
            assert.deepEqual((await readFile(log, 'utf8')).trim().split('\n'), stage === 'sql' ? ['sql'] : stage === 'start' ? ['sql', 'start'] : ['sql', 'start', 'probe']);
        }
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
});
