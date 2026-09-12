import { loginUrl } from '../api/client';
import { Button } from '../components/Button';
import { Card } from '../components/Card';

const GITHUB_ICON = (
  <svg viewBox="0 0 24 24" className="h-4 w-4 fill-current">
    <path d="M12 .5C5.73.5.98 5.24.98 11.52c0 4.94 3.2 9.13 7.65 10.61.56.1.77-.24.77-.54 0-.27-.01-1.16-.02-2.1-3.11.68-3.77-1.32-3.77-1.32-.51-1.3-1.24-1.64-1.24-1.64-1.01-.7.08-.68.08-.68 1.12.08 1.71 1.15 1.71 1.15.99 1.7 2.6 1.21 3.24.93.1-.72.39-1.21.71-1.49-2.48-.28-5.1-1.24-5.1-5.54 0-1.22.44-2.22 1.15-3-.11-.28-.5-1.42.11-2.96 0 0 .94-.3 3.08 1.15a10.7 10.7 0 0 1 5.6 0c2.14-1.45 3.08-1.15 3.08-1.15.61 1.54.22 2.68.11 2.96.72.78 1.15 1.78 1.15 3 0 4.31-2.63 5.26-5.13 5.53.4.35.76 1.03.76 2.08 0 1.5-.01 2.71-.01 3.08 0 .3.2.65.78.54A11.03 11.03 0 0 0 23.02 11.5C23.02 5.24 18.27.5 12 .5Z" />
  </svg>
);

export function LoginPage() {
  return (
    <div className="grid min-h-screen place-items-center px-4">
      <div
        className="w-full max-w-sm opacity-0"
        style={{ animation: 'fade-in-up 320ms var(--ease-out-strong) forwards' }}
      >
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <img src="/bug.svg" alt="" className="h-11 w-11" />
          <h1 className="text-xl font-semibold tracking-tight">BugBrother</h1>
          <p className="text-sm text-black/50 dark:text-white/50">
            AI-powered code fixes, committed straight to a new branch on your repo.
          </p>
        </div>

        <Card className="p-6">
          <p className="mb-4 text-center text-sm text-black/60 dark:text-white/60">
            Sign in with GitHub to submit files for an AI-assisted fix.
          </p>
          <a href={loginUrl} className="block">
            <Button variant="primary" size="md" icon={GITHUB_ICON} className="w-full">
              Continue with GitHub
            </Button>
          </a>
        </Card>
      </div>
    </div>
  );
}
