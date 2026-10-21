import http, { type Params, type Response } from 'k6/http';
import type { QueryParameters, Tags, UploadContext } from '../types.ts';
import { queryUrl } from '../../lib/query-parameters.ts';

export const benchmarkMetadata = JSON.stringify({
    alt: 'Performance benchmark fixture',
    tags: ['performance', 'benchmark'],
    labels: { suite: 'konifer-performance' },
});

/** Build consistent k6 operation/workload/case tags, allowing caller tags to override defaults. */
export function requestParameters(
    workload: string,
    caseId: string,
    operation = workload,
    extraTags?: Tags,
): Params {
    return {
        tags: {
            operation,
            workload,
            case: caseId,
            ...(extraTags || {}),
        },
    };
}

/** Post the fixture and benchmark metadata as multipart form data, with an optional per-request timeout. */
export function uploadAsset(context: UploadContext, assetPath: string, extraTags?: Tags, timeoutMs?: number): Response {
    return http.post(
        `${context.baseUrl}/assets${assetPath}`,
        {
            metadata: benchmarkMetadata,
            asset: http.file(context.sourceBytes, `fixture.${context.sourceFormat}`, context.sourceFile.mediaType),
        },
        {
            ...requestParameters(context.workloadId, context.caseId, 'upload', extraTags),
            ...(timeoutMs === undefined ? {} : { timeout: `${timeoutMs}ms` }),
        },
    );
}

/** Build a content or metadata URL for a server-issued entry ID and asset path. */
export function entryUrl(
    baseUrl: string,
    assetPath: string,
    entryId: string | number,
    selector: string,
): string {
    return `${baseUrl}/assets${assetPath}/-/entry/${entryId}/${selector}`;
}

/** Apply transformation parameters using the selected current or legacy request naming convention. */
export function withQuery(url: string, parameters: QueryParameters): string {
    return queryUrl(url, parameters, __ENV.USE_LEGACY_QUERY_PARAMS === 'true');
}

/** Decode a response body, returning null so callers can record a failed check for malformed JSON. */
export function parseJson<T>(response: Response): T | null {
    try {
        return response.json() as T;
    } catch (_) {
        return null;
    }
}

/** Find a response header without depending on capitalization; return an empty string if absent. */
export function header(response: Response, name: string): string {
    const expected = name.toLowerCase();
    for (const [key, value] of Object.entries(response.headers)) {
        if (key.toLowerCase() === expected) return value;
    }
    return '';
}

/** Normalize the response media type without parameters for fixture comparisons. */
export function contentType(response: Response): string {
    return header(response, 'Content-Type').split(';')[0].trim().toLowerCase();
}

/** Log the response body only when its status differs from the expected benchmark status. */
export function logUnexpectedResponse(response: Response, expectedStatus: number): void {
    if (response.status !== expectedStatus) {
        console.error(`Expected HTTP ${expectedStatus}, received ${response.status}: ${response.body}`);
    }
}

/** Replace path-unsafe run/workload characters with hyphens for benchmark asset namespaces. */
export function safe(value: string): string {
    return value.replace(/[^a-zA-Z0-9_-]/g, '-');
}
