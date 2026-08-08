import assert from 'node:assert/strict';
import test from 'node:test';
import { layoutHistoryChart, layoutSparkline, linePath } from './chart.ts';
import type { SeriesPoint } from './types.ts';

const points: SeriesPoint[] = [
    point('v1.0.0', 50, 100, 1),
    point('v1.1.0', 100, 200, 3),
];

test('history chart layout calculates coordinates without a DOM', () => {
    const layout = layoutHistoryChart(points, 100, 100, {
        left: 10,
        right: 10,
        top: 10,
        bottom: 10,
    });

    assert.deepEqual(layout.gridLines.map((value) => value.y), [10, 30, 50, 70, 90]);
    assert.deepEqual(layout.gridLines.map((value) => Math.round(value.value)), [220, 165, 110, 55, 0]);
    assert.equal(layout.points[0].x, 10);
    assert.equal(layout.points[1].x, 90);
    assert.equal(linePath([{ x: 1, y: 2 }, { x: 3, y: 4 }], (value) => value.y), 'M1,2 L3,4');
});

test('sparkline layout preserves release metadata including repetitions', () => {
    const layout = layoutSparkline(points, 100, 40, 5);

    assert.equal(layout[0].x, 5);
    assert.equal(layout[0].y, 20);
    assert.equal(layout[1].x, 95);
    assert.equal(layout[1].y, 5);
    assert.equal(layout[1].repetitions, 3);
});

function point(subject: string, p50: number, p95: number, repetitions: number): SeriesPoint {
    return {
        completedAt: '2026-08-05T00:00:00Z',
        subject,
        environment: 'local-compose-v2',
        repetitions,
        p50,
        p95,
    };
}
