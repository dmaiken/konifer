import assert from 'node:assert/strict';
import test from 'node:test';
import { compareReleaseRecords, compareReleaseVersions, isStableRelease } from './release-version.ts';

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
