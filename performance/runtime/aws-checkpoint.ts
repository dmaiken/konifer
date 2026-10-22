import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { loadAwsConfiguration, waitForAwsReadiness } from './aws-config.ts';

// Run from the provisioned k6 host. No load test or history publication occurs.
const connectionsFile = process.argv[2];
if (!connectionsFile || process.argv.length !== 3) throw new Error('Usage: node runtime/aws-checkpoint.ts CONNECTIONS.json');
const { connections } = await loadAwsConfiguration(JSON.parse(await readFile(connectionsFile, 'utf8')));
const baseUrl = `http://${connections.koniferPrivateIp}:8080`;
const readinessEndpoint = await waitForAwsReadiness(baseUrl);
console.log(`Readiness probe: ${readinessEndpoint}`);
const fixture = await readFile(new URL('../assets/medium/medium.jpg', import.meta.url));
const assetPath = `/performance/aws-checkpoint/${randomUUID()}`;
try {
    const form = new FormData();
    form.set('metadata', JSON.stringify({ alt: 'AWS configuration checkpoint' }));
    form.set('asset', new Blob([fixture], { type: 'image/jpeg' }), 'fixture.jpg');
    const upload = await fetch(`${baseUrl}/assets${assetPath}`, { method: 'POST', body: form, signal: AbortSignal.timeout(30000) });
    assert.equal(upload.status, 201, 'Upload failed');
    const body = await upload.json() as { entryId?: string | number };
    assert.ok(body.entryId !== undefined, 'Upload must return an entry ID');
    const download = await fetch(`${baseUrl}/assets${assetPath}/-/entry/${encodeURIComponent(String(body.entryId))}/content`, {
        redirect: 'error', signal: AbortSignal.timeout(30000),
    });
    assert.equal(download.status, 200, 'Download failed');
    assert.deepEqual(Buffer.from(await download.arrayBuffer()), fixture, 'Downloaded original does not match fixture');
} finally {
    const cleanup = await fetch(`${baseUrl}/assets${assetPath}/-/recursive`, { method: 'DELETE', signal: AbortSignal.timeout(30000) });
    assert.equal(cleanup.status, 204, 'Checkpoint cleanup failed');
}
console.log('AWS checkpoint passed: readiness, upload, byte-for-byte download, and cleanup.');
