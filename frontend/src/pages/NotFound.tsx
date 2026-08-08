import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { Button } from '@/components/common/Button';

export function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <div className="rounded-full bg-slate-100 p-3 text-slate-400 dark:bg-slate-800">
        <Compass className="h-6 w-6" />
      </div>
      <div>
        <p className="text-lg font-semibold text-slate-800 dark:text-slate-100">Page not found</p>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">The page you're looking for doesn't exist.</p>
      </div>
      <Link to="/">
        <Button>Back to Dashboard</Button>
      </Link>
    </div>
  );
}
