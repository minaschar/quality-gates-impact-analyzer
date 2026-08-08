import { Badge, type BadgeColor } from '@/components/common/Badge';
import { ENFORCEMENT_STATUS_DISPLAY_NAMES } from '@/utils/constants';
import type { EnforcementStatus } from '@/types';

const STATUS_COLOR: Record<EnforcementStatus, BadgeColor> = {
  STRICTLY_ENFORCED: 'emerald',
  MOSTLY_ENFORCED: 'emerald',
  PARTIALLY_ENFORCED: 'amber',
  NOT_ENFORCED: 'red',
  QG_ACTIVE_NO_FAILURES: 'blue',
  QG_NOT_RUNNING_ON_PRS: 'amber',
  QG_NOT_REQUIRED: 'amber',
  CONFIGURED_NOT_VERIFIED: 'slate',
  NO_QUALITY_GATE: 'slate',
  INSUFFICIENT_DATA: 'slate',
};

export function EnforcementStatusBadge({ status }: { status?: EnforcementStatus }) {
  if (!status) return <Badge color="slate">—</Badge>;
  return <Badge color={STATUS_COLOR[status]}>{ENFORCEMENT_STATUS_DISPLAY_NAMES[status]}</Badge>;
}
