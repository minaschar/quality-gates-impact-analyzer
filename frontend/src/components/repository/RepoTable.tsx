import { useNavigate } from 'react-router-dom';
import { ArrowRight, ArrowUpDown, ChevronDown, ChevronUp } from 'lucide-react';
import { QGToolBadge } from '@/components/repository/QGToolBadge';
import { EnforcementStatusBadge } from '@/components/repository/EnforcementStatusBadge';
import { TrendBadge } from '@/components/repository/TrendBadge';
import { formatRelativeTime } from '@/utils/formatters';
import type { RepoSortColumn, SortDirection } from '@/utils/repoSort';
import type { ImpactTrend, RepositoryDetectionResult } from '@/types';

interface RepoTableProps {
  repositories: RepositoryDetectionResult[];
  trendByRepo: (owner: string, repo: string) => ImpactTrend | undefined;
  sortColumn: RepoSortColumn;
  sortDirection: SortDirection;
  onSort: (column: RepoSortColumn) => void;
}

const COLUMNS: { key: RepoSortColumn; label: string }[] = [
  { key: 'repository', label: 'Repository' },
  { key: 'qgTools', label: 'QG Tools' },
  { key: 'enforcement', label: 'Enforcement' },
  { key: 'trend', label: 'Quality Impact' },
  { key: 'detectedAt', label: 'Last Analyzed' },
];

export function RepoTable({ repositories, trendByRepo, sortColumn, sortDirection, onSort }: RepoTableProps) {
  const navigate = useNavigate();

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-100 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:bg-slate-700/40 dark:text-slate-400">
            {COLUMNS.map((col) => {
              const isActive = sortColumn === col.key;
              return (
                <th key={col.key} className="px-4 py-3">
                  <button
                    type="button"
                    onClick={() => onSort(col.key)}
                    className="flex items-center gap-1 uppercase tracking-wide text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                    aria-label={`Sort by ${col.label}`}
                  >
                    {col.label}
                    {isActive ? (
                      sortDirection === 'asc' ? (
                        <ChevronUp className="h-3.5 w-3.5 text-blue-500" aria-hidden="true" />
                      ) : (
                        <ChevronDown className="h-3.5 w-3.5 text-blue-500" aria-hidden="true" />
                      )
                    ) : (
                      <ArrowUpDown className="h-3.5 w-3.5 text-slate-300 dark:text-slate-600" aria-hidden="true" />
                    )}
                  </button>
                </th>
              );
            })}
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
          {repositories.map((r) => {
            const tools = [...new Set(r.thesisRelevantDetections.map((d) => d.tool))];
            return (
              <tr
                key={`${r.owner}/${r.repo}`}
                onClick={() => navigate(`/repository/${r.owner}/${r.repo}`)}
                className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/40"
              >
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-800 dark:text-slate-100">
                    {r.owner}/{r.repo}
                  </p>
                  {r.primaryLanguage && <p className="text-xs text-slate-400">{r.primaryLanguage}</p>}
                </td>
                <td className="px-4 py-3">
                  {tools.length === 0 ? (
                    <span className="text-slate-400">None</span>
                  ) : (
                    <div className="flex flex-wrap gap-1">
                      {tools.slice(0, 3).map((tool) => (
                        <QGToolBadge key={tool} tool={tool} />
                      ))}
                      {tools.length > 3 && <span className="text-xs text-slate-400">+{tools.length - 3}</span>}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3">
                  <EnforcementStatusBadge status={r.enforcement?.status} />
                </td>
                <td className="px-4 py-3">
                  <TrendBadge trend={trendByRepo(r.owner, r.repo)} />
                </td>
                <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{formatRelativeTime(r.detectedAt)}</td>
                <td className="px-4 py-3 text-right">
                  <ArrowRight className="ml-auto h-4 w-4 text-slate-300" />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
