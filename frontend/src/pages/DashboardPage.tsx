import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { ApiError, indexRepo, submitDebugTask } from '../api/client';
import type { Submission } from '../api/types';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Header } from '../components/Header';

const STORAGE_KEY = 'bugbrother.submissions';

function loadSubmissions(): Submission[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Submission[]) : [];
  } catch {
    return [];
  }
}

interface DashboardPageProps {
  username: string;
  avatarUrl: string;
}

export function DashboardPage({ username, avatarUrl }: DashboardPageProps) {
  const [owner, setOwner] = useState('');
  const [repo, setRepo] = useState('');
  const [userQ, setUserQ] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const [submissions, setSubmissions] = useState<Submission[]>(loadSubmissions);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(submissions));
  }, [submissions]);

  const hasRepo = Boolean(owner.trim() && repo.trim());
  const canSubmit = hasRepo && userQ.trim();

  async function handleIndex() {
    if (!hasRepo || indexing) return;
    setIndexing(true);

    const trimmedOwner = owner.trim();
    const trimmedRepo = repo.trim();
    const promise = indexRepo(trimmedOwner, trimmedRepo);

    toast.promise(promise, {
      loading: `Indexing ${trimmedOwner}/${trimmedRepo} into the search engine…`,
      success: 'Indexing started — it runs in the background on the worker queue.',
      error: (err) =>
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : 'Could not reach the ingestion service',
    });

    try {
      await promise;
    } catch {
      // toast.promise already surfaced the error
    } finally {
      setIndexing(false);
    }
  }

  async function handleSubmit() {
    if (!canSubmit || submitting) return;
    setSubmitting(true);

    const trimmedOwner = owner.trim();
    const trimmedRepo = repo.trim();
    const trimmedUserQ = userQ.trim();

    const promise = submitDebugTask(trimmedOwner, trimmedRepo, { userQ: trimmedUserQ });

    toast.promise(promise, {
      loading: 'Searching the repo index for related files and sending to the worker queue…',
      success: () => `Task accepted — watch for a new ai-fix/* branch on ${trimmedOwner}/${trimmedRepo}`,
      error: (err) =>
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : 'Could not reach the ingestion service',
    });

    try {
      await promise;
      setSubmissions((prev) => [
        {
          id: crypto.randomUUID(),
          owner: trimmedOwner,
          repo: trimmedRepo,
          userQ: trimmedUserQ,
          submittedAt: new Date().toISOString(),
        },
        ...prev,
      ].slice(0, 20));
      setUserQ('');
    } catch {
      // toast.promise already surfaced the error
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen">
      <Header username={username} avatarUrl={avatarUrl} />

      <main className="mx-auto max-w-3xl px-4 py-8">
        <Card className="p-5 sm:p-6">
          <h2 className="mb-1 text-base font-semibold">Report a bug for an AI fix</h2>
          <p className="mb-5 text-sm text-black/50 dark:text-white/50">
            Paste the error below. It's used to search the repo's vector index for the related
            files, which are fixed by the LLM and committed to a new{' '}
            <code className="rounded bg-black/5 px-1 py-0.5 font-mono text-xs dark:bg-white/10">ai-fix/*</code>{' '}
            branch on GitHub.
          </p>

          <div className="mb-2 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-black/60 dark:text-white/60">
                Repo owner
              </label>
              <input
                value={owner}
                onChange={(e) => setOwner(e.target.value)}
                placeholder="octocat"
                className="w-full rounded-md border border-black/10 bg-white px-3 py-2 text-sm
                  outline-none transition-colors focus:border-accent-400 dark:border-white/10 dark:bg-white/5"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-black/60 dark:text-white/60">
                Repo name
              </label>
              <input
                value={repo}
                onChange={(e) => setRepo(e.target.value)}
                placeholder="hello-world"
                className="w-full rounded-md border border-black/10 bg-white px-3 py-2 text-sm
                  outline-none transition-colors focus:border-accent-400 dark:border-white/10 dark:bg-white/5"
              />
            </div>
          </div>

          <div className="mb-5 flex items-center justify-between gap-3">
            <p className="text-xs text-black/40 dark:text-white/40">
              New repo, or changed a lot since last time? Index it so RAG has something to find.
            </p>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              loading={indexing}
              disabled={!hasRepo}
              onClick={handleIndex}
              className="shrink-0"
            >
              Index repo
            </Button>
          </div>

          <div className="mb-6">
            <label className="mb-1 block text-xs font-medium text-black/60 dark:text-white/60">
              What's the error?
            </label>
            <textarea
              value={userQ}
              onChange={(e) => setUserQ(e.target.value)}
              placeholder="NullPointerException at com.example.Foo.bar(Foo.java:42)…"
              rows={6}
              spellCheck={false}
              className="scroll-thin w-full resize-y rounded-md border border-black/10 bg-white px-3 py-2
                font-mono text-xs leading-5 outline-none transition-colors focus:border-accent-400
                dark:border-white/10 dark:bg-white/5"
            />
          </div>

          <Button
            onClick={handleSubmit}
            loading={submitting}
            disabled={!canSubmit}
            className="w-full"
          >
            {submitting ? 'Submitting…' : 'Submit for AI fix'}
          </Button>
        </Card>

        {submissions.length > 0 && (
          <div className="mt-8">
            <h3 className="mb-3 text-sm font-semibold text-black/70 dark:text-white/70">
              Recent submissions
            </h3>
            <div className="space-y-2">
              {submissions.map((s, i) => (
                <Card
                  key={s.id}
                  className="flex items-center justify-between p-3 opacity-0"
                  style={{
                    animation: 'fade-in-up 240ms var(--ease-out-strong) forwards',
                    animationDelay: `${Math.min(i * 40, 160)}ms`,
                  }}
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {s.owner}/{s.repo}
                    </p>
                    <p className="truncate text-xs text-black/50 dark:text-white/50">{s.userQ}</p>
                  </div>
                  <a
                    href={`https://github.com/${s.owner}/${s.repo}/branches/all?query=ai-fix`}
                    target="_blank"
                    rel="noreferrer"
                    className="shrink-0 pl-3"
                  >
                    <Button variant="ghost" size="sm">
                      View branches ↗
                    </Button>
                  </a>
                </Card>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
