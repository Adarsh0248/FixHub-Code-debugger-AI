import { logoutUrl } from '../api/client';
import { Button } from './Button';

interface HeaderProps {
  username: string;
  avatarUrl: string;
}

export function Header({ username, avatarUrl }: HeaderProps) {
  return (
    <header className="sticky top-0 z-10 border-b border-black/10 bg-white/80 backdrop-blur-md dark:border-white/10 dark:bg-[#0b0b10]/80">
      <div className="mx-auto flex h-14 max-w-3xl items-center justify-between px-4">
        <div className="flex items-center gap-2">
          <img src="/bug.svg" alt="" className="h-6 w-6" />
          <span className="text-sm font-semibold tracking-tight">BugBrother</span>
          <span className="hidden text-sm text-black/40 dark:text-white/40 sm:inline">
            / Code Guardian
          </span>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            {avatarUrl ? (
              <img src={avatarUrl} alt="" className="h-6 w-6 rounded-full" />
            ) : (
              <div className="h-6 w-6 rounded-full bg-accent-200" />
            )}
            <span className="text-sm text-black/70 dark:text-white/70">{username}</span>
          </div>
          <a href={logoutUrl}>
            <Button variant="ghost" size="sm">
              Sign out
            </Button>
          </a>
        </div>
      </div>
    </header>
  );
}
