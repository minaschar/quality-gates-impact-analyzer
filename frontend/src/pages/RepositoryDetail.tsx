import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GitFork, LayoutDashboard, ListChecks, RefreshCw, ShieldCheck, Star, Trash2, TrendingUp } from 'lucide-react';
import { ErrorState } from '@/components/common/ErrorState';
import { EmptyState } from '@/components/common/EmptyState';
import { RepositoryDetailSkeleton } from '@/components/common/skeletons';
import { Tabs } from '@/components/common/Tabs';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useDetectQualityGate, useRepositoryDetection } from '@/hooks/useRepositories';
import { useDeleteRepository, useImpactAnalysis, useRunE2EAnalysis } from '@/hooks/useImpactAnalysis';
import { useConfirmDialog } from '@/hooks/useConfirmDialog';
import { classifyError } from '@/utils/errors';
import { OverviewTab } from '@/components/repository/OverviewTab';
import { QualityGatesTab } from '@/components/repository/QualityGatesTab';
import { EnforcementTab } from '@/components/repository/EnforcementTab';
import { QualityImpactTab } from '@/components/repository/QualityImpactTab';

const TABS = [
  { id: 'overview', label: 'Overview', icon: <LayoutDashboard className="h-4 w-4" /> },
  { id: 'quality-gates', label: 'Quality Gates', icon: <ShieldCheck className="h-4 w-4" /> },
  { id: 'enforcement', label: 'Enforcement', icon: <ListChecks className="h-4 w-4" /> },
  { id: 'quality-impact', label: 'Quality Impact', icon: <TrendingUp className="h-4 w-4" /> },
];

export function RepositoryDetail() {
  const { owner = '', repo = '' } = useParams<{ owner: string; repo: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');

  const detectionQuery = useRepositoryDetection(owner, repo);
  // Only used to decide whether "Re-run Full Analysis" should be shown at all (see below) --
  // QualityImpactTab independently fetches the same query key, so this doesn't add a real
  // extra network cost, just an existence check that TanStack Query dedupes/caches.
  const impactAnalysisQuery = useImpactAnalysis(owner, repo);
  const detectMutation = useDetectQualityGate();
  const e2eAnalysisMutation = useRunE2EAnalysis();
  const deleteMutation = useDeleteRepository();
  const { requestConfirm, dialogProps } = useConfirmDialog();

  if (detectionQuery.isLoading) return <RepositoryDetailSkeleton />;

  if (detectionQuery.isError || !detectionQuery.data) {
    const isNotFound = classifyError(detectionQuery.error).kind === 'not-found';

    if (isNotFound) {
      return (
        <EmptyState
          title={`${owner}/${repo} hasn't been analyzed yet`}
          message="Run detection to find its quality gate tools, enforcement, and history."
          action={
            <Button
              isLoading={detectMutation.isPending}
              onClick={() =>
                detectMutation.mutate(
                  { repositoryUrl: `https://github.com/${owner}/${repo}` },
                  { onSuccess: () => detectionQuery.refetch() }
                )
              }
            >
              {detectMutation.isPending ? 'Analyzing…' : 'Run Detection'}
            </Button>
          }
        />
      );
    }

    const { title, message } = classifyError(detectionQuery.error);
    return <ErrorState title={title} message={message} onRetry={() => detectionQuery.refetch()} />;
  }

  const detection = detectionQuery.data;

  // Detection alone doesn't need a "full" re-run yet -- there's no computed comparison to
  // redo until impact analysis has run at least once (via the Quality Impact tab's own "Run
  // Impact Analysis"). Showing the button before then would just be an unnecessary extra step
  // for what's really a two-step first-time flow: analyze, then re-run everything if needed.
  const hasImpactAnalysis = impactAnalysisQuery.isSuccess && Boolean(impactAnalysisQuery.data);

  function confirmRerunAnalysis() {
    requestConfirm({
      title: 'Re-run full analysis?',
      message: `This bypasses every cache and runs a complete end-to-end analysis for ${detection.owner}/${detection.repo} from scratch: fresh quality-gate detection, commit history, quality metrics, and the before/after comparison, including fresh GitHub API calls. This can take a while for repositories with a large commit history. Continue?`,
      confirmLabel: 'Re-run Full Analysis',
      onConfirm: () => e2eAnalysisMutation.mutate({ owner: detection.owner, repo: detection.repo }),
    });
  }

  function confirmDelete() {
    requestConfirm({
      title: 'Delete this repository?',
      message: `This permanently deletes everything stored for ${detection.owner}/${detection.repo} -- detection, commit history, quality metrics, and impact analysis. No data is left behind. This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
      onConfirm: () =>
        deleteMutation.mutate(
          { owner: detection.owner, repo: detection.repo },
          { onSuccess: () => navigate('/repositories') }
        ),
    });
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">
              {detection.owner}/{detection.repo}
            </h1>
            <a
              href={detection.url}
              target="_blank"
              rel="noreferrer"
              className="text-sm font-medium text-blue-500 hover:underline"
            >
              View on GitHub
            </a>
          </div>
          {detection.description && (
            <p className="mt-1 max-w-2xl text-sm text-slate-500 dark:text-slate-400">{detection.description}</p>
          )}
          <div className="mt-2 flex items-center gap-4 text-sm text-slate-500 dark:text-slate-400">
            <span className="flex items-center gap-1">
              <Star className="h-4 w-4" /> {detection.stars.toLocaleString()}
            </span>
            <span className="flex items-center gap-1">
              <GitFork className="h-4 w-4" /> {detection.forks.toLocaleString()}
            </span>
            {detection.primaryLanguage && <span>{detection.primaryLanguage}</span>}
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          {hasImpactAnalysis && (
            <Button variant="secondary" isLoading={e2eAnalysisMutation.isPending} onClick={confirmRerunAnalysis}>
              {!e2eAnalysisMutation.isPending && <RefreshCw className="h-4 w-4" />}
              {e2eAnalysisMutation.isPending ? 'Running Full Analysis…' : 'Re-run Full Analysis'}
            </Button>
          )}
          <Button variant="danger" isLoading={deleteMutation.isPending} onClick={confirmDelete}>
            {!deleteMutation.isPending && <Trash2 className="h-4 w-4" />}
            {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
          </Button>
        </div>
      </div>

      <Tabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} />

      <div>
        {activeTab === 'overview' && <OverviewTab detection={detection} onNavigateToTab={setActiveTab} />}
        {activeTab === 'quality-gates' && <QualityGatesTab detection={detection} />}
        {activeTab === 'enforcement' && <EnforcementTab detection={detection} />}
        {activeTab === 'quality-impact' && <QualityImpactTab owner={detection.owner} repo={detection.repo} />}
      </div>

      <ConfirmDialog {...dialogProps} />
    </div>
  );
}
