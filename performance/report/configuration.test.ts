import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { chmod, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { configurationHash, defaultConfiguration, selectConfiguration } from '../runtime/configuration.ts';

test('default config selection uses the existing file and translates only when requested', async () => {
    const selected = await selectConfiguration();
    assert.equal(selected.path, defaultConfiguration);
    assert.equal(selected.fingerprint.explicit, false);
    assert.equal(selected.source, selected.application);
    assert.equal(selected.fingerprint.sourceSha256, configurationHash(selected.source));
    const legacy = await selectConfiguration(undefined, true);
    assert.equal(legacy.fingerprint.legacyTranslated, true);
    assert.match(legacy.application, /\n    w = 200/);
    assert.equal(await readFile(defaultConfiguration, 'utf8'), selected.source);
});

test('config selection supports external paths and snapshots custom content without translation', async () => {
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-config-selection-'));
    const file = path.join(temporary, 'custom config.conf');
    const content = '# untouched\nwidth = 10\ncustom { property = true }\n';
    try {
        await writeFile(file, content);
        const selected = await selectConfiguration(file, true);
        assert.equal(selected.application, content);
        assert.equal(selected.fingerprint.legacyTranslated, false);
        assert.equal(selected.fingerprint.sourceName, 'custom config.conf');
        await writeFile(file, 'changed after selection');
        assert.equal(selected.application, content);
        const script = fileURLToPath(new URL('../runtime/configuration.ts', import.meta.url));
        const result = await promisify(execFile)(process.execPath, [script, '--config', path.basename(file), '--use-legacy-query-params'], { cwd: temporary });
        const metadata = JSON.parse(result.stdout) as { sourceSha256: string; explicit: boolean };
        assert.equal(metadata.sourceSha256, configurationHash('changed after selection'));
        assert.equal(metadata.explicit, true);
        assert.doesNotMatch(result.stdout, /changed after selection/);
        await assert.rejects(selectConfiguration(path.join(temporary, 'missing.conf')), /missing or unreadable/);
        await mkdir(path.join(temporary, 'directory'));
        await assert.rejects(selectConfiguration(path.join(temporary, 'directory')), /missing or unreadable/);
        await chmod(file, 0o000);
        await assert.rejects(selectConfiguration(file), /missing or unreadable/);
    } finally { await rm(temporary, { recursive: true, force: true }); }
});
