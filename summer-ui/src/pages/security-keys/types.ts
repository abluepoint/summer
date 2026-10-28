export type SecurityKey = {
  kid: string;
  algorithm: string;
  source: string;
  status: string;
  version: number;
  health: string;
  graceUntil?: string;
};

export type SecurityKeyRotation = {
  activeKid: string;
  version: number;
  retentionDays: number;
};

export type SecurityKeyRotationSchedule = {
  status: string;
  periodDays: number;
  retentionDays: number;
  nextRotationAt: string;
  lastAttemptAt?: string;
  lastSuccessAt?: string;
  failureCount: number;
  lastError?: string;
};

export type SecurityKeyState = {
  items: SecurityKey[];
  rotation: SecurityKeyRotationSchedule;
};
