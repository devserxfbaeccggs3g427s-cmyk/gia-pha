import type { Gender } from '@/data/types';

export const AGE_BUCKETS = ['0-17', '18-30', '31-45', '46-60', '61+', 'UNKNOWN'] as const;

export type AgeBucket = (typeof AGE_BUCKETS)[number];
export type GenderDistribution = Record<Gender, number>;
export type AgeDistribution = Record<AgeBucket, number>;

/** Chart-ready aggregate values for a whole tree or one descendant branch. */
export interface ReportStatistics {
  treeId: string;
  generatedAt: string;
  totalMembers: number;
  generationsCount: number;
  livingMembers: number;
  deceasedMembers: number;
  membersWithKnownAge: number;
  averageAge: number | null;
  genderDistribution: GenderDistribution;
  ageDistribution: AgeDistribution;
  geographicDistribution: Record<string, number>;
  occupationDistribution: Record<string, number>;
  educationDistribution: Record<string, number>;
}

export interface BranchStatistics extends ReportStatistics {
  branchRoot: {
    id: string;
    fullName: string;
  };
  /** Stable member IDs make it possible for clients to highlight the branch. */
  memberIds: string[];
}

export interface GrowthTimelinePoint {
  /** Calendar month in YYYY-MM format. */
  period: string;
  newMembers: number;
  totalMembers: number;
}

