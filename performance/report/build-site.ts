import { copyFile, cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Stages a standalone dashboard without changing the local preview or histories. */
export async function stageReportSite(performanceDirectory: string, includeLoadTesting: boolean): Promise<string> {
    const siteDirectory = path.join(performanceDirectory, 'dist/site');
    const destination = path.join(siteDirectory, 'performance');
    await rm(siteDirectory, { recursive: true, force: true });
    await Promise.all(['report', 'dist', 'history', 'config'].map((directory) =>
        mkdir(path.join(destination, directory), { recursive: true })));

    const files = [
        'report/report.css',
        'history/releases.json',
        'config/workloads.json',
        'config/environments.json',
        'BENCHMARKING.md',
        ...(includeLoadTesting ? ['history/loads.json', 'config/load.json'] : []),
    ];
    await Promise.all([
        ...files.map((file) => copyFile(path.join(performanceDirectory, file), path.join(destination, file))),
        ...['report', 'fonts'].map((directory) => cp(
            path.join(performanceDirectory, 'dist', directory),
            path.join(destination, 'dist', directory),
            { recursive: true },
        )),
    ]);

    const html = await readFile(path.join(performanceDirectory, 'report/index.html'), 'utf8');
    await writeFile(path.join(destination, 'report/index.html'), includeLoadTesting ? html : html.replace(
        'Compare operation latency and mixed-load results across Konifer releases.',
        'Compare operation latency across Konifer releases.',
    ));
    await writeFile(
        path.join(destination, 'dist/report/build-options.js'),
        `export const includeLoadTesting = ${includeLoadTesting};\n`,
    );
    return siteDirectory;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const arguments_ = process.argv.slice(2);
    if (arguments_.length > 1 || arguments_.some((argument) => argument !== '--without-load')) {
        throw new Error('Usage: npm run build -- [--without-load]');
    }
    const directory = fileURLToPath(new URL('../', import.meta.url));
    const site = await stageReportSite(directory, !arguments_.includes('--without-load'));
    console.log(`Performance dashboard: ${site}/performance/report/`);
}
