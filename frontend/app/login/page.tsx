"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, ErrorBanner } from "@/components/ui";
import { accountApi, driverApi, ApiError, type Role } from "@/lib/api";
import { useAuth } from "@/lib/auth";

type Mode = "login" | "register";

export default function AuthPage() {
  const router = useRouter();
  const { login } = useAuth();

  const [mode, setMode] = useState<Mode>("login");
  const [role, setRole] = useState<Role>("PASSENGER");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Only shown right after a fresh DRIVER registration: account-service and
  // driver-service have no link between them, so a new driver has no
  // driver-service Driver record (needed for availability toggling) until
  // they create one here.
  const [needsDriverProfile, setNeedsDriverProfile] = useState<{ token: string } | null>(null);
  const [vehicleNumber, setVehicleNumber] = useState("");
  const [vehicleType, setVehicleType] = useState("");
  const [serviceArea, setServiceArea] = useState("");

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      if (mode === "login") {
        const auth = await accountApi.login({ email, password });
        login({ token: auth.token, id: auth.id, name: auth.name, role: auth.role });
        router.push(auth.role === "DRIVER" ? "/driver" : "/passenger");
      } else {
        const auth = await accountApi.register({ name, email, password, role });
        login({ token: auth.token, id: auth.id, name: auth.name, role: auth.role });
        if (auth.role === "DRIVER") {
          setNeedsDriverProfile({ token: auth.token });
        } else {
          router.push("/passenger");
        }
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function submitDriverProfile() {
    if (!needsDriverProfile) return;
    setError(null);
    setBusy(true);
    try {
      const driver = await driverApi.createProfile(
        { name, vehicleNumber, vehicleType, serviceArea },
        needsDriverProfile.token
      );
      router.push(`/driver?newDriverId=${driver.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not create your driver profile.");
    } finally {
      setBusy(false);
    }
  }

  if (needsDriverProfile) {
    return (
      <div style={{ minHeight: "100vh" }}>
        <NavBar label="/register" />
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", padding: "60px 20px", minHeight: "calc(100vh - 46px)", backgroundImage: 'url("/image/car.jpeg")', backgroundSize: "cover", backgroundPosition: "center" }}>
          <div style={{ width: "100%", maxWidth: 460, background: "rgba(255, 255, 255, 0.9)", padding: 40, borderRadius: 16, boxShadow: "0 4px 24px rgba(0,0,0,0.1)", backdropFilter: "blur(4px)" }}>
            <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 24, fontWeight: 700, marginBottom: 8 }}>
              Set up your vehicle
            </h2>
            <p style={{ color: "var(--muted)", fontSize: 14, lineHeight: 1.6, marginBottom: 22 }}>
              Your account is ready. Driver Service also needs a driver profile before you can go
              online - vehicle details, and the service area you drive in.
            </p>
            <Field label="Vehicle number" value={vehicleNumber} onChange={(e) => setVehicleNumber(e.target.value)} placeholder="CAB-1024" />
            <Field label="Vehicle type" value={vehicleType} onChange={(e) => setVehicleType(e.target.value)} placeholder="Sedan" />
            <Field label="Service area" value={serviceArea} onChange={(e) => setServiceArea(e.target.value)} placeholder="Malabe" />
            <PrimaryButton onClick={submitDriverProfile} disabled={busy}>
              {busy ? "Saving..." : "Create driver profile"}
            </PrimaryButton>
            <ErrorBanner message={error} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Account Service &middot; 8081" />
      <div className="login-grid" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", minHeight: "calc(100vh - 46px)" }}>
        <div className="login-left-panel" style={{ padding: "56px 40px", flexDirection: "column", justifyContent: "flex-start", borderRight: "1px solid var(--line)" }}>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 12, fontWeight: 600, color: "var(--accent)", marginBottom: 14 }}>
            Account Service &middot; 8081
          </div>
          <h2 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(26px,3.4vw,38px)", fontWeight: 700, letterSpacing: -1, lineHeight: 1.05, margin: "0 0 14px" }}>
            Sign in to the board.
          </h2>
          <p style={{ margin: 0, maxWidth: "38ch", fontSize: 14.5, lineHeight: 1.6, color: "var(--muted)" }}>
            One account, one role. Passengers request rides; drivers receive them. A JWT issued
            here is what every other service validates against.
          </p>
        </div>

        <div className="login-right-panel" style={{ padding: 40, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--card-text)" }}>
          <div className="login-form-card" style={{ width: "100%", maxWidth: 420 }}>
            <div style={{ display: "flex", gap: 2, padding: 3, background: "rgba(31,58,95,.1)", borderRadius: 9, marginBottom: 24 }}>
              <button
                type="button"
                onClick={() => setMode("login")}
                style={tabStyle(mode === "login")}
              >
                Sign in
              </button>
              <button
                type="button"
                onClick={() => setMode("register")}
                style={tabStyle(mode === "register")}
              >
                Create account
              </button>
            </div>

            <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />

            {mode === "register" && (
              <Field label="Full name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Ada Okonjo" />
            )}

            <Field
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 6 characters"
            />

            {mode === "register" && (
              <div style={{ marginBottom: 18 }}>
                <div style={{ fontFamily: "var(--font-heading)", fontSize: 11.5, fontWeight: 600, color: "var(--muted)", marginBottom: 7 }}>
                  I am signing up as
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <RoleButton active={role === "PASSENGER"} title="Passenger" note="I need rides" onClick={() => setRole("PASSENGER")} />
                  <RoleButton active={role === "DRIVER"} title="Driver" note="I have a vehicle" onClick={() => setRole("DRIVER")} />
                </div>
              </div>
            )}

            <PrimaryButton onClick={submit} disabled={busy || !email || !password || (mode === "register" && !name)}>
              {busy ? "Please wait..." : mode === "login" ? "Sign in" : `Create ${role.toLowerCase()} account`}
            </PrimaryButton>

            <ErrorBanner message={error} />

            <p className="desktop-only" style={{ margin: "16px 0 0", fontSize: 12.5, color: "var(--muted)" }}>
              {mode === "login"
                ? "No account yet? Create one - you pick your role then."
                : "Roles cannot be swapped later; you would register a second account."}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function tabStyle(active: boolean): React.CSSProperties {
  return {
    flex: 1,
    fontFamily: "var(--font-heading)",
    fontSize: 13.5,
    fontWeight: 600,
    padding: "10px 12px",
    borderRadius: 7,
    border: "none",
    cursor: "pointer",
    background: active ? "#fff" : "transparent",
    color: active ? "#14161a" : "#6b7280",
    boxShadow: active ? "0 1px 2px rgba(20,22,26,.12)" : "none",
  };
}

function RoleButton({ active, title, note, onClick }: { active: boolean; title: string; note: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        textAlign: "left",
        padding: "13px 14px",
        borderRadius: 9,
        cursor: "pointer",
        fontFamily: "var(--font-heading)",
        background: active ? "rgba(255,176,32,.14)" : "#fff",
        border: active ? "1px solid #ffb020" : "1px solid rgba(31,58,95,.25)",
        color: "#14161a",
      }}
    >
      <span style={{ display: "block", fontSize: 14, fontWeight: 600 }}>{title}</span>
      <span style={{ display: "block", fontSize: 12, fontWeight: 400, color: "#6b7280", marginTop: 3 }}>{note}</span>
    </button>
  );
}
