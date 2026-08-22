#!/usr/bin/env node

import { readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
    assertHistoryMatchesSchema,
    nonReleaseLoadRuns,
    nonReleaseEntries,
    withoutNonReleaseLoadRuns,
    withoutNonReleaseEntries,
} from './history-lint.ts';
import type { LoadHistory, PerformanceHistory } from './types.ts';

const reportDirectory = path.dirname(fileURLToPath(import.meta.url));
const performanceDirectory = path.dirname(reportDirectory);
const historyPath = path.join(performanceDirectory, 'history/releases.json');
const schemaPath = path.join(performanceDirectory, 'schema/history.schema.json');
const loadHistoryPath = path.join(performanceDirectory, 'history/loads.json');
const loadSchemaPath = path.join(performanceDirectory, 'schema/load-history.schema.json');
const loadResultSchemaPath = path.join(performanceDirectory, 'schema/load-result.schema.json');
const arguments_ = process.argv.slice(2);

if (arguments_.includes('--help') || arguments_.includes('-h')) {
    console.log('Usage: ./performance/lint-history.sh [--fix]');
    process.exit(0);
}
if (arguments_.some((argument) => argument !== '--fix') || arguments_.filter((argument) => argument === '--fix').length > 1) {
    console.error('Usage: ./performance/lint-history.sh [--fix]');
    process.exit(2);
}

const fix = arguments_[0] === '--fix';
let history: PerformanceHistory;
let loadHistory: LoadHistory;
try {
    const [historyDocument, schemaDocument, loadHistoryDocument, loadSchemaDocument, loadResultSchemaDocument] = await Promise.all([
        readFile(historyPath, 'utf8'),
        readFile(schemaPath, 'utf8'),
        readFile(loadHistoryPath, 'utf8'),
        readFile(loadSchemaPath, 'utf8'),
        readFile(loadResultSchemaPath, 'utf8'),
    ]);
    const parsedHistory: unknown = JSON.parse(historyDocument);
    const schema = JSON.parse(schemaDocument) as Record<string, unknown>;
    assertHistoryMatchesSchema(parsedHistory, schema);
    history = parsedHistory;
    const parsedLoadHistory: unknown = JSON.parse(loadHistoryDocument);
    const loadSchema = JSON.parse(loadSchemaDocument) as Record<string, unknown>;
    const loadResultSchema = JSON.parse(loadResultSchemaDocument) as Record<string, unknown>;
    assertHistoryMatchesSchema<LoadHistory>(parsedLoadHistory, loadSchema, [loadResultSchema]);
    loadHistory = parsedLoadHistory;
} catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
}
const invalid = nonReleaseEntries(history);
const invalidLoads = nonReleaseLoadRuns(loadHistory);

if (invalid.length === 0 && invalidLoads.length === 0) {
    console.log(`Release and load history schemas and labels are clean: ${historyPath}, ${loadHistoryPath}`);
    process.exit(0);
}

for (const release of invalid) {
    console.error(`Non-release history entry: ${release.subject} (${release.runId})`);
}
for (const run of invalidLoads) {
    console.error(`Non-release load history entry: ${run.subject} (${run.runId})`);
}

if (!fix) {
    const total = invalid.length + invalidLoads.length;
    console.error(`Performance history contains ${total} non-release entr${total === 1 ? 'y' : 'ies'}. Run with --fix to remove ${total === 1 ? 'it' : 'them'}.`);
    process.exit(1);
}

const temporaryPath = `${historyPath}.${process.pid}.tmp`;
const loadTemporaryPath = `${loadHistoryPath}.${process.pid}.tmp`;
await writeFile(temporaryPath, `${JSON.stringify(withoutNonReleaseEntries(history), null, 2)}\n`);
await writeFile(loadTemporaryPath, `${JSON.stringify(withoutNonReleaseLoadRuns(loadHistory), null, 2)}\n`);
await rename(temporaryPath, historyPath);
await rename(loadTemporaryPath, loadHistoryPath);
const removed = invalid.length + invalidLoads.length;
console.log(`Removed ${removed} non-release history entr${removed === 1 ? 'y' : 'ies'} from performance history.`);
console.log('Review the change with Git; tracked history can be recovered from version control.');
