import { useEffect, useState } from 'react';
import { onlineManager } from '@tanstack/react-query';
import { notify } from '@/utils/toast';

/**
 * Tracks browser connectivity for the offline banner. TanStack Query's onlineManager
 * already listens to window online/offline itself by default and auto-resumes paused
 * queries/mutations on reconnect -- the explicit setOnline calls here are redundant with
 * that default but harmless, and mean this hook doesn't depend on Query's internals to
 * know when connectivity actually changed.
 */
export function useOnlineStatus() {
  const [isOnline, setIsOnline] = useState(navigator.onLine);

  useEffect(() => {
    function handleOnline() {
      setIsOnline(true);
      onlineManager.setOnline(true);
      notify.success('Back online — retrying pending requests');
    }
    function handleOffline() {
      setIsOnline(false);
      onlineManager.setOnline(false);
    }

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return isOnline;
}
