import http, { type Params, type Response } from 'k6/http';
import type { QueryParameters, Tags, UploadContext } from '../types.ts';

export const benchmarkMetadata = JSON.stringify({
    alt: 'Performance benchmark fixture',
    tags: ['performance', 'benchmark'],
    labels: { suite: 'konifer-performance' },
});

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

export function uploadAsset(context: UploadContext, assetPath: string, extraTags?: Tags): Response {
    return http.post(
        `${context.baseUrl}/assets${assetPath}`,
        {
            metadata: benchmarkMetadata,
            asset: http.file(context.sourceBytes, `fixture.${context.sourceFormat}`, context.sourceFile.mediaType),
        },
        requestParameters(context.workloadId, context.caseId, 'upload', extraTags),
    );
}

export function entryUrl(
    baseUrl: string,
    assetPath: string,
    entryId: string | number,
    selector: string,
): string {
    return `${baseUrl}/assets${assetPath}/-/entry/${entryId}/${selector}`;
}

export function withQuery(url: string, parameters: QueryParameters): string {
    const values = Object.entries(parameters).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    return values.length === 0 ? url : `${url}?${values.join('&')}`;
}

export function parseJson<T>(response: Response): T | null {
    try {
        return response.json() as T;
    } catch (_) {
        return null;
    }
}

export function header(response: Response, name: string): string {
    const expected = name.toLowerCase();
    for (const [key, value] of Object.entries(response.headers)) {
        if (key.toLowerCase() === expected) return value;
    }
    return '';
}

export function contentType(response: Response): string {
    return header(response, 'Content-Type').split(';')[0].trim().toLowerCase();
}

export function logUnexpectedResponse(response: Response, expectedStatus: number): void {
    if (response.status !== expectedStatus) {
        console.error(`Expected HTTP ${expectedStatus}, received ${response.status}: ${response.body}`);
    }
}

export function safe(value: string): string {
    return value.replace(/[^a-zA-Z0-9_-]/g, '-');
}
