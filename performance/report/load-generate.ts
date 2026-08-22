#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderLoadMarkdown, updateLoadHistory } from './load.ts';
import type { LoadHistory, LoadRun, WorkloadCatalog } from './types.ts';

const reportDirectory = path.dirname(fileURLToPath(import.meta.url));
const performanceDirectory = path.dirname(reportDirectory);
const runDirectory = path.resolve(process.argv[2] || '');

if (!process.argv[2]) throw new Error('Usage: ./performance/load-report.sh performance/results/<run-id>');

const resultPath = path.join(runDirectory, 'normalized/load.json');
const run = await readJsonFile<LoadRun>(resultPath);
const workloads = await readJsonFile<WorkloadCatalog>(path.join(performanceDirectory, 'config/workloads.json'));
await writeJson(path.join(runDirectory, 'aggregate.json'), run);
await writeFile(path.join(runDirectory, 'report.md'), renderLoadMarkdown(run, workloads));

const historyPath = path.join(performanceDirectory, 'history/loads.json');
const history = await readJson<LoadHistory>(historyPath, { version: 1, runs: [] });
await writeJson(historyPath, updateLoadHistory(history, run));

console.log(`Published mixed-load history${run.passed ? '' : ' with failed checks'}: ${historyPath}`);
console.log(`Load result: ${path.join(runDirectory, 'aggregate.json')}`);
console.log(`Load report: ${path.join(runDirectory, 'report.md')}`);

async function readJson<T>(filename: string, fallback: T): Promise<T> {
    try {
        return JSON.parse(await readFile(filename, 'utf8')) as T;
    } catch (error) {
        if (error instanceof Error && 'code' in error && error.code === 'ENOENT') return fallback;
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
