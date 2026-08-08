import { useNavigate } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { QGToolBadge } from '@/components/repository/QGToolBadge';
import { EnforcementStatusBadge } from '@/components/repository/EnforcementStatusBadge';
import { TrendBadge } from '@/components/repository/TrendBadge';
import { formatRelativeTime } from '@/utils/formatters';
import type { ImpactTrend, RepositoryDetectionResult } from '@/types';

interface RepoTableProps {
  repositories: RepositoryDetectionResult[];
  trendByRepo: (owner: string, repo: string) => ImpactTrend | undefined;
}

export function RepoTable({ repositories, trendByRepo }: RepoTableProps) {
  const navigate = useNavigate();

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
