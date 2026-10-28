export type SessionItem = {
  sessionRef: string;
  principal: string;
  clientId: string;
  status: 'ACTIVE' | 'REVOKED';
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  current: boolean;
};

export type SessionPage = {
  availability: 'AVAILABLE' | 'UNAVAILABLE';
  items: SessionItem[];
};
