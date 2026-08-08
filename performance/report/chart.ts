import type { SeriesPoint } from './types.ts';

export interface ChartBounds {
    left: number;
    right: number;
    top: number;
    bottom: number;
}

export interface GridLine {
    value: number;
    y: number;
}

export interface HistoryChartPoint extends SeriesPoint {
    x: number;
    y50: number;
    y95: number;
}

export interface SparklinePoint extends SeriesPoint {
    x: number;
    y: number;
}

export interface HistoryChartLayout {
    gridLines: GridLine[];
    points: HistoryChartPoint[];
}

export function layoutHistoryChart(
    points: SeriesPoint[],
    width: number,
    height: number,
    bounds: ChartBounds,
): HistoryChartLayout {
    const plotWidth = width - bounds.left - bounds.right;
    const plotHeight = height - bounds.top - bounds.bottom;
    const maximum = Math.max(...points.flatMap((value) => [value.p50, value.p95]), 1) * 1.1;
    const gridLines = Array.from({ length: 5 }, (_, index) => ({
        value: maximum * (1 - index / 4),
        y: bounds.top + (plotHeight * index) / 4,
    }));

    return {
        gridLines,
        points: points.map((value, index) => ({
            ...value,
            x: points.length === 1
                ? bounds.left + plotWidth / 2
                : bounds.left + (plotWidth * index) / (points.length - 1),
            y50: bounds.top + plotHeight * (1 - value.p50 / maximum),
            y95: bounds.top + plotHeight * (1 - value.p95 / maximum),
        })),
    };
}

export function layoutSparkline(
    points: SeriesPoint[],
    width: number,
    height: number,
    padding: number,
): SparklinePoint[] {
    const maximum = Math.max(...points.map((value) => value.p95), 1);
    return points.map((value, index) => ({
        ...value,
        x: points.length === 1
            ? width / 2
            : padding + index * ((width - padding * 2) / (points.length - 1)),
        y: padding + (maximum - value.p95) * ((height - padding * 2) / maximum),
    }));
}

export function linePath<T extends { x: number }>(
    points: T[],
    y: (point: T) => number,
): string {
    return points
        .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${y(point)}`)
        .join(' ');
}
