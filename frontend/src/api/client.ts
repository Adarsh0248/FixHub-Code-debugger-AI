import type {
  DebugRequestPayload,
  MeResponse,
  TaskAcceptedResponse,
  TaskResponse,
} from './types';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(
  path: string,
  init?: RequestInit,
): Promise<Response> {
  const headers = new Headers(init?.headers);

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    let message = text || response.statusText;

    try {
      const body = JSON.parse(text) as {
        errorMessage?: string;
        message?: string;
        error?: string;
        taskId?: string;
      };

      message =
        body.errorMessage ??
        body.message ??
        body.error ??
        (body.taskId
          ? `Task ${body.taskId} could not be queued.`
          : message);
    } catch {
      // A plain-text error response is already usable.
    }

    throw new ApiError(response.status, message);
  }

  return response;
}

export async function getMe(): Promise<MeResponse> {
  const response = await request('/api/me');
  return response.json();
}

export async function submitDebugTask(
  owner: string,
  repo: string,
  payload: DebugRequestPayload,
): Promise<TaskAcceptedResponse> {
  const response = await request(
    `/debug/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  );

  return response.json();
}

export async function indexRepo(
  owner: string,
  repo: string,
): Promise<TaskAcceptedResponse> {
  const response = await request(
    `/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/index`,
    { method: 'POST' },
  );

  return response.json();
}

export async function getTask(
  taskId: string,
  signal?: AbortSignal,
): Promise<TaskResponse> {
  const response = await request(
    `/api/tasks/${encodeURIComponent(taskId)}`,
    { signal },
  );

  return response.json();
}

export async function listTasks(
  limit = 20,
  signal?: AbortSignal,
): Promise<TaskResponse[]> {
  const response = await request(
    `/api/tasks?limit=${encodeURIComponent(String(limit))}`,
    { signal },
  );

  return response.json();
}

export const loginUrl = '/oauth2/authorization/github';
export const logoutUrl = '/logout';