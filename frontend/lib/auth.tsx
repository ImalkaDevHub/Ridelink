"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { type AccountStatus, type Role, accountApi, ApiError, clearToken, getToken, setToken } from "./api";

// The signed-in user. The JWT lives in the "ridelink.token" cookie (sent to
// every service as a Bearer header and read by proxy.ts for route guards);
// the rest comes from GET /api/auth/me. driverProfileId is the driver-service
// profile id, cached by the driver page.
export interface Session {
  token: string;
  id: string;
  name: string;
  role: Role;
  email?: string;
  status?: AccountStatus;
  driverProfileId?: string;
}

interface AuthContextValue {
  session: Session | null;
  ready: boolean;
  login: (session: Session) => void;
  updateSession: (patch: Partial<Session>) => void;
  // Re-reads the profile from /api/auth/me (e.g. after a profile edit).
  refresh: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const DRIVER_PROFILE_KEY = "ridelink.driverProfileId";

function readDriverProfileId(): string | undefined {
  try {
    return window.localStorage.getItem(DRIVER_PROFILE_KEY) || undefined;
  } catch {
    return undefined;
  }
}

// Turns the stored token into a session via GET /api/auth/me (null if there
// is no usable token).
async function resolveSession(): Promise<Session | null> {
  const token = getToken();
  if (!token) return null;
  try {
    const me = await accountApi.me(token, true);
    return {
      token,
      id: me.id,
      name: me.name,
      role: me.role,
      email: me.email,
      status: me.status,
      driverProfileId: readDriverProfileId(),
    };
  } catch (e) {
    // Expired/invalid token: forget it. Network errors keep the cookie so
    // a brief outage doesn't sign people out.
    if (e instanceof ApiError && (e.status === 401 || e.status === 404)) clearToken();
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    resolveSession().then((next) => {
      if (cancelled) return;
      setSession(next);
      setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const refresh = useCallback(async () => {
    setSession(await resolveSession());
  }, []);

  function persist(next: Session | null) {
    setSession(next);
    if (next) {
      setToken(next.token);
      try {
        if (next.driverProfileId) window.localStorage.setItem(DRIVER_PROFILE_KEY, next.driverProfileId);
      } catch {
        // Storage unavailable - the driver page looks the id up again.
      }
    } else {
      clearToken();
      try {
        window.localStorage.removeItem(DRIVER_PROFILE_KEY);
        window.localStorage.removeItem("ridelink.recentRideIds");
        window.localStorage.removeItem("ridelink.currentRideId");
      } catch {
        // ignore
      }
    }
  }

  const value: AuthContextValue = {
    session,
    ready,
    login: (s) => persist(s),
    updateSession: (patch) => persist(session ? { ...session, ...patch } : session),
    refresh,
    logout: () => {
      persist(null);
      router.replace("/login");
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
