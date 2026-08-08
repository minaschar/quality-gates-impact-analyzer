import { Card } from '@/components/common/Card';
import { Skeleton, SkeletonText } from '@/components/common/Skeleton';

/** Matches Dashboard's SummaryCard layout. */
export function SummaryCardSkeleton() {
  return (
    <Card className="flex items-start gap-4">
      <Skeleton className="h-10 w-10 shrink-0 rounded-md" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3.5 w-24" />
        <Skeleton className="h-7 w-16" />
        <Skeleton className="h-3 w-32" />
      </div>
    </Card>
  );
}

/** A single row matching RepoTable's column layout. */
export function RepoTableRowSkeleton() {
  return (
    <tr>
      <td className="px-4 py-3">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="mt-1.5 h-3 w-16" />
      </td>
      <td className="px-4 py-3">
        <div className="flex gap-1">
          <Skeleton className="h-5 w-16 rounded-full" />
          <Skeleton className="h-5 w-16 rounded-full" />
        </div>
      </td>
      <td className="px-4 py-3">
        <Skeleton className="h-5 w-24 rounded-full" />
      </td>
      <td className="px-4 py-3">
        <Skeleton className="h-5 w-20 rounded-full" />
      </td>
      <td className="px-4 py-3">
        <Skeleton className="h-4 w-20" />
      </td>
      <td className="px-4 py-3" />
    </tr>
  );
}

export function RepoTableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-100 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:bg-slate-700/40 dark:text-slate-400">
            <th className="px-4 py-3">Repository</th>
            <th className="px-4 py-3">QG Tools</th>
            <th className="px-4 py-3">Enforcement</th>
            <th className="px-4 py-3">Quality Impact</th>
            <th className="px-4 py-3">Last Analyzed</th>
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
          {Array.from({ length: rows }).map((_, i) => (
            <RepoTableRowSkeleton key={i} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Placeholder for chart areas -- a pulsing block roughly matching the chart's aspect ratio. */
export function ChartSkeleton({ height = 260 }: { height?: number }) {
  return (
    <div className="flex flex-col gap-3" style={{ height }}>
      <div className="flex flex-1 items-end gap-2 px-2">
        {[45, 70, 55, 85, 60, 90, 50].map((h, i) => (
          <Skeleton key={i} className="flex-1 rounded-t-md rounded-b-none" style={{ height: `${h}%` }} />
        ))}
      </div>
      <Skeleton className="h-3 w-full" />
    </div>
  );
}

/** Generic section skeleton for detail-page cards: a title line plus a few body lines. */
export function DetailSectionSkeleton({ lines = 4 }: { lines?: number }) {
  return (
    <Card>
      <Skeleton className="mb-4 h-4 w-32" />
      <SkeletonText lines={lines} />
    </Card>
  );
}

export function RepositoryHeaderSkeleton() {
  return (
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
      <div className="space-y-2">
        <Skeleton className="h-7 w-64" />
        <Skeleton className="h-4 w-96" />
        <Skeleton className="h-4 w-48" />
      </div>
      <Skeleton className="h-9 w-36 rounded-md" />
    </div>
  );
}

export function SettingsSkeleton() {
  return (
    <div className="space-y-6">
      {Array.from({ length: 3 }).map((_, cardIdx) => (
        <Card key={cardIdx}>
          <Skeleton className="mb-4 h-4 w-32" />
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, rowIdx) => (
              <div key={rowIdx} className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1.5 sm:w-1/2">
                  <Skeleton className="h-3.5 w-40" />
                  <Skeleton className="h-3 w-56" />
                </div>
                <Skeleton className="h-9 w-full sm:w-1/2" />
              </div>
            ))}
          </div>
        </Card>
      ))}
    </div>
  );
}

export function RepositoryDetailSkeleton() {
  return (
    <div className="space-y-6">
      <RepositoryHeaderSkeleton />
      <div className="flex gap-4 border-b border-slate-200 pb-2 dark:border-slate-700">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-6 w-24" />
        ))}
      </div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <DetailSectionSkeleton lines={5} />
        </div>
        <div className="space-y-6">
          <DetailSectionSkeleton lines={2} />
          <DetailSectionSkeleton lines={2} />
        </div>
      </div>
    </div>
  );
}

export function DashboardSkeleton() {
  return (
    <div className="space-y-8">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <SummaryCardSkeleton />
        <SummaryCardSkeleton />
        <SummaryCardSkeleton />
      </div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <Skeleton className="mb-4 h-4 w-32" />
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center justify-between">
                <div className="space-y-1.5">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-24" />
                </div>
                <Skeleton className="h-5 w-20 rounded-full" />
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <Skeleton className="mb-4 h-4 w-32" />
          <ChartSkeleton height={180} />
        </Card>
      </div>
    </div>
  );
}
