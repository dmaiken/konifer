import assert from 'node:assert/strict';
import test from 'node:test';
import { compareReleaseRecords, compareReleaseVersions, isReleaseVersion, isStableRelease } from './release-version.ts';

test('stable release validation accepts only vMAJOR.MINOR.PATCH', () => {
    assert.equal(isStableRelease('v0.9.0'), true);
    assert.equal(isStableRelease('v10.20.30'), true);
    assert.equal(isStableRelease('v0.9.0-rc.1'), false);
    assert.equal(isStableRelease('0.9.0'), false);
});

test('release comparison is numeric rather than lexical', () => {
    assert.ok(compareReleaseVersions('v0.9.0', 'v0.10.0') < 0);
    assert.ok(compareReleaseVersions('v2.0.0', 'v10.0.0') < 0);
    assert.ok(compareReleaseVersions('v1.9.9', 'v2.0.0') < 0);
    assert.equal(compareReleaseVersions('v1.2.3', 'v1.2.3'), 0);
});

test('record comparison uses completion time only within the same release', () => {
    const backfilled = { subject: 'v0.9.0', completedAt: '2026-08-22T22:20:00Z' };
    const existing = { subject: 'v0.10.0', completedAt: '2026-08-22T21:28:00Z' };
    assert.ok(compareReleaseRecords(backfilled, existing) < 0);
});


test('release validation accepts stable and numbered RC tags only', () => {
    for (const subject of ['v0.9.0', 'v1.0.0-rc.1', 'v1.0.0-rc.10']) {
        assert.equal(isReleaseVersion(subject), true, subject);
    }
    for (const subject of [
        '1.0.0-rc.1', 'v1.0.0-alpha.1', 'v1.0.0-beta.1', 'v1.0.0-dirty',
        'v1.0.0-rc', 'v1.0.0-rc.0', 'v1.0.0-rc.01', 'v1.0.0-rc.-1',
        'v01.0.0', 'v1.00.0-rc.1', 'v1.0.00-rc.1', 'v1.0.0-rc.1+build',
        'v1.0.0+build', 'v1.0.0-rc.1-dirty',
    ]) {
        assert.equal(isReleaseVersion(subject), false, subject);
    }
});

test('RC versions sort numerically before their final release', () => {
    const ordered = ['v0.9.0', 'v1.0.0-rc.2', 'v1.0.0-rc.10', 'v1.0.0', 'v1.1.0-rc.1'];
    assert.deepEqual([...ordered].reverse().sort(compareReleaseVersions), ordered);
    assert.equal(compareReleaseVersions('v1.0.0-rc.1', 'v1.0.0-rc.1'), 0);
});

test('RC records use completion time only for the same version', () => {
    const first = { subject: 'v1.0.0-rc.1', completedAt: '2026-08-22T22:20:00Z' };
    const later = { ...first, completedAt: '2026-08-23T22:20:00Z' };
    assert.ok(compareReleaseRecords(first, later) < 0);
    assert.ok(compareReleaseRecords(later, { ...first, subject: 'v1.0.0' }) < 0);
});

test('unrecognized labels retain lexical comparison', () => {
    assert.equal(compareReleaseVersions('working-tree', 'v1.0.0'), 'working-tree'.localeCompare('v1.0.0'));
});
