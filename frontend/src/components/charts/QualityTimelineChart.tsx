import { useMemo } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { METRIC_DISPLAY_NAMES } from '@/utils/constants';
import { formatDate } from '@/utils/formatters';
import type { TimelinePointDto } from '@/types';

const LINE_COLORS = ['#3B82F6', '#60A5FA', '#93C5FD'];

interface QualityTimelineChartProps {
  data: TimelinePointDto[];
  introducedAt?: string;
  metrics: string[];
  height?: number;
}

interface ChartPoint {
  date: number;
  [metric: string]: number | undefined;
}

export function QualityTimelineChart({ data, introducedAt, metrics, height = 320 }: QualityTimelineChartProps) {
  const { points, introducedAtMs, minDate, maxDate } = useMemo(() => {
    const sorted = [...data]
      .filter((d) => d.commitDate)
      .sort((a, b) => new Date(a.commitDate).getTime() - new Date(b.commitDate).getTime());

    const mapped: ChartPoint[] = sorted.map((d) => {
      const point: ChartPoint = { date: new Date(d.commitDate).getTime() };
      metrics.forEach((metric) => {
        const value = (d as unknown as Record<string, number | undefined>)[metric];
        if (typeof value === 'number') point[metric] = value;
      });
      return point;
    });

    const dates = mapped.map((p) => p.date);
    return {
      points: mapped,
      introducedAtMs: introducedAt ? new Date(introducedAt).getTime() : undefined,
      minDate: dates.length ? Math.min(...dates) : undefined,
      maxDate: dates.length ? Math.max(...dates) : undefined,
    };
  }, [data, introducedAt, metrics]);

  if (points.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center text-sm text-slate-400">
        No timeline data available for this selection.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={points} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" className="stroke-slate-200 dark:stroke-slate-700" />

        {introducedAtMs !== undefined && minDate !== undefined && (
          <ReferenceArea x1={minDate} x2={introducedAtMs} fill="#F1F5F9" fillOpacity={0.6} ifOverflow="extendDomain" />
        )}
        {introducedAtMs !== undefined && maxDate !== undefined && (
          <ReferenceArea x1={introducedAtMs} x2={maxDate} fill="#EFF6FF" fillOpacity={0.6} ifOverflow="extendDomain" />
        )}

        <XAxis
          dataKey="date"
          type="number"
          domain={['dataMin', 'dataMax']}
          tickFormatter={(value: number) => formatDate(new Date(value).toISOString())}
          tick={{ fontSize: 12, fill: '#64748B' }}
          axisLine={{ stroke: '#E2E8F0' }}
          tickLine={false}
        />
        <YAxis tick={{ fontSize: 12, fill: '#64748B' }} axisLine={{ stroke: '#E2E8F0' }} tickLine={false} width={40} />

        <Tooltip
          labelFormatter={(value: number) => formatDate(new Date(value).toISOString())}
          formatter={(value: number, name: string) => [value, METRIC_DISPLAY_NAMES[name] ?? name]}
          contentStyle={{
            backgroundColor: 'white',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
          }}
        />
        <Legend formatter={(name: string) => METRIC_DISPLAY_NAMES[name] ?? name} wrapperStyle={{ fontSize: 12 }} />

        {introducedAtMs !== undefined && (
          <ReferenceLine
            x={introducedAtMs}
            stroke="#94A3B8"
            strokeDasharray="5 4"
            label={{ value: 'QG introduced', position: 'top', fontSize: 11, fill: '#64748B' }}
          />
        )}

        {metrics.map((metric, i) => (
          <Line
            key={metric}
            type="monotone"
            dataKey={metric}
            stroke={LINE_COLORS[i % LINE_COLORS.length]}
            strokeWidth={2}
            dot={{ r: 2.5 }}
            activeDot={{ r: 5 }}
            connectNulls
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
