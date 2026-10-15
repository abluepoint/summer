import type { User } from 'oidc-client-ts';

function record(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function authenticatedClaims(user: User): Record<string, unknown> {
  const profile: Record<string, unknown> = user.profile || {};
  try {
    const payload = user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const decoded = decodeURIComponent(Array.from(atob(payload))
      .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''));
    const claims: unknown = JSON.parse(decoded);
    return record(claims) ? { ...profile, ...claims } : {};
  } catch {
    // Opaque access tokens can use authenticated ID-token profile metadata.
    return profile;
  }
}

/** Display hint from authenticated claims; every API request remains server-authorized. */
export function hasEnterpriseCapability(user: User | null, enterprise: string, code: string): boolean {
  if (!user || user.expired || !enterprise || !code) return false;
  const claims = authenticatedClaims(user);
  const memberships = claims.enterprise_memberships;
  if (!Array.isArray(memberships) || !memberships.some((membership) => record(membership)
    && (membership.enterpriseId || membership.enterprise_id) === enterprise
    && String(membership.status || membership.membership_status).toUpperCase() === 'ACTIVE')) return false;
  const grants = claims.enterprise_capabilities;
  const capabilities = record(grants) ? grants[enterprise] : undefined;
  return Array.isArray(capabilities) && capabilities.includes(code);
}
