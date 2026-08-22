export type QueryValue = string | number | boolean;
export type QueryParameters = Record<string, QueryValue>;
export type Tags = Record<string, string>;

export interface FixtureFile {
    path: string;
    mediaType: string;
}

export interface Fixture {
    width: number;
    height: number;
    files: Record<string, FixtureFile>;
}

export interface FixtureManifest {
    fixtures: Record<string, Fixture>;
}

export interface WorkloadCase {
    id: string;
    sourceFormat?: string;
    destinationFormat?: string;
}

export interface ExpectedImage {
    format: string;
    width: number;
    height: number;
}

export interface WorkloadDefinition {
    description: string;
    profile: string;
    fixture: string;
    sourceFormat?: string;
    case?: string;
    cases?: WorkloadCase[];
    query?: QueryParameters;
    expected?: ExpectedImage;
    expectedFormat?: string;
    path?: string;
    profiles?: string[];
    timeoutSeconds?: number;
}

export interface ArrivalPhase {
    duration: string;
    rate: number;
    timeUnit: string;
}

export interface ExecutionProfile {
    warmup: ArrivalPhase;
    measurement: ArrivalPhase;
    preAllocatedVUs: number;
    maxVUs: number;
}

export interface WorkloadCatalog {
    baseUrl: string;
    profiles: Record<string, ExecutionProfile>;
    workloads: Record<string, WorkloadDefinition>;
}

export interface LoadStreamDefinition {
    id: string;
    workload: string;
    case: string;
    exec: string;
    startRate: number;
    targetRate: number;
    preAllocatedVUs: number;
}

export interface WorkloadReference {
    workload: string;
    case: string;
}

export interface LoadProfile {
    description: string;
    timeUnit: string;
    rampUpDuration: string;
    steadyDuration: string;
    rampDownDuration: string;
    recoveryTimeout: string;
    streams: LoadStreamDefinition[];
    recovery: WorkloadReference;
}

export interface LoadCatalog {
    version: 1;
    defaultProfile: string;
    profiles: Record<string, LoadProfile>;
}

export interface AssetReference {
    assetPath: string;
    entryId: string | number;
    contentUrl: string;
}

export interface AssetPools {
    warmup: AssetReference[];
    measurement: AssetReference[];
}

export interface LoadSetupData {
    cachedOriginal: AssetReference;
    cachedVariant: AssetReference;
    cold: AssetReference[];
}

export interface VariantAttributes {
    format: string;
    width: number;
    height: number;
}

export interface Variant {
    isOriginalVariant: boolean;
    attributes: VariantAttributes;
}

export interface KoniferResponseBody {
    entryId?: string | number;
    variants?: Variant[];
    labels?: Record<string, string>;
}

export interface OperationResult {
    ok: boolean;
    durationMs?: number;
}

export interface MetricSummary {
    values?: Record<string, number | undefined>;
}

export interface SummaryData {
    metrics: Record<string, MetricSummary | undefined>;
}

export type SummaryOutput = Record<string, string>;

export interface UploadContext {
    baseUrl: string;
    sourceBytes: ArrayBuffer;
    sourceFormat: string;
    sourceFile: FixtureFile;
    workloadId: string;
    caseId: string;
}

export interface OperationContext extends UploadContext {
    workload: WorkloadDefinition;
    query: QueryParameters;
}
