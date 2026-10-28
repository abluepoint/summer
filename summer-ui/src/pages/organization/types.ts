import type { ApiFailure } from '@/api/types';

export type OrganizationUnit = { id: string; enterpriseId: string; parentId: string | null; unitType: 'COMPANY' | 'DEPARTMENT' | 'TEAM'; name: string; code: string; leaderMemberId?: string; description?: string; sortOrder: number; status: 'ACTIVE' | 'DISABLED'; memberCount: number; children: OrganizationUnit[] };
export type OrganizationTree = { enterpriseId: string; revision: string; items: OrganizationUnit[] };
export type OrganizationMember = { memberId: string; displayName: string; email: string; status: string; primary: boolean };
export type OrganizationApiError = Pick<ApiFailure, 'status' | 'code' | 'message' | 'requestId'>;
export type UnitDraft = Pick<OrganizationUnit, 'parentId' | 'unitType' | 'name' | 'code' | 'leaderMemberId' | 'description' | 'sortOrder' | 'status'>;
export type AssignmentDraft = { memberIds: string[]; organizationIds: string[]; primaryOrganizationId: string | null };

export type TenantMember = { memberId: string; displayName: string; email: string; status: string };
export type MemberDetail = TenantMember & { enterpriseId: string; revision: string };
export type MemberPage = { items: TenantMember[]; page: number; size: number; total: number; revision: string };
