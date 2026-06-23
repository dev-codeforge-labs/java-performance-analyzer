export interface UploadResponse {
  sessionId: string;
  fileNames: string[];
  detectedTypes: string[];
  snapshotCount: number | null;
  warnings: string[];
  timestamp: string;
}

export interface StackFrame {
  className: string;
  methodName: string;
  sourceFile: string;
  lineNumber: number;
  synthetic: boolean;
}

export interface CallTreeNodeDto {
  methodSignature: string;
  layerCategory: string;
  samples: number;
  selfSamples: number;
  totalTimePercent: number;
  selfTimePercent: number;
  children: CallTreeNodeDto[];
}

export interface HotspotDto {
  rank: number;
  methodSignature: string;
  className: string;
  packagePrefix: string;
  layerCategory: string;
  selfTimePercent: number;
  totalTimePercent: number;
  topCallerSignature: string | null;
  diagnosis: string | null;
  severity: string | null;
}

export interface Diagnosis {
  ruleId: string;
  severity: string;
  message: string;
  affectedMethod: string | null;
  layer: string | null;
  source: string;
}

export interface SignalBreakdown {
  signal: string;
  rawValue: number;
  points: number;
  weight: number;
}

export interface HealthScoreDto {
  score: number;
  classification: 'GREEN' | 'YELLOW' | 'RED';
  dominantFactor: string;
  signalBreakdown: SignalBreakdown[];
}

export interface SnapshotSummary {
  index: number;
  timestamp: string | null;
  runnablePercent: number;
  blockedPercent: number;
  waitingPercent: number;
  timedWaitingPercent: number;
  totalThreads: number;
}

export interface ThreadSwimLane {
  threadId: string;
  threadName: string;
  cells: { snapshotIndex: number; state: string; topFrame: string | null }[];
}

export interface HotspotTrend {
  methodSignature: string;
  selfTimePercents: number[];
}

export interface TimelineDto {
  snapshots: SnapshotSummary[];
  swimLanes: ThreadSwimLane[];
  hotspotTrends: HotspotTrend[];
  confirmedDeadlock: boolean;
}

export interface GcEventDto {
  timestamp: number;
  type: string;
  pauseMs: number;
  heapBeforeBytes: number;
  heapAfterBytes: number;
  heapTotalBytes: number;
  gcAlgorithmHint: string;
}

export interface GcLogResult {
  gcAlgorithm: string;
  events: GcEventDto[];
  maxPauseMs: number;
  avgPauseMs: number;
  p99PauseMs: number;
  totalElapsedMs: number;
  gcTimePercent: number;
  promotionFailureDetected: boolean;
  concurrentModeFailureDetected: boolean;
  warnings: { code: string; message: string }[];
}

export interface ParseWarning {
  code: string;
  message: string;
  lineNumber: number;
}

export interface AnalysisResultDto {
  sessionId: string;
  analyzedAt: string;
  fileNames: string[];
  dumpTypes: string[];
  snapshotCount: number;
  totalThreads: number;
  callTree: CallTreeNodeDto | null;
  invertedCallTree: CallTreeNodeDto | null;
  hotspots: HotspotDto[];
  diagnoses: Diagnosis[];
  healthScore: HealthScoreDto | null;
  timeline: TimelineDto | null;
  gcSummary: GcLogResult | null;
  warnings: ParseWarning[];
}

export interface RulesetDto {
  version: string;
  customSignatures: CustomSignature[];
  thresholds: Record<string, number>;
  exportedAt?: string;
}

export interface CustomSignature {
  prefix: string;
  layer: string;
  diagnosisMessage: string;
  severity: string;
}

export interface DiffResultDto {
  diffId: string;
  sessionIdA: string;
  sessionIdB: string;
  deltaThreshold: number;
  callTreeDiff: CallTreeDiffNode[];
  hotspotDiff: HotspotDiffEntry[];
  threadStateDiffA: Record<string, number>;
  threadStateDiffB: Record<string, number>;
  diagnosticDiff: {
    resolved: Diagnosis[];
    added: Diagnosis[];
    persisting: Diagnosis[];
  };
  healthScoreA: HealthScoreDto | null;
  healthScoreB: HealthScoreDto | null;
}

export interface CallTreeDiffNode {
  methodSignature: string;
  status: 'ADDED' | 'REMOVED' | 'REGRESSED' | 'IMPROVED' | 'UNCHANGED';
  totalTimePctA: number;
  totalTimePctB: number;
  delta: number;
  children: CallTreeDiffNode[];
}

export interface HotspotDiffEntry {
  methodSignature: string;
  rankA: number | null;
  rankB: number | null;
  selfTimePctA: number;
  selfTimePctB: number;
  delta: number;
  trend: 'UP' | 'DOWN' | 'NEW' | 'RESOLVED' | 'STABLE';
}

export const DEFAULT_RULESET: RulesetDto = {
  version: '1.0',
  customSignatures: [],
  thresholds: {
    'min-self-time-critical': 5.0,
    'blocked-threads-alert': 20.0,
    'gc-thrash-alert': 10.0,
    'calltree-delta-threshold': 5.0,
  }
};
