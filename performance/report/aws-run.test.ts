import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { runRequest, validateRun } from '../runtime/aws-run.ts';
import { shellQuote } from '../runtime/aws.ts';

const directory = fileURLToPath(new URL('../', import.meta.url));
const config = JSON.parse(await readFile(path.join(directory, 'config/workloads.json'), 'utf8')) as Parameters<typeof runRequest>[1];

test('AWS release selection includes all cases, repetitions and a bounded full-suite timeout', () => {
    const request = runRequest(['release', '--environment', 'aws-c9gd-v1', '--docker-tag', '0.14.0'], config, 'run-1', 'v0.14.0');
    assert.equal(request.expected.length, 69);
    assert.ok(request.timeoutSeconds > 1800);
    assert.ok(request.timeoutSeconds <= 172800);
    assert.equal(request.subject, 'v0.14.0');
    assert.ok(request.args.includes('--subject'));
});

test('AWS filtering and flags preserve the requested benchmark protocol', () => {
    const request = runRequest(['release', '--workload', 'format.encode', '--case', 'jpg-to-webp', '--repetitions', '2', '--no-start', '--use-legacy-query-params'], config, 'run-2', 'subject');
    assert.deepEqual(request.expected.map((item) => [item.workload, item.case, item.repetition]), [['format.encode', 'jpg-to-webp', 1], ['format.encode', 'jpg-to-webp', 2]]);
    assert.equal(request.noStart, true);
    assert.equal(request.legacy, true);
    assert.equal(runRequest(['smoke'], config, 'run-3', 'subject').expected.length, 6);
});

test('AWS resolves the custom config locally and does not forward its path to the worker', () => {
    const request = runRequest(['smoke', '--config', 'external config.conf', '--use-legacy-query-params'], config, 'run-custom', 'subject');
    assert.equal(request.configFile, path.resolve('external config.conf'));
    assert.ok(request.args.includes('--use-legacy-query-params'));
    assert.ok(!request.args.includes('--config'));
    assert.ok(!request.args.includes('external config.conf'));
    assert.throws(() => runRequest(['smoke', '--config'], config, 'run-custom', 'subject'), /Unsupported/);
});

test('unsupported or invalid AWS requests fail before any remote operations', () => {
    for (const args of [ ['load'], ['release', '--with-load'], ['smoke', '--environment', 'local-compose-v4'], ['smoke', '--case', 'x'], ['release', '--repetitions', '0'], ['release', '--repetitions', '999999'], ['smoke', '--workload', 'missing'], ['smoke', '--workload', 'format.encode', '--case', 'missing'], ['smoke', '--docker-tag', '$(id)'] ]) {
        assert.throws(() => runRequest(args, config, 'run-4', 'subject'));
    }
});

test('result collection rejects missing, malformed, and mismatched artifacts but accepts benchmark failures', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-collect-test-'));
    const schema = path.join(directory, 'schema/result.schema.json');
    const request = runRequest(['smoke', '--workload', 'upload.original'], config, 'run-5', 'v0.14.0');
    const file = request.expected[0].file;
    try {
        await mkdir(path.join(temporary, 'raw'));
        await mkdir(path.join(temporary, 'normalized'));
        await assert.rejects(validateRun(temporary, request, schema), /Result set differs/);
        const result = {
            version: 1, runId: request.runId, suite: request.suite, workload: 'upload.original', case: 'jpg-medium', repetition: 1,
            environment: request.environment, subject: request.subject, startedAt: new Date().toISOString(), completedAt: new Date().toISOString(), passed: false,
            metrics: { operations: 0, requests: 0, errors: 1, checks: { passed: 0, failed: 1 }, droppedIterations: 0, durationMs: { min: 0, p50: 0, p90: 0, p95: 0, p99: 0, max: 0 } },
        };
        await writeFile(path.join(temporary, 'normalized', file), JSON.stringify(result));
        await writeFile(path.join(temporary, 'raw', file), '{}');
        await validateRun(temporary, request, schema);
        await writeFile(path.join(temporary, 'normalized/extra.json'), JSON.stringify(result));
        await assert.rejects(validateRun(temporary, request, schema), /Result set differs/);
        await rm(path.join(temporary, 'normalized/extra.json'));
        await writeFile(path.join(temporary, 'normalized', file), JSON.stringify({ ...result, subject: 'wrong' }));
        await assert.rejects(validateRun(temporary, request, schema), /identity mismatch/);
        await writeFile(path.join(temporary, 'normalized', file), JSON.stringify({ ...result, passed: 'yes' }));
        await assert.rejects(validateRun(temporary, request, schema), /Invalid result/);
    } finally { await rm(temporary, { recursive: true, force: true }); }
});

test('reset refuses EBS and nested mounts before clearing data', async () => {
    const script = path.join(directory, 'runtime/aws-reset.sh');
    const prefix = `source ${shellQuote(script)}\nrealpath() { echo /mnt/konifer-scratch; }\n`;
    await assert.rejects(promisify(execFile)('bash', ['-c', `${prefix}
findmnt() { if [[ $* == *FSTYPE* ]]; then echo ext4; else echo /dev/ebs; fi; }
lsblk() { echo 'Amazon Elastic Block Store'; }
validate_reset_scratch`]), /not EC2 instance storage/);
    await assert.rejects(promisify(execFile)('bash', ['-c', `${prefix}
findmnt() { if [[ $* == *FSTYPE* ]]; then echo ext4; elif [[ $* == *SOURCE* ]]; then echo /dev/nvme; else echo /mnt/konifer-scratch/nested; fi; }
lsblk() { echo 'Amazon EC2 NVMe Instance Storage'; }
validate_reset_scratch`]), /nested mounts/);
});

test('host flock refuses concurrent benchmark and reset operations', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-lock-test-'));
    try {
        const lock = shellQuote(path.join(temporary, 'runtime.lock'));
        const output = await promisify(execFile)('bash', ['-c', `exec 9>${lock}; flock -n 9; if flock -n ${lock} true; then exit 1; fi; echo protected`]);
        assert.match(output.stdout, /protected/);
    } finally { await rm(temporary, { recursive: true, force: true }); }
});

test('reset checks an API page rather than the CLI aggregate for an empty bucket', async () => {
    const script = path.join(directory, 'runtime/aws-reset.sh');
    const command = `source ${shellQuote(script)}
aws() { if [[ $* == *--no-paginate* ]]; then echo "$probe_count"; else echo None; fi; }
verify_empty_object_bucket benchmark-bucket`;
    await promisify(execFile)('bash', ['-c', `probe_count=0\n${command}`]);
    await assert.rejects(promisify(execFile)('bash', ['-c', `probe_count=1\n${command}`]), /not empty after reset/);
});
