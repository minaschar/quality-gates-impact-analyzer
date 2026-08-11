import toast from 'react-hot-toast';
import { classifyError } from '@/utils/errors';

/** Thin wrapper so every call site gets the same styling/icons without repeating options. */
export const notify = {
  success(message: string) {
    toast.success(message, {
      style: { background: '#ECFDF5', color: '#065F46', border: '1px solid #A7F3D0' },
      iconTheme: { primary: '#10B981', secondary: '#ECFDF5' },
    });
  },
  error(error: unknown, fallbackTitle?: string) {
    const { title, message } = classifyError(error);
    toast.error(fallbackTitle ? `${fallbackTitle}: ${message}` : `${title} — ${message}`, {
      style: { background: '#FEF2F2', color: '#991B1B', border: '1px solid #FECACA' },
      iconTheme: { primary: '#EF4444', secondary: '#FEF2F2' },
      duration: 6000,
    });
  },
  warning(message: string) {
    toast(message, {
      icon: '⚠️',
      style: { background: '#FFFBEB', color: '#92400E', border: '1px solid #FDE68A' },
    });
  },
  /** Neutral follow-up tip -- not an error/warning, just a nudge toward a next action. */
  info(message: string) {
    toast(message, {
      icon: 'ℹ️',
      style: { background: '#EFF6FF', color: '#1E40AF', border: '1px solid #BFDBFE' },
      duration: 6000,
    });
  },
};
