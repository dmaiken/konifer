import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { stageReportSite } from './build-site.ts';

test('standalone builds omit load data on request without changing local previews or histories', async (context) => {
    const directory = await mkdtemp(path.join(tmpdir(), 'konifer-report-site-'));
    context.after(() => rm(directory, { recursive: true, force: true }));
    await Promise.all(['report', 'history', 'config', 'dist/report', 'dist/fonts'].map((value) =>
        mkdir(path.join(directory, value), { recursive: true })));
    const sourceFiles = {
        'report/index.html': '<meta content="Compare operation latency and mixed-load results across Konifer releases." name="description">',
        'report/report.css': 'body {}',
        'history/releases.json': '{"version":1,"releases":[]}',
        'history/loads.json': '{"version":1,"runs":[]}',
        'config/workloads.json': '{}',
        'config/load.json': '{}',
        'config/environments.json': '{}',
        'BENCHMARKING.md': '# Methodology',
        'dist/report/report.js': 'import "./build-options.js";',
        'dist/report/build-options.js': 'export const includeLoadTesting = true;\n',
        'dist/fonts/example.woff2': 'font',
    };
    await Promise.all(Object.entries(sourceFiles).map(([file, content]) =>
        writeFile(path.join(directory, file), content)));

    const siteDirectory = await stageReportSite(directory, true);
    const site = path.join(siteDirectory, 'performance');
    assert.equal(await readFile(path.join(site, 'history/loads.json'), 'utf8'), sourceFiles['history/loads.json']);
    assert.equal(await readFile(path.join(site, 'config/load.json'), 'utf8'), sourceFiles['config/load.json']);

    await stageReportSite(directory, false);
    for (const file of ['history/loads.json', 'config/load.json']) {
        await assert.rejects(readFile(path.join(site, file)), { code: 'ENOENT' });
    }
    assert.equal(await readFile(path.join(site, 'dist/report/build-options.js'), 'utf8'),
        'export const includeLoadTesting = false;\n');
    assert.doesNotMatch(await readFile(path.join(site, 'report/index.html'), 'utf8'), /mixed-load/);
    for (const file of ['history/releases.json', 'config/workloads.json', 'config/environments.json',
        'dist/report/report.js', 'dist/fonts/example.woff2', 'report/report.css', 'BENCHMARKING.md']) {
        assert.equal(await readFile(path.join(site, file), 'utf8'), sourceFiles[file as keyof typeof sourceFiles]);
    }
    for (const [file, content] of Object.entries(sourceFiles)) {
        assert.equal(await readFile(path.join(directory, file), 'utf8'), content);
    }

    await stageReportSite(directory, true);
    assert.equal(await readFile(path.join(site, 'dist/report/build-options.js'), 'utf8'),
        'export const includeLoadTesting = true;\n');
    assert.equal(await readFile(path.join(site, 'history/loads.json'), 'utf8'), sourceFiles['history/loads.json']);
    assert.equal(await readFile(path.join(site, 'report/index.html'), 'utf8'), sourceFiles['report/index.html']);
});
