export interface LoadSchedule {
    timeUnit: string;
    rampUpDuration: string;
    steadyDuration: string;
    rampDownDuration: string;
}

export interface LoadStreamRates {
    startRate: number;
    targetRate: number;
}

const millisecondsPerMinute = 60000;

/** Calculates all iterations scheduled across the three arrival-rate stages. */
export function plannedOperations(schedule: LoadSchedule, stream: LoadStreamRates): number {
    const timeUnit = durationMilliseconds(schedule.timeUnit);
    const rampUpUnits = durationMilliseconds(schedule.rampUpDuration) / timeUnit;
    const steadyUnits = durationMilliseconds(schedule.steadyDuration) / timeUnit;
    const rampDownUnits = durationMilliseconds(schedule.rampDownDuration) / timeUnit;
    return Math.round(
        ((stream.startRate + stream.targetRate) / 2) * rampUpUnits
        + stream.targetRate * steadyUnits
        + (stream.targetRate / 2) * rampDownUnits,
    );
}

export function peakRatePerMinute(schedule: LoadSchedule, streams: LoadStreamRates[]): number {
    const timeUnitMinutes = durationMilliseconds(schedule.timeUnit) / millisecondsPerMinute;
    return streams.reduce((total, stream) => total + stream.targetRate, 0) / timeUnitMinutes;
}

export function seedPoolSize(schedule: LoadSchedule, stream: LoadStreamRates): number {
    return Math.ceil(plannedOperations(schedule, stream) * 1.05) + 1;
}

export function trafficDurationMilliseconds(schedule: LoadSchedule): number {
    return durationMilliseconds(schedule.rampUpDuration)
        + durationMilliseconds(schedule.steadyDuration)
        + durationMilliseconds(schedule.rampDownDuration);
}

export function targetRatePerMinute(schedule: LoadSchedule, stream: LoadStreamRates): number {
    const timeUnitMinutes = durationMilliseconds(schedule.timeUnit) / millisecondsPerMinute;
    return stream.targetRate / timeUnitMinutes;
}

function durationMilliseconds(value: string): number {
    const match = /^(\d+)(ms|s|m|h)$/.exec(value);
    if (!match) throw new Error(`Invalid duration: ${value}`);

    const amount = Number(match[1]);
    switch (match[2]) {
        case 'ms': return amount;
        case 's': return amount * 1000;
        case 'm': return amount * 60000;
        case 'h': return amount * 3600000;
        default: throw new Error(`Invalid duration unit: ${value}`);
    }
}
