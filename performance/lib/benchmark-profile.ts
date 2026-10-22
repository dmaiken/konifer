import type { BenchmarkPhase } from '../report/types.ts';
export type { BenchmarkPhase, BenchmarkProfile, BenchmarkProtocol } from '../report/types.ts';

export interface PhaseResult {
    operations: number;
    durationMs: number;
    completed: boolean;
}

interface PhaseActions {
    now: () => number;
    sleep: (milliseconds: number) => void;
    run: (index: number) => boolean;
}

/** Parse a positive catalog duration into milliseconds; reject unsupported notation. */
export function durationMilliseconds(value: string): number {
    const match = /^([1-9]\d*)(ms|s|m|h)$/.exec(value);
    if (!match) throw new Error(`Invalid benchmark duration: ${value}`);
    return Number(match[1]) * { ms: 1, s: 1000, m: 60000, h: 3600000 }[match[2] as 'ms' | 's' | 'm' | 'h'];
}

/** Reject invalid sample counts or a maximum duration that does not exceed the minimum. */
export function validatePhase(phase: BenchmarkPhase): void {
    if (!Number.isInteger(phase.samples) || phase.samples < 1) {
        throw new Error('Benchmark samples must be a positive integer');
    }
    if (durationMilliseconds(phase.maxDuration) <= durationMilliseconds(phase.minDuration)) {
        throw new Error('Benchmark maxDuration must exceed minDuration');
    }
}

/** Pace fast operations across the minimum window without adding concurrency or seed assets. */
export function runBenchmarkPhase(phase: BenchmarkPhase, actions: PhaseActions): PhaseResult {
    validatePhase(phase);
    const minimum = durationMilliseconds(phase.minDuration);
    const maximum = durationMilliseconds(phase.maxDuration);
    const started = actions.now();
    let operations = 0;
    let successful = true;
    for (let index = 0; index < phase.samples; index += 1) {
        const wait = started + index * minimum / phase.samples - actions.now();
        if (wait > 0) actions.sleep(wait);
        if (actions.now() - started >= maximum) break;
        successful = actions.run(index);
        operations += 1;
        if (!successful) break;
    }
    if (successful && operations === phase.samples) {
        const wait = started + minimum - actions.now();
        if (wait > 0) actions.sleep(wait);
    }
    const durationMs = actions.now() - started;
    return {
        operations,
        durationMs,
        completed: successful && operations === phase.samples && durationMs >= minimum && durationMs < maximum,
    };
}
