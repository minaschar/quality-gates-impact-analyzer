import { useCallback, useState } from 'react';

interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
}

/**
 * Manages the open/pending state for a single ConfirmDialog instance. Call `requestConfirm`
 * with the copy + action for that specific confirmation, then spread `dialogProps` onto
 * <ConfirmDialog />.
 */
export function useConfirmDialog() {
  const [pending, setPending] = useState<ConfirmOptions | null>(null);

  const requestConfirm = useCallback((options: ConfirmOptions) => {
    setPending(options);
  }, []);

  const cancel = useCallback(() => setPending(null), []);

  const confirm = useCallback(() => {
    pending?.onConfirm();
    setPending(null);
  }, [pending]);

  return {
    requestConfirm,
    dialogProps: {
      open: pending !== null,
      title: pending?.title ?? '',
      message: pending?.message ?? '',
      confirmLabel: pending?.confirmLabel,
      danger: pending?.danger,
      onConfirm: confirm,
      onCancel: cancel,
    },
  };
}
