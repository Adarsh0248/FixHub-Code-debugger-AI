import type { DebugRequestPayload, MeResponse } from './types';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const res = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || res.statusText);
  }

  return res;
}

export async function getMe(): Promise<MeResponse> {
  const res = await request('/api/me');
  return res.json();
}

export async function submitDebugTask(
  owner: string,
  repo: string,
  payload: DebugRequestPayload,
): Promise<string> {
  const res = await request(`/debug/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.text();
}

export async function indexRepo(owner: string, repo: string): Promise<string> {
  const res = await request(
    `/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/index`,
    { method: 'POST' },
  );
  return res.text();
}

export const loginUrl = '/oauth2/authorization/github';
export const logoutUrl = '/logout';
