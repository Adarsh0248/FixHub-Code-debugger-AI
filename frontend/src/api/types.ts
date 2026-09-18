export interface DebugRequestPayload {
  userQ: string;
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

export interface TaskResponse {
  taskId: string;
  taskType: TaskType;
  repositoryId: number;
  owner: string;
  repo: string;
  branch: string;
  baseCommitSha: string;

  requestSummary: string | null;

  status: TaskStatus;
  stage: string;
  eventSequence: number;

  progressCurrent: number | null;
  progressTotal: number | null;
  statusMessage: string | null;

  errorCode: string | null;
  errorMessage: string | null;

  resultBranch: string | null;
  resultCommitSha: string | null;
  resultUrl: string | null;

  validationSummary: string | null;

  createdAt: string;
  updatedAt: string;
}