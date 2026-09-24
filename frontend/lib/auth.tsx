"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import type { Role } from "./api";

// What we keep client-side after login/register. driverProfileId is not
// part of account-service's response - there is no link in the backend
// between an account and a driver-service Driver record, so a driver has
// to tell us their Driver id once (see app/driver/page.tsx) and we persist
// it here alongside the session.
export interface Session {
  token: string;
  id: string;
  name: string;
  role: Role;
  driverProfileId?: string;
}

interface AuthContextValue {
  session: Session | null;
  ready: boolean;
  login: (session: Session) => void;
  updateSession: (patch: Partial<Session>) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const STORAGE_KEY = "ridelink.session";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Deliberately deferred to an effect (not a lazy useState initializer):
    // the server has no localStorage, so the first client render must also
    // start signed-out to match the server-rendered HTML, or React flags a
    // hydration mismatch. Reading it here, after mount, is what "ready"
    // exists for below.
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (raw) setSession(JSON.parse(raw) as Session);
    } catch {
      // Private browsing / storage disabled - just start signed out.
    }
    setReady(true);
  }, []);

  function persist(next: Session | null) {
    setSession(next);
    try {
      if (next) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      else window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Ignore - session still works for this tab via React state.
    }
  }

  const value: AuthContextValue = {
    session,
    ready,
    login: (s) => persist(s),
    updateSession: (patch) => persist(session ? { ...session, ...patch } : session),
    logout: () => {
      persist(null);
      try {
        window.localStorage.removeItem("ridelink.recentRideIds");
        window.localStorage.removeItem("ridelink.currentRideId");
      } catch {
        // ignore
      }
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
