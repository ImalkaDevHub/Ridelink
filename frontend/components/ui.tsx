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
  ASSIGNED: "#ffb020",
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
