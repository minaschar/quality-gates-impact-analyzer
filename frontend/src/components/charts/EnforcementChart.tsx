import { useMemo } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

interface EnforcementChartProps {
  fixedThenMerged: number;
  blocked: number;
  mergedWithFailure: number;
  /** 0-1 enforcement score, shown as a percentage in the donut's center. */
  score?: number | null;
  size?: number;
}

const SEGMENT_COLORS = {
  blocked: '#10B981',
  fixedThenMerged: '#3B82F6',
  mergedWithFailure: '#EF4444',
};

export function EnforcementChart({ fixedThenMerged, blocked, mergedWithFailure, score, size = 220 }: EnforcementChartProps) {
  const data = useMemo(
    () =>
      [
        { key: 'blocked', name: 'Blocked', value: blocked, color: SEGMENT_COLORS.blocked },
        { key: 'fixedThenMerged', name: 'Fixed Then Merged', value: fixedThenMerged, color: SEGMENT_COLORS.fixedThenMerged },
        { key: 'mergedWithFailure', name: 'Merged With Failure', value: mergedWithFailure, color: SEGMENT_COLORS.mergedWithFailure },
      ].filter((d) => d.value > 0),
    [blocked, fixedThenMerged, mergedWithFailure]
  );

  const total = blocked + fixedThenMerged + mergedWithFailure;

  if (total === 0) {
    return <div className="flex h-48 items-center justify-center text-sm text-slate-400">No enforcement evidence yet.</div>;
  }

  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="65%"
            outerRadius="95%"
            paddingAngle={2}
            strokeWidth={0}
          >
            {data.map((entry) => (
              <Cell key={entry.key} fill={entry.color} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value: number, name: string) => [`${value} PR${value === 1 ? '' : 's'}`, name]}
            contentStyle={{
              backgroundColor: 'white',
              border: '1px solid #E2E8F0',
              borderRadius: 8,
              boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
            }}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-bold text-slate-800 dark:text-slate-100">
          {score != null ? `${Math.round(score * 100)}%` : '—'}
        </span>
        <span className="text-xs text-slate-500 dark:text-slate-400">enforced</span>
      </div>
    </div>
  );
}
