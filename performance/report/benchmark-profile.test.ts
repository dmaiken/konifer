import assert from 'node:assert/strict';
import test from 'node:test';
import { runBenchmarkPhase, validatePhase, type BenchmarkPhase } from '../lib/benchmark-profile.ts';

const phase: BenchmarkPhase = { samples: 4, minDuration: '1s', maxDuration: '3s' };

test('fast operations span the minimum window without extra samples', () => {
    let now = 0;
    const starts: number[] = [];
    const result = runBenchmarkPhase(phase, {
        now: () => now,
        sleep: (milliseconds) => { now += milliseconds; },
        run: () => { starts.push(now); now += 10; return true; },
    });
    assert.deepEqual(starts, [0, 250, 500, 750]);
    assert.deepEqual(result, { operations: 4, durationMs: 1000, completed: true });
});

test('slow operations run sequentially and keep the full sample count', () => {
    let now = 0;
    const result = runBenchmarkPhase(phase, {
        now: () => now,
        sleep: () => assert.fail('slow operations need no pacing sleep'),
        run: () => { now += 500; return true; },
    });
    assert.deepEqual(result, { operations: 4, durationMs: 2000, completed: true });
});

test('a phase cannot pass with partial samples or a deadline overrun', () => {
    for (const duration of [800, 1500]) {
        let now = 0;
        const result = runBenchmarkPhase(phase, {
            now: () => now,
            sleep: (milliseconds) => { now += milliseconds; },
            run: () => { now += duration; return true; },
        });
        assert.equal(result.completed, false);
        if (duration === 800) assert.equal(result.operations, 4);
        else assert.equal(result.operations, 2);
    }
});

test('failed operations stop the phase rather than accumulating a success-only sample', () => {
    let now = 0;
    const result = runBenchmarkPhase(phase, {
        now: () => now,
        sleep: (milliseconds) => { now += milliseconds; },
        run: (index) => index !== 1,
    });
    assert.deepEqual(result, { operations: 2, durationMs: 250, completed: false });
});

test('measurement starts after warmup completes, including background draining', () => {
    let now = 0;
    const starts: number[] = [];
    for (let stage = 0; stage < 2; stage += 1) {
        const result = runBenchmarkPhase(phase, {
            now: () => now,
            sleep: (milliseconds) => { now += milliseconds; },
            run: () => { starts.push(now); now += 400; return true; },
        });
        assert.equal(result.completed, true);
    }
    assert.equal(starts[4], 1600);
});

test('invalid sample counts and impossible duration budgets fail before execution', () => {
    for (const samples of [0, -1, 0.5, NaN]) {
        assert.throws(() => validatePhase({ ...phase, samples }), /positive integer/);
    }
    assert.throws(() => validatePhase({ ...phase, maxDuration: '1s' }), /exceed/);
    assert.throws(() => validatePhase({ ...phase, minDuration: 'bad' }), /Invalid benchmark duration/);
});
