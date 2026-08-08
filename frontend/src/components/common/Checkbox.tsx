import { forwardRef, type InputHTMLAttributes } from 'react';

interface CheckboxProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  description?: string;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, description, id, className = '', ...props }, ref) => {
    const inputId = id ?? props.name;
    return (
      <label htmlFor={inputId} className="flex cursor-pointer items-start gap-2.5">
        <input
          ref={ref}
          id={inputId}
          type="checkbox"
          className={[
            'mt-0.5 h-4 w-4 rounded border-slate-300 text-blue-500',
            'focus:ring-2 focus:ring-blue-500',
            'dark:border-slate-600 dark:bg-slate-800',
            className,
          ].join(' ')}
          {...props}
        />
        <span>
          <span className="block text-sm font-medium text-slate-700 dark:text-slate-300">{label}</span>
          {description && <span className="block text-xs text-slate-500 dark:text-slate-400">{description}</span>}
        </span>
      </label>
    );
  }
);
Checkbox.displayName = 'Checkbox';
