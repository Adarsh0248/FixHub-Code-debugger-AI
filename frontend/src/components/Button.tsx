import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md';
  loading?: boolean;
  icon?: ReactNode;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium select-none ' +
  'transition-transform duration-150 ease-out-strong active:scale-[0.97] ' +
  'disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none ' +
  'focus-visible:ring-2 focus-visible:ring-accent-400 focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-white dark:focus-visible:ring-offset-[#0b0b10]';

const variants: Record<NonNullable<ButtonProps['variant']>, string> = {
  primary: 'bg-accent-600 text-white hover:bg-accent-700',
  secondary:
    'bg-white text-[#14141c] border border-black/10 hover:bg-black/[0.03]',
  ghost: 'text-[#14141c]/70 hover:bg-black/5 dark:text-white/70 dark:hover:bg-white/10',
  danger: 'bg-red-600 text-white hover:bg-red-700',
};

const sizes: Record<NonNullable<ButtonProps['size']>, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  icon,
  children,
  className = '',
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? (
        <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : (
        icon
      )}
      {children}
    </button>
  );
}
