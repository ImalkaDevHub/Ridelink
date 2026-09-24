"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export default function NavBar({ label }: { label?: string }) {
  const { session, logout, ready } = useAuth();
  const router = useRouter();

  function handleLogout() {
    logout();
    router.push("/");
  }

  return (
    <div
      style={{
        position: "sticky",
        top: 0,
        zIndex: 50,
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: 14,
        padding: "10px 20px",
        background: "rgba(255, 255, 255, 0.92)",
        backdropFilter: "blur(8px)",
        borderBottom: "1px solid var(--line)",
      }}
    >
      <Link href="/" style={{ display: "flex", alignItems: "center", gap: 9, marginRight: 6, color: "var(--text)" }}>
        <div style={{ width: 11, height: 11, background: "var(--accent)", borderRadius: 2 }} />
        <span style={{ fontFamily: "var(--font-heading)", fontWeight: 700, fontSize: 15, letterSpacing: -0.2 }}>
          RideLink
        </span>
      </Link>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
        <NavLink href="/">Landing</NavLink>
        {ready && !session && <NavLink href="/login">Sign in / Register</NavLink>}
        {ready && session?.role === "PASSENGER" && <NavLink href="/passenger">Passenger</NavLink>}
        {ready && session?.role === "DRIVER" && <NavLink href="/driver">Driver</NavLink>}
      </div>

      <span style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 14, fontSize: 12, color: "var(--muted)" }}>
        {label}
        {ready && session && (
          <>
            <span>
              {session.name} &middot; {session.role}
            </span>
            <button
              type="button"
              onClick={handleLogout}
              style={{
                fontFamily: "var(--font-heading)",
                fontSize: 12,
                fontWeight: 500,
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid var(--line)",
                background: "transparent",
                color: "var(--text)",
                cursor: "pointer",
              }}
            >
              Sign out
            </button>
          </>
        )}
      </span>
    </div>
  );
}

function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      style={{
        fontFamily: "var(--font-heading)",
        fontSize: 12,
        fontWeight: 500,
        padding: "7px 12px",
        borderRadius: 6,
        border: "1px solid var(--line)",
        background: "transparent",
        color: "var(--text)",
      }}
    >
      {children}
    </Link>
  );
}
