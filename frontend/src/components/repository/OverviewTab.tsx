import { ArrowRight, RotateCcw } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { Skeleton } from '@/components/common/Skeleton';
import { QGToolBadge } from '@/components/repository/QGToolBadge';
import { EnforcementStatusBadge } from '@/components/repository/EnforcementStatusBadge';
import { TrendBadge } from '@/components/repository/TrendBadge';
import { useImpactAnalysis } from '@/hooks/useImpactAnalysis';
import { formatDateTime, formatScore01AsPercent } from '@/utils/formatters';
import { classifyError } from '@/utils/errors';
import type { RepositoryDetectionResult } from '@/types';

interface OverviewTabProps {
  detection: RepositoryDetectionResult;
  onNavigateToTab: (tab: string) => void;
}

export function OverviewTab({ detection, onNavigateToTab }: OverviewTabProps) {
  const tools = [...new Set(detection.thesisRelevantDetections.map((d) => d.tool))];
  const impactQuery = useImpactAnalysis(detection.owner, detection.repo);
  const impact = impactQuery.data;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <CardHeader title="Repository Info" />
        <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-slate-500 dark:text-slate-400">Default Branch</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-100">{detection.defaultBranch ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-slate-500 dark:text-slate-400">Primary Language</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-100">{detection.primaryLanguage ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-slate-500 dark:text-slate-400">Detected At</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-100">{formatDateTime(detection.detectedAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500 dark:text-slate-400">API Calls Used</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-100">{detection.apiCallsMade}</dd>
          </div>
          <div>
            <dt className="text-slate-500 dark:text-slate-400">Detection Time</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-100">{detection.detectionTimeMs} ms</dd>
          </div>
        </dl>

        {detection.conclusion && (
          <div className="mt-5 rounded-md bg-slate-50 p-4 text-sm text-slate-600 dark:bg-slate-700/40 dark:text-slate-300">
            <p className="font-medium text-slate-700 dark:text-slate-200">Conclusion</p>
            <p className="mt-1">{detection.conclusion}</p>
            {detection.recommendation && <p className="mt-2 text-slate-500 dark:text-slate-400">{detection.recommendation}</p>}
          </div>
        )}
      </Card>

      <div className="space-y-6">
        <Card>
          <CardHeader title="Enforcement" />
          <div className="flex items-center justify-between">
            <EnforcementStatusBadge status={detection.enforcement?.status} />
            {detection.enforcement?.score != null && (
              <span className="text-lg font-bold text-slate-800 dark:text-slate-100">
                {formatScore01AsPercent(detection.enforcement.score)}
              </span>
            )}
          </div>
          <button
            onClick={() => onNavigateToTab('enforcement')}
            className="mt-3 flex items-center gap-1 text-sm font-medium text-blue-500 hover:underline"
          >
            View details <ArrowRight className="h-3.5 w-3.5" />
          </button>
        </Card>

        <Card>
          <CardHeader title="Quality Impact" />
          {impactQuery.isLoading ? (
            <Skeleton className="h-6 w-32" />
          ) : impact?.hasQualityGate ? (
            <div className="flex items-center justify-between">
              <TrendBadge trend={impact.overallTrend} />
              {impact.overallImprovementScore != null && (
                <span className="text-lg font-bold text-slate-800 dark:text-slate-100">
                  {impact.overallImprovementScore.toFixed(1)}%
                </span>
              )}
            </div>
          ) : impactQuery.isError && classifyError(impactQuery.error).kind !== 'not-found' ? (
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm text-red-500">{classifyError(impactQuery.error).title}</p>
              <button
                onClick={() => impactQuery.refetch()}
                aria-label="Retry loading quality impact"
                className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700"
              >
                <RotateCcw className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <p className="text-sm text-slate-400">Not computed yet.</p>
          )}
          <button
            onClick={() => onNavigateToTab('quality-impact')}
            className="mt-3 flex items-center gap-1 text-sm font-medium text-blue-500 hover:underline"
          >
            View details <ArrowRight className="h-3.5 w-3.5" />
          </button>
        </Card>
      </div>

      <Card className="lg:col-span-3">
        <CardHeader title="Quality Gate Tools Detected" subtitle={`${tools.length} tool${tools.length === 1 ? '' : 's'} found`} />
        {tools.length === 0 ? (
          <p className="text-sm text-slate-400">No quality gate tools detected.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {tools.map((tool) => (
              <QGToolBadge key={tool} tool={tool} />
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
