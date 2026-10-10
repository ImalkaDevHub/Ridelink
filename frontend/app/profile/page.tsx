"use client";

import { useState } from "react";
import NavBar from "@/components/NavBar";
import { FieldWithError, PrimaryButton, ErrorBanner, Card } from "@/components/ui";
import { accountApi, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { PASSWORD_HINT, validatePassword } from "@/lib/validation";

// Mirrors account-service's PasswordChangeRequest rules.
function validatePasswordChange(current: string, newPwd: string, confirm: string) {
  const errs: Record<string, string> = {};
  if (!current) errs.current = "Current password is required.";
  const ruleError = validatePassword(newPwd);
  if (ruleError) errs.newPwd = ruleError;
  else if (newPwd === current) errs.newPwd = "New password must differ from your current password.";
  if (!confirm) errs.confirm = "Please confirm your new password.";
  else if (confirm !== newPwd) errs.confirm = "Passwords do not match.";
  return errs;
}

export default function ProfilePage() {
  const { session } = useAuth();

  const [current, setCurrent] = useState("");
  const [newPwd, setNewPwd] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [busy, setBusy] = useState(false);

  async function changePassword() {
    if (!session) return;
    setError(null);
    setSuccess(false);
    const errs = validatePasswordChange(current, newPwd, confirm);
    if (Object.keys(errs).length > 0) { setFieldErrors(errs); return; }
    setFieldErrors({});
    setBusy(true);
    try {
      await accountApi.changePassword(session.id, { currentPassword: current, newPassword: newPwd });
      setSuccess(true);
      setCurrent(""); setNewPwd(""); setConfirm("");
    } catch (e) {
      // Put server-side problems next to the field they're about.
      if (e instanceof ApiError && e.code === "WRONG_PASSWORD") setFieldErrors({ current: e.message });
      else if (e instanceof ApiError && e.code === "SAME_PASSWORD") setFieldErrors({ newPwd: e.message });
      else if (e instanceof ApiError && e.details?.newPassword) setFieldErrors({ newPwd: e.details.newPassword });
      else if (e instanceof ApiError && e.details?.currentPassword) setFieldErrors({ current: e.details.currentPassword });
      else setError(e instanceof ApiError ? e.message : "Could not change your password. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar />
      <div style={{ maxWidth: 480, margin: "0 auto", padding: "36px 24px" }}>
        <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 22, fontWeight: 700, marginBottom: 4 }}>
          Profile
        </h2>
        {session && (
          <p style={{ color: "var(--muted)", fontSize: 13.5, marginBottom: 28 }}>
            {session.name} &middot; {session.role}
            {session.email && <> &middot; {session.email}</>}
          </p>
        )}

        <Card>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 18 }}>
            Change Password
          </div>
          <FieldWithError
            label="Current password"
            type="password"
            value={current}
            onChange={(e) => { setCurrent(e.target.value); setFieldErrors(p => ({ ...p, current: "" })); }}
            placeholder="Your current password"
            error={fieldErrors.current}
          />
          <FieldWithError
            label="New password"
            type="password"
            value={newPwd}
            onChange={(e) => { setNewPwd(e.target.value); setFieldErrors(p => ({ ...p, newPwd: "" })); }}
            placeholder={PASSWORD_HINT}
            error={fieldErrors.newPwd}
          />
          <FieldWithError
            label="Confirm new password"
            type="password"
            value={confirm}
            onChange={(e) => { setConfirm(e.target.value); setFieldErrors(p => ({ ...p, confirm: "" })); }}
            placeholder="Repeat new password"
            error={fieldErrors.confirm}
          />
          <PrimaryButton onClick={changePassword} disabled={busy}>
            {busy ? "Saving..." : "Change password"}
          </PrimaryButton>
          <ErrorBanner message={error} />
          {success && (
            <p style={{ marginTop: 14, fontSize: 13, color: "#3ddc84", fontWeight: 600 }}>
              ✓ Password changed successfully.
            </p>
          )}
        </Card>
      </div>
    </div>
  );
}
