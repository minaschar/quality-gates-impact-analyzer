import type { ReactNode } from 'react';

export type BadgeColor = 'blue' | 'emerald' | 'amber' | 'red' | 'slate';

interface BadgeProps {
  children: ReactNode;
  color?: BadgeColor;
  icon?: ReactNode;
  className?: string;
}

const colorClasses: Record<BadgeColor, string> = {
  blue: 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300',
  emerald: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300',
  amber: 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300',
  red: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300',
  slate: 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300',
};

export function Badge({ children, color = 'slate', icon, className = '' }: BadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',
        colorClasses[color],
        className,
      ].join(' ')}
    >
      {icon}
      {children}
    </span>
  );
}
