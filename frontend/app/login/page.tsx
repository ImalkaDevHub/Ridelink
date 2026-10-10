"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NavBar from "@/components/NavBar";
import { FieldWithError, SelectField, PrimaryButton, ErrorBanner } from "@/components/ui";
import { accountApi, driverApi, ApiError, type Role } from "@/lib/api";
import { PASSWORD_HINT, validateEmail, validateName, validatePassword } from "@/lib/validation";
import { useAuth } from "@/lib/auth";

type Mode = "login" | "register";

type FieldErrors = Partial<Record<"name" | "email" | "password" | "vehicleNumber" | "vehicleType" | "serviceArea", string>>;

// Same rules as account-service's RegisterRequest, so most mistakes are
// caught before a round trip.
function validateRegister(name: string, email: string, password: string): FieldErrors {
  const errs: FieldErrors = {};
  const nameError = validateName(name);
  const emailError = validateEmail(email);
  const passwordError = validatePassword(password);
  if (nameError) errs.name = nameError;
  if (emailError) errs.email = emailError;
  if (passwordError) errs.password = passwordError;
  return errs;
}

// Login doesn't apply the password rule - older accounts may have shorter passwords.
function validateLogin(email: string, password: string): FieldErrors {
  const errs: FieldErrors = {};
  const emailError = validateEmail(email);
  if (emailError) errs.email = emailError;
  if (!password) errs.password = "Password is required.";
  return errs;
}

function validateDriverProfile(vehicleNumber: string, serviceArea: string): FieldErrors {
  const errs: FieldErrors = {};
  const number = vehicleNumber.trim();
  if (!number) errs.vehicleNumber = "Vehicle number is required.";
  else if (number.length < 2 || number.length > 15) errs.vehicleNumber = "Vehicle number must be 2-15 characters.";
  else if (!/^(?=.*\d)[A-Za-z0-9]+(\s*[- ]\s*[A-Za-z0-9]+)*$/.test(number)) {
    errs.vehicleNumber = "Use letters and digits separated by '-' or spaces, e.g. CAB-1024.";
  }
  if (!serviceArea.trim()) errs.serviceArea = "Service area is required.";
  return errs;
}

// Server-side field errors (VALIDATION_ERROR details) go under their fields;
// anything else is shown in the banner.
function splitServerError(e: unknown, fields: (keyof FieldErrors)[]): { fieldErrors: FieldErrors; banner: string | null } {
  if (!(e instanceof ApiError)) return { fieldErrors: {}, banner: "Something went wrong. Please try again." };
  if (e.code === "EMAIL_ALREADY_EXISTS") return { fieldErrors: { email: e.message }, banner: null };
  if (e.code === "VEHICLE_ALREADY_EXISTS") return { fieldErrors: { vehicleNumber: e.message }, banner: null };
  const fieldErrors: FieldErrors = {};
  for (const [field, message] of Object.entries(e.details ?? {})) {
    if ((fields as string[]).includes(field)) fieldErrors[field as keyof FieldErrors] = message;
  }
  return { fieldErrors, banner: Object.keys(fieldErrors).length > 0 ? null : e.message };
}

const HOME_BY_ROLE: Record<Role, string> = {
  PASSENGER: "/passenger",
  DRIVER: "/driver",
  ADMIN: "/admin/dashboard",
};

export default function AuthPage() {
  // useSearchParams needs a Suspense boundary.
  return (
    <Suspense fallback={null}>
      <AuthForm />
    </Suspense>
  );
}

function AuthForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  // Where to go after signing in: the page that sent the user here (only
  // same-site paths), else their role's dashboard.
  function destinationFor(role: Role): string {
    const next = searchParams.get("next");
    return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login") ? next : HOME_BY_ROLE[role];
  }

  const [mode, setMode] = useState<Mode>("login");
  const [role, setRole] = useState<Role>("PASSENGER");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Only shown right after a fresh DRIVER registration: account-service and
  // driver-service have no link between them, so a new driver has no
  // driver-service Driver record (needed for availability toggling) until
  // they create one here.
  const [needsDriverProfile, setNeedsDriverProfile] = useState<{ token: string } | null>(null);
  const [vehicleNumber, setVehicleNumber] = useState("");
  const [vehicleType, setVehicleType] = useState("Car");
  const [serviceArea, setServiceArea] = useState("");

  function switchMode(m: Mode) {
    setMode(m);
    setFieldErrors({});
    setError(null);
  }

  async function submit() {
    setError(null);
    const errs = mode === "login"
      ? validateLogin(email, password)
      : validateRegister(name, email, password);
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      return;
    }
    setFieldErrors({});
    setBusy(true);
    try {
      if (mode === "login") {
        const auth = await accountApi.login({ email: email.trim(), password });
        login({ token: auth.token, id: auth.id, name: auth.name, role: auth.role, email: email.trim() });
        router.push(destinationFor(auth.role));
      } else {
        const auth = await accountApi.register({ name: name.trim(), email: email.trim(), password, role });
        login({ token: auth.token, id: auth.id, name: auth.name, role: auth.role, email: email.trim() });
        if (auth.role === "DRIVER") {
          setNeedsDriverProfile({ token: auth.token });
        } else {
          router.push(destinationFor(auth.role));
        }
      }
    } catch (e) {
      const { fieldErrors: serverFieldErrors, banner } = splitServerError(e, ["name", "email", "password"]);
      setFieldErrors(serverFieldErrors);
      setError(banner);
    } finally {
      setBusy(false);
    }
  }

  async function submitDriverProfile() {
    if (!needsDriverProfile) return;
    setError(null);
    const errs = validateDriverProfile(vehicleNumber, serviceArea);
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      return;
    }
    setFieldErrors({});
    setBusy(true);
    try {
      await driverApi.createProfile(
        { name: name.trim(), email: email.trim(), vehicleNumber: vehicleNumber.trim(), vehicleType, serviceArea: serviceArea.trim() },
        needsDriverProfile.token
      );
      router.push("/driver");
    } catch (e) {
      const { fieldErrors: serverFieldErrors, banner } = splitServerError(e, ["vehicleNumber", "vehicleType", "serviceArea"]);
      setFieldErrors(serverFieldErrors);
      setError(banner);
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
            <FieldWithError label="Vehicle number" value={vehicleNumber} onChange={(e) => { setVehicleNumber(e.target.value); setFieldErrors(prev => ({ ...prev, vehicleNumber: undefined })); }} placeholder="CAB-1024" error={fieldErrors.vehicleNumber} />
            <SelectField 
              label="Vehicle type" 
              value={vehicleType} 
              onChange={(e) => setVehicleType(e.target.value)}
              options={[
                { value: "Car", label: "Car" },
                { value: "Sedan", label: "Sedan" },
                { value: "Hatchback", label: "Hatchback" },
                { value: "Bike", label: "Bike" },
                { value: "Tuk", label: "Tuk" },
                { value: "Van", label: "Van" },
              ]}
            />
            <FieldWithError label="Service area" value={serviceArea} onChange={(e) => { setServiceArea(e.target.value); setFieldErrors(prev => ({ ...prev, serviceArea: undefined })); }} placeholder="Malabe" error={fieldErrors.serviceArea} />
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
                onClick={() => switchMode("login")}
                style={tabStyle(mode === "login")}
              >
                Sign in
              </button>
              <button
                type="button"
                onClick={() => switchMode("register")}
                style={tabStyle(mode === "register")}
              >
                Create account
              </button>
            </div>

            <FieldWithError
              label="Email"
              type="email"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setFieldErrors(prev => ({ ...prev, email: undefined })); }}
              placeholder="you@example.com"
              error={fieldErrors.email}
            />

            {mode === "register" && (
              <FieldWithError
                label="Full name"
                value={name}
                onChange={(e) => { setName(e.target.value); setFieldErrors(prev => ({ ...prev, name: undefined })); }}
                placeholder="Ada Okonjo"
                error={fieldErrors.name}
              />
            )}

            <FieldWithError
              label="Password"
              type="password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setFieldErrors(prev => ({ ...prev, password: undefined })); }}
              placeholder={mode === "register" ? PASSWORD_HINT : "Your password"}
              error={fieldErrors.password}
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

            <PrimaryButton onClick={submit} disabled={busy}>
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
