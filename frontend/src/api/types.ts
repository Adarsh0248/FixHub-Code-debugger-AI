export interface DebugRequestPayload {
  userQ: string;
  branch: string;
  mode: DebugMode;
}

export type DebugMode = 'GUIDE_ONLY' | 'FIX_AND_COMMIT';

export interface RepositoryBranch {
  name: string;
  commitSha: string;
  protectedBranch: boolean;
}

export interface RepositoryBranchPage {
  branches: RepositoryBranch[];
  page: number;
  pageSize: number;
  hasNext: boolean;
}

export interface MeResponse {
  authenticated: boolean;
  username?: string;
  avatarUrl?: string;
}



export type TaskType =
  | 'INDEX_REPOSITORY'
  | 'DEBUG_REPOSITORY';

export type TaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED';

export interface TaskAcceptedResponse {
  taskId: string;
  status: TaskStatus;
  statusUrl: string;
}

export type TaskStage =
  | 'QUEUED'
  | 'STARTING'
  | 'FETCHING'
  | 'CHUNKING'
  | 'STORING_MANIFEST'
  | 'SUBMITTING'
  | 'WAITING_FOR_INDEX'
  | 'RETRIEVING'
  | 'GENERATING'
  | 'COMMITTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'PUBLICATION_FAILED'
  | 'WORKER_TIMEOUT';



export interface TaskResponse {
  taskId: string;
  taskType: TaskType;
  repositoryId: number;
  owner: string;
  repo: string;
  branch: string;
  baseCommitSha: string;
  generationId: string | null;
  debugMode: DebugMode | null;
  requestSummary: string | null;

  status: TaskStatus;
  stage: TaskStage;
  eventSequence: number;

  progressCurrent: number | null;
  progressTotal: number | null;
  statusMessage: string | null;

  errorCode: string | null;
  errorMessage: string | null;

  resultBranch: string | null;
  resultCommitSha: string | null;
  resultUrl: string | null;
  resultExplanation: string | null;

  validationSummary: string | null;

  createdAt: string;
  updatedAt: string;
}
