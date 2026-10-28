export type AuthUser = {
  username: string;
  role: 'ADMIN' | 'USER';
  active: boolean;
};

export type UserFailure = { status: number; requestId: string };

export type CreateUser = {
  username: string;
  password: string;
  role: AuthUser['role'];
};
