import { TrendingDown, TrendingUp, Minus, HelpCircle } from 'lucide-react';
import { Badge, type BadgeColor } from '@/components/common/Badge';
import { IMPACT_TREND_DISPLAY_NAMES } from '@/utils/constants';
import type { ImpactTrend } from '@/types';

const TREND_STYLE: Record<ImpactTrend, { color: BadgeColor; icon: React.ReactNode }> = {
  IMPROVED: { color: 'emerald', icon: <TrendingUp className="h-3 w-3" aria-hidden="true" /> },
  DEGRADED: { color: 'red', icon: <TrendingDown className="h-3 w-3" aria-hidden="true" /> },
  UNCHANGED: { color: 'slate', icon: <Minus className="h-3 w-3" aria-hidden="true" /> },
  INSUFFICIENT_DATA: { color: 'amber', icon: <HelpCircle className="h-3 w-3" aria-hidden="true" /> },
};

export function TrendBadge({ trend }: { trend?: ImpactTrend }) {
  if (!trend) return <Badge color="slate">—</Badge>;
  const style = TREND_STYLE[trend];
  return (
    <Badge color={style.color} icon={style.icon}>
      {IMPACT_TREND_DISPLAY_NAMES[trend]}
    </Badge>
  );
}
