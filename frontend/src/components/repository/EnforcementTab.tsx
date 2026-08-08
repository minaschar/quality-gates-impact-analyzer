import { Card, CardHeader } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Badge, type BadgeColor } from '@/components/common/Badge';
import { EnforcementStatusBadge } from '@/components/repository/EnforcementStatusBadge';
import { EnforcementChart } from '@/components/charts/EnforcementChart';
import { PR_OUTCOME_DISPLAY_NAMES } from '@/utils/constants';
import { formatDate, formatScore01AsPercent } from '@/utils/formatters';
import type { PROutcome, RepositoryDetectionResult } from '@/types';

interface EnforcementTabProps {
  detection: RepositoryDetectionResult;
}

const OUTCOME_COLOR: Record<PROutcome, BadgeColor> = {
  FIXED_THEN_MERGED: 'emerald',
  BLOCKED: 'emerald',
  MERGED_WITH_FAILURE: 'red',
  STILL_OPEN: 'amber',
  NO_FAILURE: 'slate',
};

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border border-slate-200 p-3 text-center dark:border-slate-700">
      <p className="text-xl font-bold text-slate-800 dark:text-slate-100">{value}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">{label}</p>
    </div>
  );
}

export function EnforcementTab({ detection }: EnforcementTabProps) {
  const enforcement = detection.enforcement;

  if (!enforcement) {
    return (
      <Card>
        <EmptyState title="No enforcement data" message="Enforcement detection has not been run for this repository." />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Enforcement Status" subtitle={enforcement.interpretation} />
          <div className="flex items-center gap-4">
            <EnforcementStatusBadge status={enforcement.status} />
            {enforcement.score != null && (
              <span className="text-2xl font-bold text-slate-800 dark:text-slate-100">
                {formatScore01AsPercent(enforcement.score)}
              </span>
            )}
            <Badge color="slate">{enforcement.confidence} confidence</Badge>
          </div>

          <div className="mt-5 grid grid-cols-3 gap-3 sm:grid-cols-6">
            <StatCard label="PRs Checked" value={enforcement.totalPRsChecked} />
            <StatCard label="With QG Failures" value={enforcement.prsWithQGFailures} />
            <StatCard label="Fixed & Merged" value={enforcement.fixedThenMerged} />
            <StatCard label="Blocked" value={enforcement.blocked} />
            <StatCard label="Merged w/ Failure" value={enforcement.mergedWithFailure} />
            <StatCard label="Still Open" value={enforcement.stillOpen} />
          </div>
        </Card>

        <Card>
          <CardHeader title="PR Outcomes" />
          <EnforcementChart
            fixedThenMerged={enforcement.fixedThenMerged}
            blocked={enforcement.blocked}
            mergedWithFailure={enforcement.mergedWithFailure}
            score={enforcement.score}
          />
          <div className="mt-4 flex flex-wrap justify-center gap-3 text-xs text-slate-500 dark:text-slate-400">
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Blocked
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-blue-500" /> Fixed Then Merged
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-red-500" /> Merged With Failure
            </span>
          </div>
        </Card>
      </div>

      <Card padded={false}>
        <div className="p-6 pb-0">
          <CardHeader title="Sample Pull Requests" subtitle="PRs used as enforcement evidence" />
        </div>
        {enforcement.samplePRs.length === 0 ? (
          <div className="px-6 pb-6">
            <EmptyState title="No sample PRs" message="No pull requests were available to sample for this repository." />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-y border-slate-200 bg-slate-100 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:bg-slate-700/40 dark:text-slate-400">
                  <th className="px-4 py-2.5">PR</th>
                  <th className="px-4 py-2.5">Outcome</th>
                  <th className="px-4 py-2.5">Failed Tools</th>
                  <th className="px-4 py-2.5">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {enforcement.samplePRs.map((pr) => (
                  <tr key={pr.prNumber}>
                    <td className="px-4 py-3">
                      <a href={pr.prUrl} target="_blank" rel="noreferrer" className="text-blue-500 hover:underline">
                        #{pr.prNumber}
                      </a>
                      <p className="max-w-xs truncate text-slate-500 dark:text-slate-400">{pr.prTitle}</p>
                    </td>
                    <td className="px-4 py-3">
                      <Badge color={OUTCOME_COLOR[pr.outcome]}>{PR_OUTCOME_DISPLAY_NAMES[pr.outcome]}</Badge>
                    </td>
                    <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                      {pr.failedQGTools.length ? pr.failedQGTools.join(', ') : '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{formatDate(pr.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
