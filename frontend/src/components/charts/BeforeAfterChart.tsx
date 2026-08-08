import { useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { METRIC_DISPLAY_NAMES } from '@/utils/constants';
import { formatPercent } from '@/utils/formatters';
import type { MetricComparisonDto } from '@/types';

interface BeforeAfterChartProps {
  metrics: Record<string, MetricComparisonDto>;
  /** Only these metric keys are plotted, in this order; defaults to every metric with data. */
  include?: string[];
  height?: number;
}

interface Row {
  metric: string;
  label: string;
  before: number;
  after: number;
  deltaPercent?: number;
}

export function BeforeAfterChart({ metrics, include, height = 340 }: BeforeAfterChartProps) {
  const rows = useMemo<Row[]>(() => {
    const keys = include ?? Object.keys(metrics);
    const result: Row[] = [];
    for (const key of keys) {
      const m = metrics[key];
      if (!m || m.avgBefore == null || m.avgAfter == null) continue;
      result.push({
        metric: key,
        label: METRIC_DISPLAY_NAMES[key] ?? key,
        before: m.avgBefore,
        after: m.avgAfter,
        deltaPercent: m.deltaPercent,
      });
    }
    return result;
  }, [metrics, include]);

  if (rows.length === 0) {
    return <div className="flex h-48 items-center justify-center text-sm text-slate-400">No comparable metrics.</div>;
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={rows} margin={{ top: 24, right: 16, left: 0, bottom: 0 }} barGap={4}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-slate-200 dark:stroke-slate-700" />
        <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#64748B' }} axisLine={{ stroke: '#E2E8F0' }} tickLine={false} />
        <YAxis tick={{ fontSize: 12, fill: '#64748B' }} axisLine={{ stroke: '#E2E8F0' }} tickLine={false} width={40} />
        <Tooltip
          formatter={(value: number) => value.toFixed(2)}
          contentStyle={{
            backgroundColor: 'white',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
          }}
        />
        <Bar dataKey="before" name="Before" fill="#CBD5E1" radius={[4, 4, 0, 0]} />
        <Bar dataKey="after" name="After" fill="#3B82F6" radius={[4, 4, 0, 0]}>
          <LabelList
            dataKey="deltaPercent"
            position="top"
            formatter={(value: number) => formatPercent(value)}
            content={(props) => <DeltaLabel {...props} />}
          />
          {rows.map((row) => (
            <Cell key={row.metric} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

// Custom label so the percentage-change text can be colored by direction (positive/negative),
// which recharts' built-in LabelList formatter can't do since it only controls text content.
// Recharts passes the full LabelProps shape (x/y/width/value as string | number) here, so the
// numeric fields must accept both and get coerced -- a narrower `number`-only type doesn't
// structurally match what LabelList actually hands to a custom `content` renderer.
function DeltaLabel(props: { x?: string | number; y?: string | number; width?: string | number; value?: string | number }) {
  const x = Number(props.x ?? 0);
  const y = Number(props.y ?? 0);
  const width = Number(props.width ?? 0);
  if (props.value === undefined || props.value === null) return null;
  const value = Number(props.value);
  const color = value > 0 ? '#10B981' : value < 0 ? '#EF4444' : '#64748B';
  return (
    <text x={x + width / 2} y={y - 6} textAnchor="middle" fontSize={11} fontWeight={600} fill={color}>
      {formatPercent(value)}
    </text>
  );
}
