"use client";

import { useEffect, useState, useCallback, useRef, use as usePromise } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import NavBar from "@/components/NavBar";
import { Card, ErrorBanner, StatusBadge, RideStepper, Field, PrimaryButton } from "@/components/ui";
import {
  rideApi, fareApi, ApiError, errorMessage, CANCELLABLE_STATUSES, TERMINAL_STATUSES,
  type Ride, type ReceiptResponse, type PaymentMethod,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDisplayId } from "@/lib/format";
import { formatMoney } from "@/lib/money";

const POLL_MS = 4000;

export default function RideDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = usePromise(params);
  const { session, ready } = useAuth();
  const router = useRouter();

  const [ride, setRide] = useState<Ride | null>(null);
  const [rideError, setRideError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Receipt / payment (passenger only; receipts are payer-or-admin).
  const [receipt, setReceipt] = useState<ReceiptResponse | null>(null);
  const [receiptChecked, setReceiptChecked] = useState(false);
  const [receiptError, setReceiptError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [payMethod, setPayMethod] = useState<PaymentMethod>("CARD");
  const [cardLast4, setCardLast4] = useState("");

  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // Latest status, read by the polling timer without re-creating it.
  const statusRef = useRef<Ride["status"] | null>(null);

  useEffect(() => {
    if (ready && !session) router.push("/login");
  }, [ready, session, router]);

  const canSeeReceipt = session?.role === "PASSENGER" || session?.role === "ADMIN";

  const loadReceipt = useCallback(async () => {
    try {
      setReceipt(await fareApi.receipt(id));
      setReceiptError(null);
    } catch (e) {
      // 404: not paid yet - the pay form is shown instead.
      if (e instanceof ApiError && e.status === 404) setReceipt(null);
      else setReceiptError(errorMessage(e, "We couldn't load the receipt."));
    } finally {
      setReceiptChecked(true);
    }
  }, [id]);

  const loadRide = useCallback(async () => {
    try {
      const r = await rideApi.get(id);
      statusRef.current = r.status;
      setRide(r);
      setRideError(null);
      if (r.status === "COMPLETED" && canSeeReceipt) await loadReceipt();
    } catch (e) {
      setRideError(errorMessage(e, "We couldn't load this ride."));
    } finally {
      setLoading(false);
    }
  }, [id, canSeeReceipt, loadReceipt]);

  // Load now, then poll every 4 s until the ride is completed or cancelled.
  useEffect(() => {
    if (!session) return;
    const first = setTimeout(loadRide, 0);
    const interval = setInterval(() => {
      if (statusRef.current && TERMINAL_STATUSES.has(statusRef.current)) {
        clearInterval(interval);
        return;
      }
      loadRide();
    }, POLL_MS);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
    };
  }, [session, loadRide]);

  async function cancelRide() {
    if (!ride) return;
    setCancelling(true);
    setCancelError(null);
    try {
      const updated = await rideApi.cancel(ride.id, {
        reason: session?.role === "DRIVER" ? "Cancelled by driver" : "Cancelled by passenger",
      });
      statusRef.current = updated.status;
      setRide(updated);
    } catch (e) {
      setCancelError(errorMessage(e, "We couldn't cancel this ride. Please try again."));
      await loadRide(); // show whatever the ride's status is now
    } finally {
      setCancelling(false);
    }
  }

  async function payRide() {
    if (!ride) return;
    setPaying(true);
    setPayError(null);
    try {
      await fareApi.pay({ rideId: ride.id, method: payMethod, cardLast4: payMethod === "CARD" ? cardLast4 : undefined });
      await loadReceipt();
    } catch (e) {
      if (e instanceof ApiError && e.code === "PAYMENT_ALREADY_EXISTS") {
        await loadReceipt(); // already paid - just show the receipt
      } else {
        // PAYMENT_FAILED leaves the form open so they can retry.
        setPayError(errorMessage(e, "The payment didn't go through. Please try again."));
      }
    } finally {
      setPaying(false);
    }
  }

  if (!ready || !session) return null;

  const isPassenger = session.role === "PASSENGER";
  const canCancel = !!ride && CANCELLABLE_STATUSES.has(ride.status) && session.role !== "ADMIN";
  const showPayForm = isPassenger && ride?.status === "COMPLETED" && receiptChecked && !receipt && !receiptError;

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Ride Service · 8083" />

      <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "18px 32px", borderBottom: "1px solid var(--line)", flexWrap: "wrap" }}>
        <Link href={session.role === "DRIVER" ? "/driver" : session.role === "ADMIN" ? "/admin/dashboard" : "/passenger"} style={{ fontSize: 12.5, color: "var(--muted)" }}>
          ← Back
        </Link>
        <h2 style={{ fontFamily: "var(--font-heading)", fontSize: 19, fontWeight: 600, margin: 0 }}>Ride {formatDisplayId(id)}</h2>
        {ride && <StatusBadge status={ride.status} />}
        {ride && !TERMINAL_STATUSES.has(ride.status) && (
          <span style={{ fontSize: 12, color: "var(--muted)" }}>Updates automatically</span>
        )}
      </div>

      <div style={{ padding: "28px 32px", maxWidth: 640 }}>
        {loading && <p style={{ color: "var(--muted)" }}>Loading…</p>}
        <ErrorBanner message={rideError} />

        {ride && (
          <Card style={{ marginBottom: 24 }}>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 4 }}>Trip</div>
            <RideStepper status={ride.status} />
            <Row label="Passenger" value={ride.passengerName} />
            <Row label="Pickup" value={ride.pickup} />
            <Row label="Destination" value={ride.destination} />
            {ride.vehicleType && <Row label="Vehicle" value={ride.vehicleType} />}
            <Row label="Driver" value={ride.driverId ? formatDisplayId(ride.driverId) : "Unassigned"} />
            {ride.estimatedFare != null && <Row label="Estimated fare" value={formatMoney(ride.estimatedFare)} />}
            {ride.finalFare != null && <Row label="Final fare" value={formatMoney(ride.finalFare)} />}
            {ride.status === "CANCELLED" && ride.cancelReason && <Row label="Cancelled" value={ride.cancelReason} />}

            {canCancel && (
              <div style={{ marginTop: 14 }}>
                <button
                  type="button"
                  onClick={cancelRide}
                  disabled={cancelling}
                  style={{
                    fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600,
                    background: "transparent", border: "1px solid #ef4444",
                    borderRadius: 7, padding: "9px 16px", cursor: "pointer", color: "#ef4444",
                    opacity: cancelling ? 0.6 : 1,
                  }}
                >
                  {cancelling ? "Cancelling…" : "Cancel ride"}
                </button>
              </div>
            )}
            <ErrorBanner message={cancelError} />
          </Card>
        )}

        {/* Pay - passengers, completed rides that aren't paid yet */}
        {showPayForm && (
          <Card style={{ marginBottom: 24 }}>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 6 }}>Pay for this ride</div>
            {ride?.finalFare != null && (
              <p style={{ margin: "0 0 14px", fontSize: 13.5, color: "var(--muted)" }}>Amount due: <strong>{formatMoney(ride.finalFare)}</strong></p>
            )}
            <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
              {(["CARD", "CASH"] as const).map(m => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setPayMethod(m)}
                  style={{
                    flex: 1, padding: "8px 0", borderRadius: 7, fontSize: 13,
                    fontFamily: "var(--font-heading)", fontWeight: 600,
                    border: `1px solid ${payMethod === m ? "var(--accent)" : "var(--line)"}`,
                    background: payMethod === m ? "rgba(255,196,0,0.1)" : "transparent",
                    cursor: "pointer", color: "var(--text)",
                  }}
                >
                  {m}
                </button>
              ))}
            </div>
            {payMethod === "CARD" && (
              <Field
                label="Last 4 digits of card"
                value={cardLast4}
                inputMode="numeric"
                onChange={(e) => setCardLast4(e.target.value.replace(/\D/g, "").slice(0, 4))}
                placeholder="4242  (0000 simulates a declined card)"
                maxLength={4}
              />
            )}
            <PrimaryButton onClick={payRide} disabled={paying || (payMethod === "CARD" && cardLast4.length !== 4)}>
              {paying ? "Processing…" : "Pay now"}
            </PrimaryButton>
            <ErrorBanner message={payError} />
          </Card>
        )}

        {ride && ride.status !== "COMPLETED" && ride.status !== "CANCELLED" && isPassenger && (
          <p style={{ color: "var(--muted)", fontSize: 13.5 }}>
            Once your driver completes the ride, you can pay and see your receipt here.
          </p>
        )}

        <ErrorBanner message={receiptError} />

        {receipt && (
          <div style={{ background: "var(--card)", color: "var(--card-text)", borderRadius: 12, padding: 24 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 22, gap: 12, flexWrap: "wrap" }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600 }}>
                Receipt {receipt.receiptNumber && <span style={{ fontWeight: 500, color: "#6b7280" }}>· {receipt.receiptNumber}</span>}
              </div>
              <div style={{ fontSize: 12.5, color: "#6b7280" }}>
                {(receipt.timestamp ?? receipt.completedAt) ? new Date((receipt.timestamp ?? receipt.completedAt) as string).toLocaleString() : ""}
              </div>
            </div>

            <div style={{ display: "grid", gap: 9, fontSize: 14, paddingBottom: 16, borderBottom: "1px solid rgba(31,58,95,.2)" }}>
              <Row label="Distance" value={`${receipt.distanceKm} km`} />
              <Row label="Duration" value={`${receipt.durationMin} min`} />
              <Row label="Payment method" value={receipt.method ?? receipt.paymentMethod} />
              <Row label="Status" value={receipt.status} />
            </div>

            <p style={{ fontSize: 12.5, color: "#6b7280", margin: "14px 0", lineHeight: 1.6 }}>{receipt.breakdown}</p>

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", paddingTop: 8 }}>
              <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>Total paid</span>
              <span style={{ fontFamily: "var(--font-heading)", fontSize: 30, fontWeight: 700 }}>{formatMoney(receipt.finalFare, receipt.currency)}</span>
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
      <span style={{ textAlign: "right" }}>{value}</span>
    </div>
  );
}
