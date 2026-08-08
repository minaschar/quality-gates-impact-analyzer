import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, Home, RotateCw } from 'lucide-react';
import { Button } from '@/components/common/Button';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error:', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-4 text-center dark:bg-slate-900">
          <div className="rounded-full bg-red-100 p-3 text-red-500 dark:bg-red-500/20">
            <AlertTriangle className="h-6 w-6" aria-hidden="true" />
          </div>
          <div>
            <p className="text-lg font-semibold text-slate-800 dark:text-slate-100">Something went wrong</p>
            <p className="mt-1 max-w-md text-sm text-slate-500 dark:text-slate-400">
              An unexpected error crashed this part of the page. You can try reloading, or head back to the
              dashboard.
            </p>
            <p className="mt-2 max-w-md break-words rounded-md bg-slate-100 px-3 py-1.5 font-mono text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
              {this.state.error.message}
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => window.location.reload()}>
              <RotateCw className="h-4 w-4" />
              Reload Page
            </Button>
            <Button onClick={() => window.location.assign('/')}>
              <Home className="h-4 w-4" />
              Back to Dashboard
            </Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
