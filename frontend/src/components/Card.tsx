import type { HTMLAttributes } from 'react';

export function Card({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={
        'rounded-2xl border border-black/10 bg-white shadow-sm ' +
        'dark:border-white/10 dark:bg-white/[0.03] ' +
        className
      }
      {...rest}
    />
  );
}
