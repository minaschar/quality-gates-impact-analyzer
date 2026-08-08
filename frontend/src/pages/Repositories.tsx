import { useEffect, useMemo, useState } from 'react';
import { PlusCircle, Search, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { ErrorState } from '@/components/common/ErrorState';
import { EmptyState } from '@/components/common/EmptyState';
import { Button } from '@/components/common/Button';
import { Pagination } from '@/components/common/Pagination';
import { RepoTableSkeleton } from '@/components/common/skeletons';
import { RepoTable } from '@/components/repository/RepoTable';
import { useRepositories } from '@/hooks/useRepositories';
import { useImpactAnalysisList } from '@/hooks/useImpactAnalysis';
import { classifyError } from '@/utils/errors';
import type { RepositoryDetectionResult } from '@/types';

type QGFilter = 'all' | 'with-qg' | 'without-qg';

// Stable module-level reference so `data ?? []` doesn't create a new array identity every
// render, which would otherwise retrigger the `filtered` useMemo below on every re-render.
const EMPTY_REPOS: RepositoryDetectionResult[] = [];

export function Repositories() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [qgFilter, setQgFilter] = useState<QGFilter>('all');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const repositoriesQuery = useRepositories();
  const impactAnalysesQuery = useImpactAnalysisList();

  const repositories = repositoriesQuery.data ?? EMPTY_REPOS;
  const impactAnalyses = impactAnalysesQuery.data ?? [];

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return repositories.filter((r) => {
      const matchesSearch = !term || `${r.owner}/${r.repo}`.toLowerCase().includes(term);
      const matchesFilter =
        qgFilter === 'all' || (qgFilter === 'with-qg' ? r.hasQualityGate : !r.hasQualityGate);
      return matchesSearch && matchesFilter;
    });
  }, [repositories, search, qgFilter]);

  // Whenever the filtered set or page size changes, the current page may no longer be valid
  // (e.g. it narrows to fewer pages than we were on), so snap back to page 1.
  useEffect(() => {
    setPage(1);
  }, [search, qgFilter, pageSize]);

  const paginated = useMemo(
    () => filtered.slice((page - 1) * pageSize, page * pageSize),
    [filtered, page, pageSize]
  );

  function trendByRepo(owner: string, repo: string) {
    return impactAnalyses.find((a) => a.owner === owner && a.repo === repo)?.overallTrend;
  }

  const isLoading = repositoriesQuery.isLoading || impactAnalysesQuery.isLoading;
  const isError = repositoriesQuery.isError || impactAnalysesQuery.isError;

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">Repositories</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            All repositories that have been analyzed for quality gate detection.
          </p>
        </div>
        <Button onClick={() => navigate('/analyze')}>Analyze New Repository</Button>
      </div>

      <Card padded={false}>
        <div className="flex flex-col gap-3 border-b border-slate-200 p-4 sm:flex-row sm:items-center dark:border-slate-700">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input
              placeholder="Search by owner/repo…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
              aria-label="Search repositories"
            />
          </div>
          <div className="flex gap-1 rounded-md bg-slate-100 p-1 dark:bg-slate-700/50">
            {(['all', 'with-qg', 'without-qg'] as QGFilter[]).map((f) => (
              <button
                key={f}
                onClick={() => setQgFilter(f)}
                className={[
                  'rounded px-3 py-1.5 text-sm font-medium transition-colors',
                  qgFilter === f
                    ? 'bg-white text-blue-600 shadow-sm dark:bg-slate-800 dark:text-blue-400'
                    : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
                ].join(' ')}
              >
                {f === 'all' ? 'All' : f === 'with-qg' ? 'Has QG' : 'No QG'}
              </button>
            ))}
          </div>
        </div>

        {isLoading && <RepoTableSkeleton />}
        {isError &&
          !isLoading &&
          (() => {
            const { title, message } = classifyError(repositoriesQuery.error ?? impactAnalysesQuery.error);
            return (
              <div className="p-6">
                <ErrorState
                  title={title}
                  message={message}
                  onRetry={() => {
                    repositoriesQuery.refetch();
                    impactAnalysesQuery.refetch();
                  }}
                />
              </div>
            );
          })()}
        {!isLoading && !isError && filtered.length === 0 && (
          <div className="p-6">
            {repositories.length === 0 ? (
              <EmptyState
                title="No repositories analyzed yet"
                message="Analyze your first repository to see detection results here."
                action={
                  <Button onClick={() => navigate('/analyze')}>
                    <PlusCircle className="h-4 w-4" />
                    Analyze your first repository
                  </Button>
                }
              />
            ) : (
              <EmptyState
                title="No results match your search"
                message="Try a different search term or filter."
                action={
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setSearch('');
                      setQgFilter('all');
                    }}
                  >
                    <X className="h-4 w-4" />
                    Clear filters
                  </Button>
                }
              />
            )}
          </div>
        )}
        {!isLoading && !isError && filtered.length > 0 && (
          <>
            <RepoTable repositories={paginated} trendByRepo={trendByRepo} />
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={filtered.length}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
            />
          </>
        )}
      </Card>
    </div>
  );
}
