import { useCallback, useEffect, useState } from 'react';
import { getMe } from '../api/client';
import type { MeResponse } from '../api/types';

type AuthState =
  | { status: 'loading' }
  | { status: 'signed-out' }
  | { status: 'signed-in'; username: string; avatarUrl: string };

export function useAuth() {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const refresh = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const me: MeResponse = await getMe();
      if (me.authenticated && me.username) {
        setState({ status: 'signed-in', username: me.username, avatarUrl: me.avatarUrl ?? '' });
      } else {
        setState({ status: 'signed-out' });
      }
    } catch {
      setState({ status: 'signed-out' });
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { ...state, refresh };
}
