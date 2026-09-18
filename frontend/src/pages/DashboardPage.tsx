import { FormEvent, useState } from "react";
import { toast } from "sonner";
import { ApiError, indexRepo, submitDebugTask } from "../api/client";
import type { TaskResponse } from "../api/types";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Header } from "../components/Header";
import { useTasks } from "../hooks/useTasks";

interface DashboardPageProps {
  username: string;
  avatarUrl: string;
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "An unexpected error occurred";
}

function getTaskTitle(task: TaskResponse): string {
  return task.taskType === "INDEX_REPOSITORY"
    ? "Repository indexing"
    : "Debug request";
}

function getStatusClass(status: TaskResponse["status"]): string {
  switch (status) {
    case "COMPLETED":
      return "bg-green-100 text-green-800";
    case "FAILED":
      return "bg-red-100 text-red-800";
    case "RUNNING":
      return "bg-blue-100 text-blue-800";
    case "QUEUED":
      return "bg-yellow-100 text-yellow-800";
    default:
      return "bg-gray-100 text-gray-800";
  }
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);

  if (Number.isNaN(date.getTime())) {
    return timestamp;
  }

  return date.toLocaleString();
}

export function DashboardPage({ username, avatarUrl }: DashboardPageProps) {
  const [owner, setOwner] = useState("");
  const [repo, setRepo] = useState("");
  const [userQuery, setUserQuery] = useState("");
  const [indexing, setIndexing] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const {
    tasks,
    loading: tasksLoading,
    error: tasksError,
    refresh,
  } = useTasks();

  const canIndex = Boolean(owner.trim() && repo.trim() && !indexing);

  const canSubmit = Boolean(
    owner.trim() &&
      repo.trim() &&
      userQuery.trim() &&
      !submitting,
  );

  async function handleIndex(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canIndex) {
      return;
    }

    setIndexing(true);

    try {
      const task = await indexRepo(owner.trim(), repo.trim());

      toast.success(
        `Indexing task accepted: ${task.taskId.slice(0, 8)}`,
      );

      await refresh();
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setIndexing(false);
    }
  }

  async function handleDebug(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canSubmit) {
      return;
    }

    setSubmitting(true);

    try {
      const task = await submitDebugTask(
        owner.trim(),
        repo.trim(),
        { userQ: userQuery.trim() },
      );

      toast.success(
        `Debug task accepted: ${task.taskId.slice(0, 8)}`,
      );

      setUserQuery("");
      await refresh();
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Header username={username} avatarUrl={avatarUrl} />

      <main className="mx-auto max-w-6xl space-y-8 px-4 py-8 sm:px-6 lg:px-8">
        <section>
          <h1 className="text-3xl font-bold text-slate-900">
            BugBrother dashboard
          </h1>

          <p className="mt-2 text-slate-600">
            Index a repository, submit an error, and track the durable
            background task.
          </p>
        </section>

        <section className="grid gap-6 lg:grid-cols-2">
          <Card>
            <form className="space-y-4" onSubmit={handleIndex}>
              <div>
                <h2 className="text-xl font-semibold text-slate-900">
                  Index repository
                </h2>

                <p className="mt-1 text-sm text-slate-600">
                  Store the repository code in the vector search system.
                </p>
              </div>

              <div>
                <label
                  htmlFor="index-owner"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  GitHub owner
                </label>

                <input
                  id="index-owner"
                  value={owner}
                  onChange={(event) => setOwner(event.target.value)}
                  placeholder="repository-owner"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                />
              </div>

              <div>
                <label
                  htmlFor="index-repo"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Repository name
                </label>

                <input
                  id="index-repo"
                  value={repo}
                  onChange={(event) => setRepo(event.target.value)}
                  placeholder="repository-name"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                />
              </div>

              <Button type="submit" disabled={!canIndex}>
                {indexing ? "Starting indexing..." : "Index repository"}
              </Button>
            </form>
          </Card>

          <Card>
            <form className="space-y-4" onSubmit={handleDebug}>
              <div>
                <h2 className="text-xl font-semibold text-slate-900">
                  Submit debugging task
                </h2>

                <p className="mt-1 text-sm text-slate-600">
                  Describe the error after the repository has been indexed.
                </p>
              </div>

              <div>
                <label
                  htmlFor="debug-query"
                  className="mb-1 block text-sm font-medium text-slate-700"
                >
                  Error or debugging request
                </label>

                <textarea
                  id="debug-query"
                  value={userQuery}
                  onChange={(event) => setUserQuery(event.target.value)}
                  rows={8}
                  placeholder="Paste the error and explain the expected behavior..."
                  className="w-full resize-y rounded-lg border border-slate-300 px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                />
              </div>

              <Button type="submit" disabled={!canSubmit}>
                {submitting ? "Submitting..." : "Start debugging"}
              </Button>
            </form>
          </Card>
        </section>

        <section>
          <div className="mb-4 flex items-center justify-between gap-4">
            <div>
              <h2 className="text-2xl font-semibold text-slate-900">
                Task history
              </h2>

              <p className="mt-1 text-sm text-slate-600">
                Status comes from the ingestion service database.
              </p>
            </div>

            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={tasksLoading}
              onClick={() => void refresh()}
            >
              {tasksLoading ? "Refreshing..." : "Refresh"}
            </Button>
          </div>

          {tasksError && (
            <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              {tasksError}
            </div>
          )}

          {tasksLoading && tasks.length === 0 && (
            <Card>
              <p className="text-slate-600">Loading tasks...</p>
            </Card>
          )}

          {!tasksLoading && tasks.length === 0 && (
            <Card>
              <p className="text-slate-600">
                No tasks have been submitted yet.
              </p>
            </Card>
          )}

          <div className="space-y-4">
            {tasks.map((task) => (
              <Card key={task.taskId}>
                <div className="flex flex-col justify-between gap-4 sm:flex-row">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold text-slate-900">
                        {getTaskTitle(task)}
                      </h3>

                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-semibold ${getStatusClass(
                          task.status,
                        )}`}
                      >
                        {task.status}
                      </span>
                    </div>

                    <p className="mt-2 text-sm text-slate-700">
                      {task.owner}/{task.repo}
                    </p>

                    {task.requestSummary && (
                      <p className="mt-3 whitespace-pre-wrap text-sm text-slate-600">
                        {task.requestSummary}
                      </p>
                    )}

                    <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
                      <div>
                        <dt className="font-medium text-slate-700">
                          Current stage
                        </dt>
                        <dd className="text-slate-600">{task.stage}</dd>
                      </div>

                      <div>
                        <dt className="font-medium text-slate-700">
                          Progress
                        </dt>
                        <dd className="text-slate-600">
                          {task.progressCurrent ?? 0}
                          {task.progressTotal !== null
                            ? ` / ${task.progressTotal}`
                            : ""}
                        </dd>
                      </div>

                      <div>
                        <dt className="font-medium text-slate-700">
                          Created
                        </dt>
                        <dd className="text-slate-600">
                          {formatTimestamp(task.createdAt)}
                        </dd>
                      </div>

                      <div>
                        <dt className="font-medium text-slate-700">
                          Last updated
                        </dt>
                        <dd className="text-slate-600">
                          {formatTimestamp(task.updatedAt)}
                        </dd>
                      </div>
                    </dl>

                    <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-200">
                      <div
                        className="h-full rounded-full bg-blue-600 transition-all"
                        style={{
                          width: `${
                            task.progressTotal && task.progressTotal > 0
                              ? Math.min(
                                  100,
                                  Math.max(
                                    0,
                                    ((task.progressCurrent ?? 0) /
                                      task.progressTotal) *
                                      100,
                                  ),
                                )
                              : 0
                          }%`,
                        }}
                      />
                    </div>

                    {task.statusMessage && (
                      <p className="mt-3 text-sm text-slate-600">
                        {task.statusMessage}
                      </p>
                    )}

                    {task.errorMessage && (
                      <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                        {task.errorMessage}
                      </p>
                    )}

                    <p className="mt-3 break-all text-xs text-slate-400">
                      Task ID: {task.taskId}
                    </p>
                  </div>

                  {task.resultUrl && (
                    <div className="shrink-0">
                      <a
                        href={task.resultUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
                      >
                        View result
                      </a>
                    </div>
                  )}
                </div>
              </Card>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
