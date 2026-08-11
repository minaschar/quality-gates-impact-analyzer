import { useMemo } from 'react';
import { RefreshCw } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { ErrorState } from '@/components/common/ErrorState';
import { EmptyState } from '@/components/common/EmptyState';
import { ChartSkeleton, DetailSectionSkeleton } from '@/components/common/skeletons';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useConfirmDialog } from '@/hooks/useConfirmDialog';
import { QGToolBadge } from '@/components/repository/QGToolBadge';
import { TrendBadge } from '@/components/repository/TrendBadge';
import { QualityTimelineChart } from '@/components/charts/QualityTimelineChart';
import { BeforeAfterChart } from '@/components/charts/BeforeAfterChart';
import { useImpactAnalysis, useRunImpactAnalysis } from '@/hooks/useImpactAnalysis';
import { formatDate, formatDateTime, formatPercent } from '@/utils/formatters';
import { classifyError } from '@/utils/errors';
import type { TimelinePointDto, ToolComparisonDto } from '@/types';

interface QualityImpactTabProps {
  owner: string;
  repo: string;
}

/** Picks the metrics with the largest (non-null) movement to keep charts readable. */
function topMetrics(comparison: ToolComparisonDto, limit = 3): string[] {
  return Object.entries(comparison.metrics)
    .filter(([key, m]) => key !== 'ncloc' && m.deltaPercent != null)
    .sort((a, b) => Math.abs(b[1].deltaPercent!) - Math.abs(a[1].deltaPercent!))
    .slice(0, limit)
    .map(([key]) => key);
}

function ToolImpactCard({ comparison, timeline }: { comparison: ToolComparisonDto; timeline: TimelinePointDto[] }) {
  const toolTimeline = timeline.filter((t) => t.tool === comparison.tool);
  const metrics = topMetrics(comparison);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <QGToolBadge tool={comparison.tool} category={comparison.toolCategory} />
          <span className="text-sm text-slate-500 dark:text-slate-400">
            introduced {formatDate(comparison.introducedAt)}
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500 dark:text-slate-400">
            {comparison.samplesBefore} before / {comparison.samplesAfter} after
          </span>
          <TrendBadge trend={comparison.trend} />
          {comparison.improvementScore != null && (
            <span className="text-lg font-bold text-slate-800 dark:text-slate-100">
              {formatPercent(comparison.improvementScore)}
            </span>
          )}
        </div>
      </div>

      {comparison.trend === 'INSUFFICIENT_DATA' ? (
        <p className="mt-4 text-sm text-slate-400">Not enough before/after samples to compare.</p>
      ) : (
        <div className="mt-5 grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div>
            <p className="mb-2 text-sm font-medium text-slate-600 dark:text-slate-300">Metrics Over Time</p>
            <QualityTimelineChart data={toolTimeline} introducedAt={comparison.introducedAt} metrics={metrics} height={260} />
          </div>
          <div>
            <p className="mb-2 text-sm font-medium text-slate-600 dark:text-slate-300">Before vs. After (avg)</p>
            <BeforeAfterChart metrics={comparison.metrics} include={metrics} height={260} />
          </div>
        </div>
      )}
    </Card>
  );
}

export function QualityImpactTab({ owner, repo }: QualityImpactTabProps) {
  const impactQuery = useImpactAnalysis(owner, repo);
  const runMutation = useRunImpactAnalysis();
  const { requestConfirm, dialogProps } = useConfirmDialog();

  const errorInfo = impactQuery.isError ? classifyError(impactQuery.error) : null;
  const notComputedYet = errorInfo?.kind === 'not-found';

  const summary = useMemo(() => {
    const data = impactQuery.data;
    if (!data) return null;
    const items: { label: string; value?: string; node?: React.ReactNode }[] = [
      { label: 'Tools Analyzed', value: String(data.toolsAnalyzed) },
      { label: 'Overall Trend', node: <TrendBadge trend={data.overallTrend} /> },
      {
        label: 'Improvement Score',
        value: data.overallImprovementScore != null ? formatPercent(data.overallImprovementScore) : '—',
      },
      {
        label: 'Date Range',
        value: data.dateRangeStart ? `${formatDate(data.dateRangeStart)} – ${formatDate(data.dateRangeEnd)}` : '—',
      },
      { label: 'Analyzed At', value: data.computedAt ? formatDateTime(data.computedAt) : '—' },
    ];
    return items;
  }, [impactQuery.data]);

  if (impactQuery.isLoading) {
    return (
      <div className="space-y-6">
        <DetailSectionSkeleton lines={2} />
        <Card>
          <ChartSkeleton />
        </Card>
      </div>
    );
  }

  if (notComputedYet) {
    return (
      <Card>
        <EmptyState
          title="No impact analysis yet"
          message="Run an impact analysis to compare code quality metrics before and after quality gate introduction."
          action={
            <Button isLoading={runMutation.isPending} onClick={() => runMutation.mutate({ owner, repo })}>
              {!runMutation.isPending && <RefreshCw className="h-4 w-4" />}
              {runMutation.isPending ? 'Running Analysis…' : 'Run Impact Analysis'}
            </Button>
          }
        />
      </Card>
    );
  }

  if (errorInfo || !impactQuery.data) {
    return (
      <ErrorState
        title={errorInfo?.title ?? 'Something went wrong'}
        message={errorInfo?.message ?? 'Could not load impact analysis.'}
        onRetry={() => impactQuery.refetch()}
      />
    );
  }

  const data = impactQuery.data;

  if (!data.hasQualityGate) {
    return (
      <Card>
        <EmptyState
          title="No quality gate detected"
          message="This repository has no detected quality gate tool, so there's nothing to compare before/after. Check the Overview tab to confirm detection results, or re-run detection if you believe this is wrong."
        />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Impact Summary"
          subtitle={data.cached ? 'Loaded from cache' : 'Freshly computed'}
          action={
            <Button
              variant="secondary"
              size="sm"
              isLoading={runMutation.isPending}
              onClick={() =>
                requestConfirm({
                  title: 'Recompute impact analysis?',
                  message: 'This bypasses the cache and recomputes the before/after comparison from scratch.',
                  confirmLabel: 'Recompute',
                  onConfirm: () => runMutation.mutate({ owner, repo, forceNewAnalysis: true }),
                })
              }
            >
              {!runMutation.isPending && <RefreshCw className="h-3.5 w-3.5" />}
              {runMutation.isPending ? 'Recomputing…' : 'Recompute'}
            </Button>
          }
        />
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {summary?.map((s) => (
            <div key={s.label}>
              <p className="text-xs text-slate-500 dark:text-slate-400">{s.label}</p>
              <div className="mt-1 font-semibold text-slate-800 dark:text-slate-100">{s.node ?? s.value}</div>
            </div>
          ))}
        </div>
      </Card>

      {data.comparisons.length === 0 ? (
        <Card>
          <EmptyState
            title="No tool comparisons"
            message="No dated tool introductions were available to compare, so before/after metrics couldn't be computed for any tool."
          />
        </Card>
      ) : (
        data.comparisons.map((comparison) => (
          <ToolImpactCard key={comparison.tool} comparison={comparison} timeline={data.timeline} />
        ))
      )}

      <ConfirmDialog {...dialogProps} />
    </div>
  );
}
