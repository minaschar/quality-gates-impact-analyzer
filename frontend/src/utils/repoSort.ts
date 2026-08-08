import type { EnforcementStatus, ImpactTrend, RepositoryDetectionResult } from '@/types';

export type RepoSortColumn = 'repository' | 'qgTools' | 'enforcement' | 'trend' | 'detectedAt';
export type SortDirection = 'asc' | 'desc';

// Best-to-worst rank so ascending order reads as "healthiest first". Statuses/trends not
// listed (there are none currently) or missing data always sort last, regardless of direction.
const ENFORCEMENT_RANK: Record<EnforcementStatus, number> = {
  STRICTLY_ENFORCED: 0,
  MOSTLY_ENFORCED: 1,
  QG_ACTIVE_NO_FAILURES: 2,
  PARTIALLY_ENFORCED: 3,
  QG_NOT_REQUIRED: 4,
  CONFIGURED_NOT_VERIFIED: 5,
  QG_NOT_RUNNING_ON_PRS: 6,
  NOT_ENFORCED: 7,
  NO_QUALITY_GATE: 8,
  INSUFFICIENT_DATA: 9,
};

const TREND_RANK: Record<ImpactTrend, number> = {
  IMPROVED: 0,
  UNCHANGED: 1,
  INSUFFICIENT_DATA: 2,
  DEGRADED: 3,
};

const MISSING_RANK = 99;

export function compareRepositories(
  a: RepositoryDetectionResult,
  b: RepositoryDetectionResult,
  column: RepoSortColumn,
  direction: SortDirection,
  trendByRepo: (owner: string, repo: string) => ImpactTrend | undefined
): number {
  let result: number;

  switch (column) {
    case 'repository':
      result = `${a.owner}/${a.repo}`.localeCompare(`${b.owner}/${b.repo}`);
      break;
    case 'qgTools': {
      const aCount = new Set(a.thesisRelevantDetections.map((d) => d.tool)).size;
      const bCount = new Set(b.thesisRelevantDetections.map((d) => d.tool)).size;
      result = aCount - bCount;
      break;
    }
    case 'enforcement': {
      const aRank = a.enforcement?.status ? ENFORCEMENT_RANK[a.enforcement.status] : MISSING_RANK;
      const bRank = b.enforcement?.status ? ENFORCEMENT_RANK[b.enforcement.status] : MISSING_RANK;
      result = aRank - bRank;
      break;
    }
    case 'trend': {
      const aTrend = trendByRepo(a.owner, a.repo);
      const bTrend = trendByRepo(b.owner, b.repo);
      const aRank = aTrend ? TREND_RANK[aTrend] : MISSING_RANK;
      const bRank = bTrend ? TREND_RANK[bTrend] : MISSING_RANK;
      result = aRank - bRank;
      break;
    }
    case 'detectedAt': {
      const aTime = a.detectedAt ? new Date(a.detectedAt).getTime() : 0;
      const bTime = b.detectedAt ? new Date(b.detectedAt).getTime() : 0;
      result = aTime - bTime;
      break;
    }
  }

  return direction === 'asc' ? result : -result;
}
