import { useIsFetching, useIsMutating } from '@tanstack/react-query';

/** Subtle YouTube/GitHub-style progress bar shown whenever any query or mutation is in flight. */
export function TopLoadingBar() {
  const isFetching = useIsFetching();
  const isMutating = useIsMutating();
  const active = isFetching > 0 || isMutating > 0;

  return (
    <div
      className="fixed left-0 top-0 z-[60] h-0.5 w-full overflow-hidden bg-transparent"
      role="progressbar"
      aria-hidden={!active}
      aria-valuetext={active ? 'Loading' : undefined}
    >
      {active && <div className="h-full w-1/3 animate-loading-bar bg-blue-500" />}
    </div>
  );
}
