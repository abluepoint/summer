export type TenantMember = { memberId: string; displayName: string; email: string; status: string };
export type MemberDetail = TenantMember & { enterpriseId: string; revision: string };
export type MemberPage = { items: TenantMember[]; page: number; size: number; total: number; revision: string };
