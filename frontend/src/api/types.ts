export interface DebugRequestPayload {
  userQ: string;
}

export interface MeResponse {
  authenticated: boolean;
  username?: string;
  avatarUrl?: string;
}

export interface Submission {
  id: string;
  owner: string;
  repo: string;
  userQ: string;
  submittedAt: string;
}
