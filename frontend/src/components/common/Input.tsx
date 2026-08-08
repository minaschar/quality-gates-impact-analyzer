import { forwardRef, type InputHTMLAttributes } from 'react';
import { CheckCircle2 } from 'lucide-react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  /** Shows a green border + checkmark. Ignored while `error` is set. */
  valid?: boolean;
  required?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, valid, required, id, className = '', ...props }, ref) => {
    const inputId = id ?? props.name;
    const showValid = valid && !error;
    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
            {label}
            {required && (
              <span className="ml-0.5 text-red-500" aria-hidden="true">
                *
              </span>
            )}
          </label>
        )}
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            className={[
              'w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-800 transition-colors',
              'placeholder:text-slate-400',
              'focus:outline-none focus:ring-2 focus:ring-offset-0',
              'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400',
              'dark:bg-slate-800 dark:text-slate-100 dark:placeholder:text-slate-500',
              error
                ? 'border-red-400 focus:border-red-500 focus:ring-red-500 dark:border-red-500/60'
                : showValid
                  ? 'border-emerald-400 focus:border-emerald-500 focus:ring-emerald-500 pr-9 dark:border-emerald-500/60'
                  : 'border-slate-300 focus:border-blue-500 focus:ring-blue-500 dark:border-slate-700',
              className,
            ].join(' ')}
            aria-invalid={Boolean(error)}
            aria-required={required}
            aria-describedby={error ? `${inputId}-error` : undefined}
            {...props}
          />
          {showValid && (
            <CheckCircle2
              className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-emerald-500"
              aria-hidden="true"
            />
          )}
        </div>
        {error && (
          <p id={`${inputId}-error`} className="mt-1 text-sm text-red-500" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);
Input.displayName = 'Input';
