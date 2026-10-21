import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFile, mkdir, mkdtemp, readFile, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const performanceDirectory = fileURLToPath(new URL('../', import.meta.url));

test('shared runner preserves native local smoke, filtered release, and load execution', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-runner-test-'));
    const harness = path.join(temporary, 'performance');
    try {
        for (const directory of ['assets', 'config', 'schema', 'runtime', 'lib', 'bin']) await mkdir(path.join(harness, directory), { recursive: true });
        await mkdir(path.join(temporary, 'models/siglip2-base-patch16-224'), { recursive: true });
        for (const file of ['vision_model.onnx', 'text_model.onnx', 'tokenizer.json']) await writeFile(path.join(temporary, 'models/siglip2-base-patch16-224', file), 'test');
        for (const file of ['run.sh', 'runtime/configuration.ts', 'runtime/legacy-config.ts', 'runtime/konifer.conf', 'lib/query-parameters.ts', 'runtime/validate-result.ts', 'schema/result.schema.json', 'schema/load-result.schema.json']) await copyFile(path.join(performanceDirectory, file), path.join(harness, file));
        await symlink(path.join(performanceDirectory, 'node_modules'), path.join(harness, 'node_modules'));
        const fixture = 'test fixture';
        await writeFile(path.join(harness, 'assets/test.jpg'), fixture);
        await writeFile(path.join(harness, 'assets/manifest.json'), JSON.stringify({ fixtures: { test: { files: { jpg: { path: 'test.jpg', bytes: fixture.length, sha256: createHash('sha256').update(fixture).digest('hex') } } } } }));
        await writeFile(path.join(harness, 'config/workloads.json'), JSON.stringify({ methodology: 'single-vu-v1', baseUrl: 'http://example.invalid:8080', suites: { smoke: { repetitions: 1, workloads: ['upload.original'] }, release: { repetitions: 3, workloads: ['upload.original'] } }, workloads: { 'upload.original': { case: 'jpg-medium' } } }));
        await writeFile(path.join(harness, 'config/load.json'), JSON.stringify({ defaultProfile: 'mixed-v1' }));
        const profile = { benchmarkMethodology: 'single-vu-v1', runtime: 'compose', composeFile: 'unused.yml', services: [{ container: 'fake-konifer', cpus: 2, memoryBytes: 100 }] };
        await writeFile(path.join(harness, 'config/environments.json'), JSON.stringify({ default: 'benchmark', loadDefault: 'load', profiles: { benchmark: profile, load: profile, aws: { ...profile, runtime: 'aws' } } }));
        await writeFile(path.join(harness, 'report.sh'), '#!/usr/bin/env bash\nprintf benchmark > "$1/reported"\n', { mode: 0o755 });
        await writeFile(path.join(harness, 'load-report.sh'), '#!/usr/bin/env bash\nprintf load > "$1/reported"\n', { mode: 0o755 });
        await writeFile(path.join(harness, 'bin/curl'), '#!/usr/bin/env bash\nif [[ $* == *DELETE* ]]; then printf 204; else printf 200; fi\n', { mode: 0o755 });
        await writeFile(path.join(harness, 'bin/docker'), '#!/usr/bin/env bash\nif [[ $* == *Memory* ]]; then echo 100; else echo 2000000000; fi\n', { mode: 0o755 });
        await writeFile(path.join(harness, 'bin/sleep'), '#!/usr/bin/env bash\nexit 0\n', { mode: 0o755 });
        await writeFile(path.join(harness, 'bin/k6'), `#!${process.execPath}
const fs = require('fs');
const env = {};
for (let i = 0; i < process.argv.length; i++) {
  if (process.argv[i] === '-e') {
    const value = process.argv[++i]; const equals = value.indexOf('='); env[value.slice(0, equals)] = value.slice(equals + 1);
  }
}
const checks = {passed: 1, failed: 0};
const result = {version: 1, runId: env.RUN_ID, environment: env.ENVIRONMENT, subject: env.SUBJECT, startedAt: env.STARTED_AT, completedAt: new Date().toISOString(), passed: true};
if (env.LOAD_PROFILE) Object.assign(result, {profile: env.LOAD_PROFILE, trafficDurationSeconds: 1, peakRatePerMinute: 1, targetOperations: 1, operations: 1, requests: 1, errors: 0, checks, droppedIterations: 0, streams: [{id: 'test', workload: 'upload.original', case: 'jpg-medium', targetRatePerMinute: 1, plannedOperations: 1, operations: 1, requests: 1, errors: 0, checks, droppedIterations: 0, durationMs: {p50: 1, p90: 1, p95: 1, p99: 1}, passed: true}], recovery: {healthPassed: true, eagerReady: true, durationMs: 1, checks, passed: true}});
else Object.assign(result, {suite: env.SUITE, workload: env.WORKLOAD, case: env.CASE, repetition: Number(env.REPETITION), metrics: {operations: 1, requests: 1, errors: 0, checks, droppedIterations: 0, durationMs: {min: 1, p50: 1, p90: 1, p95: 1, p99: 1, max: 1}}});
fs.writeFileSync(env.RESULT_PATH, JSON.stringify(result)); fs.writeFileSync(env.RAW_RESULT_PATH, JSON.stringify(env));
if (process.env.K6_WEB_DASHBOARD === 'true') fs.writeFileSync(process.env.K6_WEB_DASHBOARD_EXPORT, '<html>test</html>');
process.exitCode = Number(process.env.KONIFER_TEST_K6_STATUS || 0);
`, { mode: 0o755 });
        for (const suite of ['smoke', 'release', 'load']) {
            const args = [path.join(harness, 'run.sh'), suite, '--no-start', '--subject', 'v0.14.0'];
            if (suite === 'release') args.push('--workload', 'upload.original', '--case', 'jpg-medium', '--repetitions', '2');
            await promisify(execFile)('bash', args, { env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: `local-${suite}` } });
            const results = path.join(harness, 'results', `local-${suite}`);
            assert.equal(await readFile(path.join(results, 'reported'), 'utf8'), suite === 'load' ? 'load' : 'benchmark');
            const normalized = JSON.parse(await readFile(path.join(results, 'normalized', suite === 'load' ? 'load.json' : 'upload-original--jpg-medium--1.json'), 'utf8')) as { environment: string };
            assert.equal(normalized.environment, suite === 'load' ? 'load' : 'benchmark');
            if (suite === 'release') await readFile(path.join(results, 'html/upload-original--jpg-medium--2.html'));
            const configuration = JSON.parse(await readFile(path.join(results, 'configuration.json'), 'utf8')) as { explicit: boolean };
            assert.equal(configuration.explicit, false);
        }
        const externalDirectory = path.join(temporary, 'external configs');
        await mkdir(externalDirectory);
        const customFile = path.join(externalDirectory, 'old konifer.conf');
        const custom = '# no environment changes\nwidth = 123\nobject-store.s3.endpoint-url = "http://custom-garage:3900"\n';
        await writeFile(customFile, custom);
        const dockerLog = path.join(temporary, 'mounted-configs');
        await writeFile(path.join(harness, 'bin/docker'), '#!/usr/bin/env bash\nif [[ $1 == compose ]]; then printf "%s\\n" "$KONIFER_CONFIG" >> "$KONIFER_TEST_DOCKER_LOG"; fi\nif [[ $* == *Memory* ]]; then echo 100; else echo 2000000000; fi\n', { mode: 0o755 });
        await promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'release', '--config', 'external configs/old konifer.conf', '--use-legacy-query-params', '--subject', 'v0.14.0', '--repetitions', '1', '--with-load'], {
            cwd: temporary,
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: 'custom-config', KONIFER_TEST_DOCKER_LOG: dockerLog },
        });
        assert.equal(await readFile(customFile, 'utf8'), custom);
        assert.deepEqual((await readFile(dockerLog, 'utf8')).trim().split('\n'), [customFile, customFile]);
        const customResults = path.join(harness, 'results/custom-config');
        const customMetadata = JSON.parse(await readFile(path.join(customResults, 'configuration.json'), 'utf8')) as { explicit: boolean; legacyTranslated: boolean };
        assert.equal(customMetadata.explicit, true);
        assert.equal(customMetadata.legacyTranslated, false);
        const loadArgs = JSON.parse(await readFile(path.join(customResults, 'raw/load.json'), 'utf8')) as { USE_LEGACY_QUERY_PARAMS: string };
        assert.equal(loadArgs.USE_LEGACY_QUERY_PARAMS, 'true');
        await promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--use-legacy-query-params'], {
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: 'default-legacy', KONIFER_TEST_DOCKER_LOG: dockerLog },
        });
        assert.match(await readFile(path.join(harness, 'runtime/konifer.legacy.conf'), 'utf8'), /\n    w = 200/);
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--config', path.join(temporary, 'missing.conf')], {
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}` },
        }), /missing or unreadable/);
        const forwarded = path.join(temporary, 'aws-arguments');
        await writeFile(path.join(harness, 'runtime/aws.sh'), '#!/usr/bin/env bash\nprintf "%s\\0" "$@" > "$KONIFER_TEST_FORWARD"\n');
        await promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--environment', 'aws', '--config', 'external configs/old konifer.conf'], {
            cwd: temporary, env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_TEST_FORWARD: forwarded },
        });
        const forwardedArgs = (await readFile(forwarded, 'utf8')).split('\0');
        assert.equal(forwardedArgs[forwardedArgs.indexOf('--config') + 1], customFile);
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'load', '--environment', 'aws']), /AWS supports smoke and release only/);
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'release', '--environment', 'aws', '--with-load']), /AWS supports smoke and release only/);
        await writeFile(path.join(harness, 'bin/docker'), '#!/usr/bin/env bash\nif [[ $1 == start ]]; then touch "$KONIFER_TEST_START_MARKER"; fi\nif [[ $* == *Memory* ]]; then echo 100; else echo 2000000000; fi\n', { mode: 0o755 });
        const marker = path.join(temporary, 'measurement-started');
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--environment', 'aws'], {
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: 'bad-limits', KONIFER_AWS_WORKER: 'true', KONIFER_K6_IMAGE: 'fake-image', KONIFER_TEST_START_MARKER: marker },
        }), /limits differ/);
        await assert.rejects(readFile(marker), { code: 'ENOENT' });
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--no-start'], {
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: 'failed-k6', KONIFER_TEST_K6_STATUS: '7' },
        }), /k6 exited with status 7 despite producing a passing result/);
        await writeFile(path.join(harness, 'bin/curl'), '#!/usr/bin/env bash\nif [[ $* == *DELETE* ]]; then printf 500; else printf 200; fi\n', { mode: 0o755 });
        await assert.rejects(promisify(execFile)('bash', [path.join(harness, 'run.sh'), 'smoke', '--no-start'], {
            env: { ...process.env, PATH: `${harness}/bin:${process.env.PATH}`, KONIFER_RUN_ID: 'failed-cleanup' },
        }), /Benchmark asset cleanup did not complete successfully/);
    } finally { await rm(temporary, { recursive: true, force: true }); }
});
