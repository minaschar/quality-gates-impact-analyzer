import { useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { IMPACT_TREND_DISPLAY_NAMES } from '@/utils/constants';
import type { ImpactAnalysisSummaryDto, ImpactTrend } from '@/types';

const TREND_ORDER: ImpactTrend[] = ['IMPROVED', 'UNCHANGED', 'DEGRADED', 'INSUFFICIENT_DATA'];
const TREND_COLORS: Record<ImpactTrend, string> = {
  IMPROVED: '#10B981',
  DEGRADED: '#EF4444',
  UNCHANGED: '#94A3B8',
  INSUFFICIENT_DATA: '#F59E0B',
};

interface TrendDistributionChartProps {
  analyses: ImpactAnalysisSummaryDto[];
  height?: number;
}

export function TrendDistributionChart({ analyses, height = 200 }: TrendDistributionChartProps) {
  const data = useMemo(
    () =>
      TREND_ORDER.map((trend) => ({
        trend,
        label: IMPACT_TREND_DISPLAY_NAMES[trend],
        count: analyses.filter((a) => a.overallTrend === trend).length,
      })),
    [analyses]
  );

  if (analyses.length === 0) {
    return <div className="flex h-32 items-center justify-center text-sm text-slate-400">No analyses yet.</div>;
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 24, left: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" horizontal={false} className="stroke-slate-200 dark:stroke-slate-700" />
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#64748B' }} axisLine={{ stroke: '#E2E8F0' }} tickLine={false} />
        <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 12, fill: '#64748B' }} axisLine={false} tickLine={false} />
        <Tooltip
          contentStyle={{
            backgroundColor: 'white',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
          }}
        />
        <Bar dataKey="count" radius={[0, 4, 4, 0]} barSize={18}>
          {data.map((d) => (
            <Cell key={d.trend} fill={TREND_COLORS[d.trend]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
