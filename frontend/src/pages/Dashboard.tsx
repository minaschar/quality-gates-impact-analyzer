import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, FolderGit2, PlusCircle, Search, ShieldCheck, TrendingUp } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { ErrorState } from '@/components/common/ErrorState';
import { EmptyState } from '@/components/common/EmptyState';
import { DashboardSkeleton } from '@/components/common/skeletons';
import { TrendBadge } from '@/components/repository/TrendBadge';
import { TrendDistributionChart } from '@/components/charts/TrendDistributionChart';
import { useDetectQualityGate, useRepositories } from '@/hooks/useRepositories';
import { useImpactAnalysisList } from '@/hooks/useImpactAnalysis';
import { formatRelativeTime } from '@/utils/formatters';
import { validateGithubUrl } from '@/utils/validators';
import { classifyError } from '@/utils/errors';
import type { ImpactAnalysisSummaryDto, RepositoryDetectionResult } from '@/types';

// Stable module-level references so `data ?? []` doesn't create a new array identity every
// render, which would otherwise retrigger the useMemo hooks below on every re-render.
const EMPTY_REPOS: RepositoryDetectionResult[] = [];
const EMPTY_ANALYSES: ImpactAnalysisSummaryDto[] = [];

function SummaryCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card className="flex items-start gap-4">
      <div className="rounded-md bg-blue-50 p-2.5 text-blue-500 dark:bg-blue-500/10">{icon}</div>
      <div>
        <p className="text-sm text-slate-500 dark:text-slate-400">{label}</p>
        <p className="mt-1 text-2xl font-bold text-slate-800 dark:text-slate-100">{value}</p>
        {hint && <p className="mt-0.5 text-xs text-slate-400 dark:text-slate-500">{hint}</p>}
      </div>
    </Card>
  );
}

export function Dashboard() {
  const navigate = useNavigate();
  const [repoUrl, setRepoUrl] = useState('');
  const [touched, setTouched] = useState(false);

  const repositoriesQuery = useRepositories();
  const impactAnalysesQuery = useImpactAnalysisList();
  const detectMutation = useDetectQualityGate();

  const repositories = repositoriesQuery.data ?? EMPTY_REPOS;
  const impactAnalyses = impactAnalysesQuery.data ?? EMPTY_ANALYSES;

  const validation = validateGithubUrl(repoUrl);
  const showValidation = touched || repoUrl.length > 0;

  const stats = useMemo(() => {
    const withQG = repositories.filter((r) => r.hasQualityGate).length;
    const scores = impactAnalyses.map((a) => a.overallImprovementScore).filter((s): s is number => s != null);
    const avgImprovement = scores.length ? scores.reduce((sum, s) => sum + s, 0) / scores.length : null;
    return { total: repositories.length, withQG, avgImprovement };
  }, [repositories, impactAnalyses]);

  const recent = useMemo(
    () =>
      [...repositories]
        .sort((a, b) => new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime())
        .slice(0, 6),
    [repositories]
  );

  function trendFor(owner: string, repo: string) {
    return impactAnalyses.find((a) => a.owner === owner && a.repo === repo)?.overallTrend;
  }

  function handleQuickAnalyze(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!validation.valid) return;
    detectMutation.mutate(
      { repositoryUrl: repoUrl.trim() },
      {
        onSuccess: (result) => navigate(`/repository/${result.owner}/${result.repo}`),
      }
    );
  }

  const isLoading = repositoriesQuery.isLoading || impactAnalysesQuery.isLoading;
  const isError = repositoriesQuery.isError || impactAnalysesQuery.isError;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">Dashboard</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Overview of quality gate detection and impact analysis across your repositories.
        </p>
      </div>

      <Card>
        <CardHeader title="Analyze a repository" subtitle="Paste a GitHub URL to detect its quality gates" icon={<Search className="h-5 w-5" />} />
        <form onSubmit={handleQuickAnalyze} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <div className="flex-1">
            <Input
              name="repoUrl"
              placeholder="https://github.com/owner/repository"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              onBlur={() => setTouched(true)}
              error={showValidation && !validation.valid ? validation.error : undefined}
              valid={showValidation && validation.valid}
              aria-label="GitHub repository URL"
              required
            />
          </div>
          <Button type="submit" isLoading={detectMutation.isPending} disabled={showValidation && !validation.valid}>
            {detectMutation.isPending ? 'Analyzing…' : 'Analyze'}
            {!detectMutation.isPending && <ArrowRight className="h-4 w-4" />}
          </Button>
        </form>
      </Card>

      {isLoading && <DashboardSkeleton />}
      {isError &&
        !isLoading &&
        (() => {
          const { title, message } = classifyError(repositoriesQuery.error ?? impactAnalysesQuery.error);
          return (
            <ErrorState
              title={title}
              message={message}
              onRetry={() => {
                repositoriesQuery.refetch();
                impactAnalysesQuery.refetch();
              }}
            />
          );
        })()}

      {!isLoading && !isError && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <SummaryCard
              icon={<FolderGit2 className="h-5 w-5" />}
              label="Repositories Analyzed"
              value={String(stats.total)}
            />
            <SummaryCard
              icon={<ShieldCheck className="h-5 w-5" />}
              label="Repos with Quality Gate"
              value={String(stats.withQG)}
              hint={stats.total ? `${Math.round((stats.withQG / stats.total) * 100)}% of analyzed repos` : undefined}
            />
            <SummaryCard
              icon={<TrendingUp className="h-5 w-5" />}
              label="Avg. Quality Improvement"
              value={stats.avgImprovement != null ? `${stats.avgImprovement.toFixed(1)}%` : '—'}
              hint={`${impactAnalyses.length} impact ${impactAnalyses.length === 1 ? 'analysis' : 'analyses'}`}
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Recent Analyses" subtitle="Most recently detected repositories" />
              {recent.length === 0 ? (
                <EmptyState
                  title="No repositories analyzed yet"
                  message="Analyze your first repository to see detection results and quality trends here."
                  action={
                    <Button onClick={() => navigate('/analyze')}>
                      <PlusCircle className="h-4 w-4" />
                      Analyze your first repository
                    </Button>
                  }
                />
              ) : (
                <ul className="divide-y divide-slate-100 dark:divide-slate-700">
                  {recent.map((r) => (
                    <li key={`${r.owner}/${r.repo}`}>
                      <button
                        onClick={() => navigate(`/repository/${r.owner}/${r.repo}`)}
                        className="flex w-full items-center justify-between gap-3 py-3 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/50 -mx-2 px-2 rounded-md"
                      >
                        <div className="min-w-0">
                          <p className="truncate font-medium text-slate-800 dark:text-slate-100">
                            {r.owner}/{r.repo}
                          </p>
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            Analyzed {formatRelativeTime(r.detectedAt)}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <TrendBadge trend={trendFor(r.owner, r.repo)} />
                          <ArrowRight className="h-4 w-4 text-slate-300" />
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardHeader title="Impact Trends" subtitle="Distribution across all analyzed tools" />
              <TrendDistributionChart analyses={impactAnalyses} />
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
