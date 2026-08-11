import { GitCommitHorizontal, RefreshCw } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useConfirmDialog } from '@/hooks/useConfirmDialog';
import { QGToolBadge } from '@/components/repository/QGToolBadge';
import { useDetectQualityGate } from '@/hooks/useRepositories';
import { formatDate } from '@/utils/formatters';
import type { RepositoryDetectionResult } from '@/types';

interface QualityGatesTabProps {
  detection: RepositoryDetectionResult;
}

export function QualityGatesTab({ detection }: QualityGatesTabProps) {
  const detectMutation = useDetectQualityGate();
  const { requestConfirm, dialogProps } = useConfirmDialog();

  const introductions = [...(detection.qualityGateHistory?.toolIntroductions ?? [])].sort((a, b) => {
    if (!a.effectiveDate) return 1;
    if (!b.effectiveDate) return -1;
    return new Date(a.effectiveDate).getTime() - new Date(b.effectiveDate).getTime();
  });

  function confirmRedetect() {
    requestConfirm({
      title: 'Redetect quality gates?',
      message: `This bypasses the cache and re-runs quality gate detection for ${detection.owner}/${detection.repo} from scratch, including fresh GitHub API calls. Commit history and quality metrics aren't affected -- use Re-run Full Analysis on this page's header instead if you want those refreshed too. Continue?`,
      confirmLabel: 'Redetect',
      onConfirm: () => detectMutation.mutate({ repositoryUrl: detection.url, forceNewDetection: true }),
    });
  }

  const redetectButton = (
    <Button variant="secondary" size="sm" isLoading={detectMutation.isPending} onClick={confirmRedetect}>
      {!detectMutation.isPending && <RefreshCw className="h-3.5 w-3.5" />}
      {detectMutation.isPending ? 'Redetecting…' : 'Redetect'}
    </Button>
  );

  if (introductions.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No quality gate history available"
          message="History detection may have been skipped or found no tool introductions for this repository."
          action={redetectButton}
        />
        <ConfirmDialog {...dialogProps} />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Introduction Timeline"
          subtitle="Order in which quality gate tools were added"
          action={redetectButton}
        />
        <div className="overflow-x-auto pb-2">
          <div className="flex min-w-max items-start gap-0">
            {introductions.map((intro, i) => (
              <div key={intro.tool} className="flex items-start">
                <div className="flex w-44 flex-col items-center text-center">
                  <span className="h-3 w-3 rounded-full bg-blue-500 ring-4 ring-blue-100 dark:ring-blue-500/20" />
                  <p className="mt-2 text-sm font-medium text-slate-800 dark:text-slate-100">
                    {intro.tool.replace(/_/g, ' ')}
                  </p>
                  <p className="text-xs text-slate-500 dark:text-slate-400">{formatDate(intro.effectiveDate)}</p>
                </div>
                {i < introductions.length - 1 && (
                  <div className="mt-1.5 h-0.5 w-8 shrink-0 bg-slate-200 dark:bg-slate-600" />
                )}
              </div>
            ))}
          </div>
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {introductions.map((intro) => (
          <Card key={intro.tool}>
            <div className="flex items-center justify-between">
              <QGToolBadge tool={intro.tool} category={intro.category} />
              {intro.presentSinceRepoCreation && (
                <span className="text-xs text-slate-400">Since repo creation</span>
              )}
            </div>

            <div className="mt-3 space-y-2 text-sm">
              <div className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
                <GitCommitHorizontal className="h-4 w-4 text-slate-400" />
                <span className="font-mono text-xs">{intro.effectiveIntroductionCommit?.shortSha ?? '—'}</span>
                <span className="text-slate-400">by {intro.effectiveIntroductionCommit?.author ?? 'unknown'}</span>
              </div>
              <p className="text-slate-500 dark:text-slate-400">{formatDate(intro.effectiveDate)}</p>
              {intro.introductionSummary && (
                <p className="rounded-md bg-slate-50 p-3 text-slate-600 dark:bg-slate-700/40 dark:text-slate-300">
                  {intro.introductionSummary}
                </p>
              )}
            </div>
          </Card>
        ))}
      </div>

      <ConfirmDialog {...dialogProps} />
    </div>
  );
}
