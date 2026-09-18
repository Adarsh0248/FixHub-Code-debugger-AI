import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, listTasks } from '../api/client';
import type { TaskResponse } from '../api/types';

const POLL_INTERVAL_MS = 3000;

export function useTasks() {
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const mounted = useRef(false);
  const activeRequest = useRef<AbortController | null>(null);

  const refresh = useCallback(async () => {
    if (!mounted.current) return;

    // A manual refresh replaces any request already in progress.
    activeRequest.current?.abort();

    const controller = new AbortController();
    activeRequest.current = controller;

    try {
      const nextTasks = await listTasks(20, controller.signal);

      if (
        mounted.current &&
        activeRequest.current === controller
      ) {
        setTasks(nextTasks);
        setError(null);
      }
    } catch (cause) {
      if (
        !mounted.current ||
        controller.signal.aborted ||
        activeRequest.current !== controller
      ) {
        return;
      }

      if (cause instanceof ApiError && cause.status === 401) {
        setTasks([]);
        setError('Your session has expired. Please sign in again.');
      } else {
        setError(
          'Could not refresh task history. Displayed statuses may be outdated.',
        );
      }
    } finally {
      if (
        mounted.current &&
        activeRequest.current === controller
      ) {
        activeRequest.current = null;
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    let stopped = false;
    let timer: number | undefined;

    async function poll() {
      await refresh();

      if (!stopped) {
        timer = window.setTimeout(
          () => void poll(),
          POLL_INTERVAL_MS,
        );
      }
    }

    void poll();

    return () => {
      stopped = true;
      mounted.current = false;

      if (timer !== undefined) {
        window.clearTimeout(timer);
      }

      activeRequest.current?.abort();
      activeRequest.current = null;
    };
  }, [refresh]);

  return {
    tasks,
    loading,
    error,
    refresh,
  };
}