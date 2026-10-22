import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

interface Workloads {
    methodology: string;
    profiles: Record<string, { warmup: { maxDuration: string }; measurement: { maxDuration: string } }>;
    suites: Record<string, { repetitions: number; workloads: string[] }>;
    workloads: Record<string, { profile: string; case?: string; cases?: { id: string }[]; path?: string }>;
}
export interface RunRequest {
    runId: string;
    suite: 'smoke' | 'release';
    environment: string;
    tag: string;
    subject: string;
    legacy: boolean;
    noStart: boolean;
    configFile?: string;
    args: string[];
    expected: { file: string; workload: string; case: string; repetition: number }[];
    timeoutSeconds: number;
}

/** Convert the catalog's duration notation to seconds for the remote execution budget. */
function seconds(duration: string): number {
    const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
    if (!match) throw new Error(`Invalid duration: ${duration}`);
    return Number(match[1]) * ({ ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]]!);
}

/**
 * Validate CLI selection and enumerate expected artifacts before any remote work.
 * Budget the whole suite within SSM's limit and omit operator-local config paths
 * from worker arguments. Does not execute benchmarks or access AWS.
 */
export function runRequest(args: string[], config: Workloads, runId: string, defaultSubject: string): RunRequest {
    const options = new Map<string, string>();
    const flags = new Set<string>();
    const suite = args[0] === 'release' ? 'release' : args[0] === 'smoke' || args[0]?.startsWith('--') ? 'smoke' : undefined;
    if (!suite) throw new Error('AWS supports smoke and release only');
    const start = args[0]?.startsWith('--') ? 0 : 1;
    for (let i = start; i < args.length; i++) {
        const key = args[i];
        if (['--no-start', '--keep-assets', '--use-legacy-query-params'].includes(key)) flags.add(key);
        else if (['--environment', '--workload', '--case', '--repetitions', '--subject', '--docker-tag', '--config'].includes(key) && args[i + 1] && !args[i + 1].startsWith('--')) options.set(key, args[++i]);
        else throw new Error(`Unsupported AWS run option: ${key}`);
    }
    const environment = options.get('--environment') ?? 'aws-c9gd-v1';
    if (environment !== 'aws-c9gd-v1') throw new Error('Expected environment aws-c9gd-v1');
    const tag = options.get('--docker-tag') ?? 'latest';
    if (!/^[\w][\w.-]{0,127}$/.test(tag)) throw new Error('Invalid Docker tag');
    const workload = options.get('--workload');
    const selectedCase = options.get('--case');
    if (selectedCase && !workload) throw new Error('--case requires --workload');
    const repetitionString = options.get('--repetitions') ?? String(config.suites[suite].repetitions);
    if (!/^[1-9][0-9]*$/.test(repetitionString) || !Number.isSafeInteger(Number(repetitionString))) throw new Error('Invalid repetitions');
    const repetitions = Number(repetitionString);
    const expected: RunRequest['expected'] = [];
    let timeoutSeconds = 600;
    for (const id of workload ? [workload] : config.suites[suite].workloads) {
        const definition = config.workloads[id];
        if (!definition) throw new Error(`Unknown workload: ${id}`);
        const cases = definition.cases?.map((item) => item.id) ?? [definition.case!];
        if (selectedCase && !cases.includes(selectedCase)) throw new Error(`Unknown case: ${selectedCase}`);
        const profile = config.profiles[definition.profile];
        const perRepetition = suite === 'smoke' ? 300 + 120 + 240 : 1800 + seconds(profile.warmup.maxDuration) + seconds(profile.measurement.maxDuration) + 240;
        const count = (selectedCase ? 1 : cases.length) * repetitions;
        timeoutSeconds += count * perRepetition;
        if (timeoutSeconds > 172800) throw new Error('Selected run exceeds SSM execution budget; split it by workload');
        for (const caseId of selectedCase ? [selectedCase] : cases) {
            for (let repetition = 1; repetition <= repetitions; repetition++) {
                expected.push({ file: `${id.replaceAll('.', '-')}--${caseId}--${repetition}.json`, workload: id, case: caseId, repetition });
            }
        }
    }
    const subject = options.get('--subject') ?? defaultSubject;
    // The worker runs k6 only; an operator-local configuration path is not portable.
    const forwarded = args.filter((_, index) => args[index] !== '--config' && args[index - 1] !== '--config');
    if (!options.has('--environment')) forwarded.push('--environment', environment);
    if (!options.has('--subject')) forwarded.push('--subject', subject);
    return { runId, suite, environment, tag, subject, legacy: flags.has('--use-legacy-query-params'), noStart: flags.has('--no-start'),
        ...(options.has('--config') ? { configFile: path.resolve(options.get('--config')!) } : {}), args: forwarded, expected, timeoutSeconds };
}

/** Reject missing/extra repetitions, schema or identity mismatches, and absent raw/HTML artifacts before publication. */
export async function validateRun(directory: string, request: RunRequest, schemaFile: string): Promise<void> {
    const ajv = new Ajv2020({ allErrors: true });
    addFormats(ajv);
    const validate = ajv.compile(JSON.parse(await readFile(schemaFile, 'utf8')));
    const files = (await readdir(`${directory}/normalized`)).sort();
    if (JSON.stringify(files) !== JSON.stringify(request.expected.map((item) => item.file).sort())) throw new Error('Result set differs from selected cases and repetitions');
    for (const item of request.expected) {
        const result = JSON.parse(await readFile(`${directory}/normalized/${item.file}`, 'utf8')) as Record<string, unknown>;
        if (!validate(result)) throw new Error(`Invalid result ${item.file}: ${ajv.errorsText(validate.errors)}`);
        for (const [key, value] of Object.entries({ runId: request.runId, suite: request.suite, environment: request.environment, subject: request.subject, workload: item.workload, case: item.case, repetition: item.repetition })) {
            if (result[key] !== value) throw new Error(`Result identity mismatch: ${item.file}/${key}`);
        }
        JSON.parse(await readFile(`${directory}/raw/${item.file}`, 'utf8'));
        if (request.suite === 'release') await readFile(`${directory}/html/${item.file.replace(/\.json$/, '.html')}`);
    }
}
