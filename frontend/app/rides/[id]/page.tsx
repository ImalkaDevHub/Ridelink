"use client";

import { useEffect, useState, use as usePromise } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import NavBar from "@/components/NavBar";
import { Card, ErrorBanner, StatusBadge } from "@/components/ui";
import { rideApi, fareApi, ApiError, type Ride, type ReceiptResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function RideDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = usePromise(params);
  const { session, ready } = useAuth();
  const router = useRouter();

  const [ride, setRide] = useState<Ride | null>(null);
  const [rideError, setRideError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<ReceiptResponse | null>(null);
  const [receiptError, setReceiptError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (ready && !session) router.push("/login");
  }, [ready, session, router]);

  useEffect(() => {
    if (!session) return;
    let cancelled = false;

    async function load() {
      setLoading(true);
      try {
        const r = await rideApi.get(id, session!.token);
        if (cancelled) return;
        setRide(r);
        if (r.status === "COMPLETED") {
          try {
            const rec = await fareApi.receipt(id, session!.token);
            if (!cancelled) setReceipt(rec);
          } catch (e) {
            if (!cancelled) setReceiptError(e instanceof ApiError ? e.message : "Could not load the receipt.");
          }
        }
      } catch (e) {
        if (!cancelled) setRideError(e instanceof ApiError ? e.message : "Could not reach Ride Service.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id, session]);

  if (!ready || !session) return null;

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Fare & Payment Service &middot; 8084" />

      <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "18px 32px", borderBottom: "1px solid var(--line)", flexWrap: "wrap" }}>
        <Link href={session.role === "DRIVER" ? "/driver" : "/passenger"} style={{ fontSize: 12.5, color: "var(--muted)" }}>
          &larr; Back
        </Link>
        <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 19, fontWeight: 600, margin: 0 }}>Ride #{id}</h2>
        {ride && <StatusBadge status={ride.status} />}
      </div>

      <div style={{ padding: "28px 32px", maxWidth: 640 }}>
        {loading && <p style={{ color: "var(--muted)" }}>Loading...</p>}
        <ErrorBanner message={rideError} />

        {ride && (
          <Card style={{ marginBottom: 24 }}>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 16 }}>Trip</div>
            <Row label="Passenger" value={ride.passengerName} />
            <Row label="Pickup" value={ride.pickup} />
            <Row label="Destination" value={ride.destination} />
            <Row label="Driver id" value={ride.driverId ? String(ride.driverId) : "unassigned"} />
          </Card>
        )}

        {ride && ride.status !== "COMPLETED" && (
          <p style={{ color: "var(--muted)", fontSize: 13.5 }}>
            A receipt appears here once the assigned driver marks this ride complete.
          </p>
        )}

        <ErrorBanner message={receiptError} />

        {receipt && (
          <div style={{ background: "var(--card)", color: "var(--card-text)", borderRadius: 12, padding: 24 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 22 }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600 }}>Receipt</div>
              <div style={{ fontSize: 12.5, color: "#6b7280" }}>
                {receipt.completedAt ? new Date(receipt.completedAt).toLocaleString() : ""}
              </div>
            </div>

            <div style={{ display: "grid", gap: 9, fontSize: 14, paddingBottom: 16, borderBottom: "1px solid rgba(31,58,95,.2)" }}>
              <Row label="Distance" value={`${receipt.distanceKm} km`} />
              <Row label="Duration" value={`${receipt.durationMin} min`} />
              <Row label="Payment method" value={receipt.paymentMethod} />
            </div>

            <p style={{ fontSize: 12.5, color: "#6b7280", margin: "14px 0", lineHeight: 1.6 }}>{receipt.breakdown}</p>

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", paddingTop: 8 }}>
              <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>Total paid</span>
              <span style={{ fontFamily: "var(--font-heading)", fontSize: 30, fontWeight: 700 }}>{receipt.finalFare.toFixed(2)}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13.5, marginBottom: 7, gap: 12 }}>
      <span style={{ color: "#6b7280" }}>{label}</span>
      <span>{value}</span>
    </div>
  );
}
