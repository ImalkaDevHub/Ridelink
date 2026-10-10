import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// Role-based route guards (Next.js 16 renamed middleware.ts to proxy.ts).
// This only decides where to send the browser - it does not verify the JWT
// signature. Every backend service verifies the token on every call, so a
// forged cookie gets past this redirect and then gets nothing but 401s.

const TOKEN_COOKIE = "ridelink.token";

// Path prefix -> roles allowed (empty = any signed-in user).
const GUARDS: Array<{ prefix: string; roles: string[] }> = [
  { prefix: "/passenger", roles: ["PASSENGER"] },
  { prefix: "/driver", roles: ["DRIVER"] },
  { prefix: "/admin", roles: ["ADMIN"] },
  { prefix: "/rides", roles: [] },
  { prefix: "/profile", roles: [] },
];

const HOME_BY_ROLE: Record<string, string> = {
  PASSENGER: "/passenger",
  DRIVER: "/driver",
  ADMIN: "/admin/dashboard",
};

interface TokenPayload {
  role?: string;
  exp?: number;
}

// JWT payloads are base64url; the proxy runs on Node.js, so Buffer is available.
function readToken(token: string | undefined): TokenPayload | null {
  if (!token) return null;
  try {
    const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8")) as TokenPayload;
    if (payload.exp && payload.exp * 1000 <= Date.now()) return null; // expired
    return payload;
  } catch {
    return null;
  }
}

function toLogin(request: NextRequest): NextResponse {
  const url = new URL("/login", request.url);
  url.searchParams.set("next", request.nextUrl.pathname + request.nextUrl.search);
  const response = NextResponse.redirect(url);
  // Drop an expired or unreadable token so the login page starts clean.
  response.cookies.delete(TOKEN_COOKIE);
  return response;
}

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const token = request.cookies.get(TOKEN_COOKIE)?.value;
  const payload = readToken(token);

  // Already signed in: skip the login page.
  if (path === "/login" && payload?.role && HOME_BY_ROLE[payload.role]) {
    return NextResponse.redirect(new URL(HOME_BY_ROLE[payload.role], request.url));
  }

  const guard = GUARDS.find((g) => path === g.prefix || path.startsWith(`${g.prefix}/`));
  if (!guard) return NextResponse.next();

  if (!payload) return toLogin(request);

  if (guard.roles.length > 0 && !guard.roles.includes(payload.role ?? "")) {
    // Signed in with the wrong role: send them to their own dashboard.
    return NextResponse.redirect(new URL(HOME_BY_ROLE[payload.role ?? ""] ?? "/", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|image/|.*\\.(?:svg|png|jpg|jpeg|webp|ico)$).*)"],
};
