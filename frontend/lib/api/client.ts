// Shared fetch wrapper for the four RideLink services: attaches the JWT from
// the session cookie as a Bearer header, turns {"code","message","details"}
// error bodies into ApiError (with calm wording), and sends the user back to
// /login when a token is rejected (401).

import { friendlyMessage, NETWORK_ERROR_MESSAGE } from "./errors";

export const ACCOUNT_API = process.env.NEXT_PUBLIC_ACCOUNT_API_URL ?? "http://localhost:8081";
export const DRIVER_API = process.env.NEXT_PUBLIC_DRIVER_API_URL ?? "http://localhost:8082";
export const RIDE_API = process.env.NEXT_PUBLIC_RIDE_API_URL ?? "http://localhost:8083";
export const FARE_API = process.env.NEXT_PUBLIC_FARE_API_URL ?? "http://localhost:8084";

// ---- Token cookie -----------------------------------------------------------
// Readable by client code (it has to send the token to other origins as a
// Bearer header) and by proxy.ts (for role-based route guards).

export const TOKEN_COOKIE = "ridelink.token";

export function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.split("; ").find((part) => part.startsWith(`${TOKEN_COOKIE}=`));
  return match ? decodeURIComponent(match.slice(TOKEN_COOKIE.length + 1)) || null : null;
}

// Cookie lifetime follows the token's own expiry.
export function setToken(token: string): void {
  const exp = decodeTokenPayload(token)?.exp;
  const maxAge = exp ? Math.max(0, Math.floor(exp - Date.now() / 1000)) : 3600;
  const secure = typeof location !== "undefined" && location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${TOKEN_COOKIE}=${encodeURIComponent(token)}; Path=/; Max-Age=${maxAge}; SameSite=Lax${secure}`;
}

export function clearToken(): void {
  if (typeof document === "undefined") return;
  document.cookie = `${TOKEN_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

export interface TokenPayload {
  sub?: string;
  accountId?: string;
  email?: string;
  role?: string;
  exp?: number;
}

// JWT payloads are base64url, not plain base64.
export function decodeTokenPayload(token: string): TokenPayload | null {
  try {
    const part = token.split(".")[1];
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    return JSON.parse(atob(base64)) as TokenPayload;
  } catch {
    return null;
  }
}

// ---- Errors -----------------------------------------------------------------

export class ApiError extends Error {
  status: number;
  code?: string;
  // Per-field messages from a VALIDATION_ERROR, keyed by field name.
  details?: Record<string, string>;
  serverMessage?: string;

  constructor(status: number, message: string, code?: string, details?: Record<string, string>, serverMessage?: string) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
    this.serverMessage = serverMessage;
  }
}

// Message for a caught error, with a fallback for non-API failures.
export function errorMessage(e: unknown, fallback = "Something went wrong. Please try again."): string {
  return e instanceof ApiError ? e.message : fallback;
}

// ---- Request ----------------------------------------------------------------

interface RequestOptions extends RequestInit {
  // Explicit token (otherwise the cookie's token is used).
  token?: string | null;
  // Don't treat 401 as an expired session (e.g. wrong password at login).
  skipAuthRedirect?: boolean;
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { token, skipAuthRedirect, ...init } = options;
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (init.headers) Object.assign(headers, init.headers as Record<string, string>);

  const bearer = token ?? getToken();
  if (bearer) headers["Authorization"] = `Bearer ${bearer}`;

  let res: Response;
  try {
    res = await fetch(url, { ...init, headers });
  } catch {
    throw new ApiError(0, NETWORK_ERROR_MESSAGE);
  }

  const text = await res.text();
  const body = text ? safeJson(text) : null;

  if (!res.ok) {
    const obj = body && typeof body === "object" ? (body as Record<string, unknown>) : {};
    const code = typeof obj.code === "string" ? obj.code : undefined;
    const serverMessage = typeof obj.message === "string" ? obj.message : `Request failed (${res.status})`;
    const details = obj.details && typeof obj.details === "object"
      ? Object.fromEntries(Object.entries(obj.details as Record<string, unknown>).map(([k, v]) => [k, String(v)]))
      : undefined;

    if (res.status === 401 && !skipAuthRedirect) {
      redirectToLogin();
    }

    throw new ApiError(res.status, friendlyMessage(code, serverMessage), code, details, serverMessage);
  }

  return body as T;
}

// The session is gone or invalid: drop the cookie and send the user to sign
// in, coming back to where they were afterwards.
function redirectToLogin(): void {
  if (typeof window === "undefined") return;
  clearToken();
  if (window.location.pathname.startsWith("/login")) return;
  const next = window.location.pathname + window.location.search;
  window.location.replace(new URL(`/login?next=${encodeURIComponent(next)}`, window.location.origin).toString());
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
