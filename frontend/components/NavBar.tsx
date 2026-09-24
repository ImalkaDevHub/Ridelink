"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export default function NavBar({ label }: { label?: string }) {
  const { session, logout, ready } = useAuth();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);

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
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%", maxWidth: "100%", gap: 14 }}>
        <Link href="/" style={{ display: "flex", alignItems: "center", gap: 9, color: "var(--text)" }}>
          <div style={{ width: 11, height: 11, background: "var(--accent)", borderRadius: 2 }} />
          <span style={{ fontFamily: "var(--font-heading)", fontWeight: 700, fontSize: 15, letterSpacing: -0.2 }}>
            RideLink
          </span>
        </Link>
        
        <div className="mobile-only">
          <button 
            onClick={() => setMenuOpen(!menuOpen)}
            style={{ background: "none", border: "none", fontSize: 24, cursor: "pointer", padding: "0 8px" }}
          >
            ☰
          </button>
        </div>

        <div className="desktop-only" style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center", flex: 1 }}>
          {ready && !session && <NavLink href="/login">Sign in</NavLink>}
          {ready && session?.role === "PASSENGER" && <NavLink href="/passenger">Passenger</NavLink>}
          {ready && session?.role === "DRIVER" && <NavLink href="/driver">Driver</NavLink>}
          
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
      </div>

      {/* Mobile Menu Dropdown */}
      {menuOpen && (
        <div className="mobile-only" style={{ display: "flex", flexDirection: "column", gap: 8, width: "100%", marginTop: 12, borderTop: "1px solid var(--line)", paddingTop: 12 }}>
          {ready && !session && <NavLink href="/login">Sign in</NavLink>}
          {ready && session?.role === "PASSENGER" && <NavLink href="/passenger">Passenger</NavLink>}
          {ready && session?.role === "DRIVER" && <NavLink href="/driver">Driver</NavLink>}
          <span style={{ fontSize: 12, color: "var(--muted)" }}>{label}</span>
          {ready && session && (
            <button
              type="button"
              onClick={handleLogout}
              style={{
                fontFamily: "var(--font-heading)",
                fontSize: 13,
                fontWeight: 600,
                padding: "8px 12px",
                borderRadius: 6,
                border: "1px solid var(--line)",
                background: "transparent",
                color: "var(--text)",
                cursor: "pointer",
                alignSelf: "flex-start",
                marginTop: 8
              }}
            >
              Sign out
            </button>
          )}
        </div>
      )}
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
