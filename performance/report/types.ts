export interface BenchmarkPhase {
    samples: number;
    minDuration: string;
    maxDuration: string;
}

export interface BenchmarkProfile {
    warmup: BenchmarkPhase;
    measurement: BenchmarkPhase;
}

export interface BenchmarkProtocol extends BenchmarkProfile {
    methodology: 'single-vu-v1';
}

export interface DurationMetrics {
    min: number;
    p50: number;
    p90: number;
    p95: number;
    p99: number;
    max: number;
}

export interface CheckMetrics {
    passed: number;
    failed: number;
}

export interface NormalizedMetrics {
    operations: number;
    requests: number;
    errors: number;
    checks: CheckMetrics;
    droppedIterations: number;
    durationMs: DurationMetrics;
}

export interface NormalizedResult {
    benchmark?: BenchmarkProtocol;
    version: 1;
    runId: string;
    suite: 'smoke' | 'release';
    workload: string;
    case: string;
    repetition: number;
    environment: string;
    subject: string;
    startedAt: string;
    completedAt: string;
    metrics: NormalizedMetrics;
    passed: boolean;
}

export interface AggregatedDurationMetrics {
    p50: number;
    p90: number;
    p95: number;
    p99: number;
    p95Min: number;
    p95Max: number;
}

export interface AggregatedResult {
    benchmark?: BenchmarkProtocol;
    workload: string;
    case: string;
    repetitions: number;
    operations: number;
    requests: number;
    errors: number;
    checks: CheckMetrics;
    droppedIterations: number;
    durationMs: AggregatedDurationMetrics;
    notes?: string[];
    passed: boolean;
}

export interface PerformanceRelease {
    version: 1;
    runId: string;
    suite: 'smoke' | 'release';
    environment: string;
    subject: string;
    startedAt: string;
    completedAt: string;
    notes?: string[];
    results: AggregatedResult[];
    passed: boolean;
}

export interface PerformanceHistory {
    version: 1;
    releases: PerformanceRelease[];
}

export interface EnvironmentHardware {
    system?: string;
    architecture?: string;
    processor?: string;
    physicalCores?: number;
    logicalCpus?: number;
    memoryGiB?: number;
}

export interface EnvironmentProfile {
    runtime?: 'compose' | 'aws';
    aws?: AwsEnvironment;
    benchmarkMethodology?: string;
    displayName?: string;
    description?: string;
    hardware?: EnvironmentHardware;
    interpretation?: string[];
    composeFile?: string;
    services?: Array<{
        container: string;
        cpus: number;
        memoryBytes: number;
    }>;
}

export interface AwsInstance {
    instanceType: string;
    architecture: 'arm64';
    vcpus: number;
    memoryGiB: number;
    container: { cpus: number; memoryBytes: number };
    metadata: { httpTokens: 'required'; httpPutResponseHopLimit: 2 };
}

export interface AwsEnvironment {
    network: { traffic: 'private'; sameAvailabilityZone: true; s3Endpoint: 'gateway' };
    konifer: AwsInstance;
    k6: AwsInstance;
    scratch: { type: 'instance-store'; hostPath: string; containerPath: '/app/tmp'; filesystem: 'ext4' };
    rds: {
        engine: 'postgres';
        engineMajorVersion: string;
        instanceClass: string;
        multiAz: false;
        publiclyAccessible: false;
        storage: { type: 'gp3'; allocatedGiB: number; encrypted: true };
        database: string;
        user: string;
        sslMode: 'verify-full';
        sslRootCert: string;
        forceSsl: true;
    };
    s3: { storageClass: 'STANDARD'; encryption: 'AES256'; blockPublicAccess: true; credentials: 'instance-role' };
}

export interface EnvironmentCatalog {
    loadDefault?: string;
    default: string;
    profiles: Record<string, EnvironmentProfile>;
}

export interface WorkloadDefinition {
    description: string;
    case?: string;
    cases?: Array<{ id: string }>;
}

export interface WorkloadSuite {
    repetitions: number;
    workloads: string[];
}

export interface WorkloadCatalog {
    suites: Record<string, WorkloadSuite> & { release: WorkloadSuite };
    workloads: Record<string, WorkloadDefinition>;
}

export interface EnvironmentSummary {
    id: string;
    displayName: string;
    description: string | null;
    hardware: EnvironmentHardware | null;
    interpretation: string[];
}

export interface LatestSeriesResult extends AggregatedResult {
    subject: string;
    environment: string;
    completedAt: string;
    hasPrevious: boolean;
    changeMs: number | null;
    changePercent: number | null;
}

export interface SeriesPoint {
    completedAt: string;
    subject: string;
    environment: string;
    repetitions: number;
    p50: number;
    p95: number;
}

export interface LoadDurationMetrics {
    p50: number;
    p90: number;
    p95: number;
    p99: number;
}

export interface LoadStreamResult {
    id: string;
    workload: string;
    case: string;
    targetRatePerMinute: number;
    plannedOperations: number;
    operations: number;
    requests: number;
    errors: number;
    checks: CheckMetrics;
    droppedIterations: number;
    durationMs: LoadDurationMetrics;
    notes?: string[];
    passed: boolean;
}

export interface LoadRecoveryResult {
    healthPassed: boolean;
    eagerReady: boolean;
    durationMs: number;
    checks: CheckMetrics;
    passed: boolean;
}

export interface LoadRun {
    version: 1;
    runId: string;
    profile: string;
    environment: string;
    subject: string;
    startedAt: string;
    completedAt: string;
    trafficDurationSeconds: number;
    peakRatePerMinute: number;
    targetOperations: number;
    operations: number;
    requests: number;
    errors: number;
    checks: CheckMetrics;
    droppedIterations: number;
    streams: LoadStreamResult[];
    recovery: LoadRecoveryResult;
    notes?: string[];
    passed: boolean;
}

export interface LoadHistory {
    version: 1;
    runs: LoadRun[];
}

export interface LatestLoadStream extends LoadStreamResult {
    hasPrevious: boolean;
    changePercent: number | null;
}

export interface LatestLoadRun extends Omit<LoadRun, 'streams'> {
    streams: LatestLoadStream[];
}

export interface LoadStreamDefinition {
    id: string;
    workload: string;
    case: string;
    targetRate: number;
}

export interface LoadProfileDefinition {
    description: string;
    timeUnit: string;
    rampUpDuration: string;
    steadyDuration: string;
    rampDownDuration: string;
    recoveryTimeout: string;
    streams: LoadStreamDefinition[];
    recovery: {
        workload: string;
        case: string;
    };
}

export interface LoadCatalog {
    version: 1;
    defaultProfile: string;
    profiles: Record<string, LoadProfileDefinition>;
}
