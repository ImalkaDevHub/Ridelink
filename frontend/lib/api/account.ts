import { ACCOUNT_API, request } from "./client";
import type { AccountResponse, AccountStatus, AuthResponse, PagedResponse, Role } from "./types";

// account-service (8081)
export const accountApi = {
  register: (body: { name: string; email: string; password: string; role?: Role }) =>
    request<AuthResponse>(`${ACCOUNT_API}/api/auth/register`, {
      method: "POST",
      body: JSON.stringify(body),
      skipAuthRedirect: true,
    }),

  // A 401 here means wrong credentials, not an expired session.
  login: (body: { email: string; password: string }) =>
    request<AuthResponse>(`${ACCOUNT_API}/api/auth/login`, {
      method: "POST",
      body: JSON.stringify(body),
      skipAuthRedirect: true,
    }),

  // skipAuthRedirect: the auth context checks a stored token on page load and
  // just drops it if it's stale, rather than bouncing public pages to /login.
  me: (token?: string | null, skipAuthRedirect = false) =>
    request<AccountResponse>(`${ACCOUNT_API}/api/auth/me`, { token, skipAuthRedirect }),

  getProfile: (id: string) => request<AccountResponse>(`${ACCOUNT_API}/api/accounts/${id}`),

  updateProfile: (id: string, body: { name: string; email: string }) =>
    request<AccountResponse>(`${ACCOUNT_API}/api/accounts/${id}`, { method: "PATCH", body: JSON.stringify(body) }),

  // Owner only. A wrong current password is a 400 WRONG_PASSWORD, not a 401.
  changePassword: (id: string, body: { currentPassword: string; newPassword: string }) =>
    request<{ message: string }>(`${ACCOUNT_API}/api/accounts/${id}/password`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),

  // ADMIN only.
  list: (page = 0, size = 100) =>
    request<PagedResponse<AccountResponse>>(`${ACCOUNT_API}/api/accounts?page=${page}&size=${size}`),

  setStatus: (id: string, status: AccountStatus) =>
    request<AccountResponse>(`${ACCOUNT_API}/api/accounts/${id}/status?status=${status}`, { method: "PATCH" }),

  setRole: (id: string, role: Role) =>
    request<AccountResponse>(`${ACCOUNT_API}/api/accounts/${id}/role?role=${role}`, { method: "PATCH" }),
};
