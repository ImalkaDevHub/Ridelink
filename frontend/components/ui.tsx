"use client";

import type { InputHTMLAttributes, ButtonHTMLAttributes, ReactNode } from "react";

export function Field({
  label,
  ...props
}: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label style={{ display: "block", marginBottom: 14 }}>
      <span
        style={{
          display: "block",
          fontFamily: "var(--font-heading)",
          fontSize: 11.5,
          fontWeight: 600,
          color: "var(--muted)",
          marginBottom: 5,
        }}
      >
        {label}
      </span>
      <input
        {...props}
        style={{
          width: "100%",
          padding: "11px 12px",
          border: "1px solid rgba(31,58,95,.3)",
          borderRadius: 8,
          background: "#fff",
          color: "#14161a",
          fontSize: 14,
          ...props.style,
        }}
      />
    </label>
  );
}

export function FieldWithError({
  label,
  error,
  ...props
}: { label: string; error?: string | null } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label>
        <span
          style={{
            display: "block",
            fontFamily: "var(--font-heading)",
            fontSize: 11.5,
            fontWeight: 600,
            color: "var(--muted)",
            marginBottom: 5,
          }}
        >
          {label}
        </span>
        <input
          {...props}
          style={{
            width: "100%",
            padding: "11px 12px",
            border: `1px solid ${error ? "#ef4444" : "rgba(31,58,95,.3)"}`,
            borderRadius: 8,
            background: "#fff",
            color: "#14161a",
            fontSize: 14,
            boxSizing: "border-box",
            ...props.style,
          }}
        />
      </label>
      {error && (
        <p style={{ margin: "4px 0 0", fontSize: 12, color: "#ef4444" }}>{error}</p>
      )}
    </div>
  );
}

export function SelectField({
  label,
  options,
  ...props
}: { label: string; options: { value: string; label: string }[] } & React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <label style={{ display: "block", marginBottom: 14 }}>
      <span
        style={{
          display: "block",
          fontFamily: "var(--font-heading)",
          fontSize: 11.5,
          fontWeight: 600,
          color: "var(--muted)",
          marginBottom: 5,
        }}
      >
        {label}
      </span>
      <select
        {...props}
        style={{
          width: "100%",
          padding: "11px 12px",
          border: "1px solid rgba(31,58,95,.3)",
          borderRadius: 8,
          background: "#fff",
          color: "#14161a",
          fontSize: 14,
          cursor: "pointer",
          ...props.style,
        }}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
    </label>
  );
}

export function PrimaryButton({
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { children: ReactNode }) {
  return (
    <button
      {...props}
      disabled={disabled}
      style={{
        width: "100%",
        fontFamily: "var(--font-heading)",
        fontSize: 14.5,
        fontWeight: 600,
        background: "var(--accent)",
        color: "#111111",
        border: "none",
        borderRadius: 8,
        padding: 14,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.7 : 1,
        ...props.style,
      }}
    >
      {children}
    </button>
  );
}

export function SecondaryButton({
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { children: ReactNode }) {
  return (
    <button
      {...props}
      style={{
        fontFamily: "var(--font-heading)",
        fontSize: 13.5,
        fontWeight: 600,
        background: "transparent",
        color: "var(--accent)",
        border: "1px solid var(--accent)",
        borderRadius: 7,
        padding: "11px 16px",
        cursor: "pointer",
        ...props.style,
      }}
    >
      {children}
    </button>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{ border: "1px solid var(--line)", borderRadius: 12, padding: 22, ...style }}>
      {children}
    </div>
  );
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div
      style={{
        marginTop: 14,
        display: "flex",
        gap: 10,
        padding: "12px 14px",
        borderRadius: 8,
        background: "rgba(255,176,32,.1)",
        borderLeft: "3px solid var(--accent)",
      }}
    >
      <div style={{ fontSize: 13, lineHeight: 1.5 }}>{message}</div>
    </div>
  );
}

export function InfoBanner({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        display: "flex",
        gap: 14,
        padding: 16,
        borderRadius: 10,
        background: "rgba(31,58,95,.28)",
      }}
    >
      <div style={{ width: 3, background: "var(--muted)", borderRadius: 2, flexShrink: 0 }} />
      <div style={{ fontSize: 13.5, lineHeight: 1.55, color: "var(--muted)" }}>{children}</div>
    </div>
  );
}

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: "#60a5fa",
  ASSIGNED: "#f59e0b",
  ACCEPTED: "#f59e0b",
  IN_PROGRESS: "#ffb020",
  COMPLETED: "#3ddc84",
  CANCELLED: "#6b7280",
};

export function StatusBadge({ status }: { status: string }) {
  const color = STATUS_COLORS[status] ?? "#6b7280";
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        fontFamily: "var(--font-heading)",
        fontSize: 11,
        fontWeight: 600,
        color,
        border: `1px solid ${color}66`,
        padding: "4px 10px",
        borderRadius: 999,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: "50%", background: color }} />
      {status}
    </span>
  );
}

const STEPPER_STATES = ["REQUESTED", "ASSIGNED", "ACCEPTED", "IN_PROGRESS", "COMPLETED"];

export function RideStepper({ status }: { status: string }) {
  const isCancelled = status === "CANCELLED";
  const activeIndex = STEPPER_STATES.indexOf(status);

  return (
    <div style={{ margin: "16px 0" }}>
      {isCancelled ? (
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ width: 28, height: 28, borderRadius: "50%", background: "#6b7280", display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontSize: 13 }}>✕</span>
          <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600, color: "#6b7280" }}>Cancelled</span>
        </div>
      ) : (
        <div style={{ display: "flex", alignItems: "center", gap: 0, overflowX: "auto", paddingBottom: 4 }}>
          {STEPPER_STATES.map((step, i) => {
            const done = i < activeIndex;
            const current = i === activeIndex;
            const accentColor = step === "COMPLETED" ? "#3ddc84" : "var(--accent)";
            const color = done || current ? accentColor : "var(--line)";
            return (
              <div key={step} style={{ display: "flex", alignItems: "center", flexShrink: 0 }}>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
                  <div style={{
                    width: 26, height: 26, borderRadius: "50%",
                    background: done || current ? color : "transparent",
                    border: `2px solid ${color}`,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    fontSize: 11, fontWeight: 700,
                    color: done || current ? "#111" : color,
                    transition: "all 0.3s",
                  }}>
                    {done ? "✓" : i + 1}
                  </div>
                  <span style={{
                    fontSize: 10, fontFamily: "var(--font-heading)",
                    fontWeight: current ? 700 : 400,
                    color: done || current ? "var(--text)" : "var(--muted)",
                    whiteSpace: "nowrap",
                  }}>
                    {step === "IN_PROGRESS" ? "In Progress" : step.charAt(0) + step.slice(1).toLowerCase()}
                  </span>
                </div>
                {i < STEPPER_STATES.length - 1 && (
                  <div style={{
                    width: 24, height: 2,
                    background: i < activeIndex ? accentColor : "var(--line)",
                    margin: "0 2px", marginBottom: 18,
                    transition: "background 0.3s", flexShrink: 0,
                  }} />
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
