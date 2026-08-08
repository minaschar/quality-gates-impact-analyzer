import { Badge, type BadgeColor } from '@/components/common/Badge';
import { TOOL_DISPLAY_NAMES } from '@/utils/constants';
import type { QualityGateCategory, QualityGateTool } from '@/types';

const CATEGORY_COLOR: Record<QualityGateCategory, BadgeColor> = {
  CODE_QUALITY: 'blue',
  CODE_STYLE: 'slate',
  COVERAGE: 'emerald',
  SECURITY: 'amber',
  LICENSE: 'slate',
};

interface QGToolBadgeProps {
  tool: QualityGateTool;
  category?: QualityGateCategory;
}

export function QGToolBadge({ tool, category }: QGToolBadgeProps) {
  return (
    <Badge color={category ? CATEGORY_COLOR[category] : 'blue'}>{TOOL_DISPLAY_NAMES[tool] ?? tool}</Badge>
  );
}
