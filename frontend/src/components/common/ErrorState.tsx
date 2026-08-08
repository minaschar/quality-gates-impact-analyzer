import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from '@/components/common/Button';

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

export function ErrorState({ title = 'Something went wrong', message, onRetry }: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-red-200 bg-red-50 py-12 text-center dark:border-red-500/30 dark:bg-red-500/10">
      <AlertTriangle className="h-6 w-6 text-red-500" aria-hidden="true" />
      <div>
        <p className="font-medium text-slate-800 dark:text-slate-100">{title}</p>
        {message && <p className="mt-1 max-w-md text-sm text-slate-500 dark:text-slate-400">{message}</p>}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          Try again
        </Button>
      )}
    </div>
  );
}
