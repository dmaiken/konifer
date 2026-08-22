#!/usr/bin/env node

import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { aggregateResults, updateHistory } from './aggregate.ts';
import { missingReleaseCases } from './publication.ts';
import { renderRunMarkdown } from './render.ts';
import type { EnvironmentCatalog, NormalizedResult, PerformanceHistory, WorkloadCatalog } from './types.ts';

const reportDirectory = path.dirname(fileURLToPath(import.meta.url));
const performanceDirectory = path.dirname(reportDirectory);
const runDirectory = path.resolve(process.argv[2] || '');

if (!process.argv[2]) {
    throw new Error('Usage: ./performance/report.sh performance/results/<run-id>');
}

const normalizedDirectory = path.join(runDirectory, 'normalized');
const filenames = (await readdir(normalizedDirectory)).filter((value) => value.endsWith('.json')).sort();
const documents = await Promise.all(filenames.map(async (filename): Promise<NormalizedResult> => (
    JSON.parse(await readFile(path.join(normalizedDirectory, filename), 'utf8')) as NormalizedResult
)));
const aggregate = aggregateResults(documents);
const [environments, catalog] = await Promise.all([
    readJsonFile<EnvironmentCatalog>(path.join(performanceDirectory, 'config/environments.json')),
    readJsonFile<WorkloadCatalog>(path.join(performanceDirectory, 'config/workloads.json')),
]);
const environment = environments.profiles[aggregate.environment];

await writeJson(path.join(runDirectory, 'aggregate.json'), aggregate);
await writeFile(path.join(runDirectory, 'report.md'), renderRunMarkdown(aggregate, environment, catalog));

if (aggregate.suite === 'release') {
    const requiredRepetitions = catalog.suites.release.repetitions;
    const incomplete = aggregate.results.filter((value) => value.repetitions < requiredRepetitions);
    const missing = missingReleaseCases(aggregate, catalog);
    if (incomplete.length > 0) {
        console.log(`Release history not updated: ${incomplete.length} case(s) have fewer than ${requiredRepetitions} repetitions.`);
    } else if (missing.length > 0) {
        console.log(`Release history not updated: ${missing.length} configured case(s) were not run.`);
    } else {
        const historyPath = path.join(performanceDirectory, 'history/releases.json');
        const history = await readJson<PerformanceHistory>(historyPath, { version: 1, releases: [] });
        const updated = updateHistory(history, aggregate);
        await writeJson(historyPath, updated);
        const outcome = aggregate.passed ? 'passing' : 'with failed benchmark results';
        console.log(`Published release history ${outcome}: ${historyPath}`);
    }
}

console.log(`Aggregate result: ${path.join(runDirectory, 'aggregate.json')}`);
console.log(`Run report: ${path.join(runDirectory, 'report.md')}`);

async function readJson<T>(filename: string, fallback: T): Promise<T> {
    try {
        return JSON.parse(await readFile(filename, 'utf8')) as T;
    } catch (error) {
        if (isNodeError(error) && error.code === 'ENOENT') return fallback;
        throw error;
    }
}

async function readJsonFile<T>(filename: string): Promise<T> {
    return JSON.parse(await readFile(filename, 'utf8')) as T;
}

async function writeJson(filename: string, value: unknown): Promise<void> {
    await mkdir(path.dirname(filename), { recursive: true });
    await writeFile(filename, `${JSON.stringify(value, null, 2)}\n`);
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
    return error instanceof Error;
}
