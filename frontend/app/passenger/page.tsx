"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import NavBar from "@/components/NavBar";
import { FieldWithError, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge, RideStepper } from "@/components/ui";
import {
  fareApi, rideApi, errorMessage, CANCELLABLE_STATUSES, TERMINAL_STATUSES,
  type FareEstimateResponse, type Ride,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDisplayId } from "@/lib/format";
import { formatMoney } from "@/lib/money";

const VEHICLE_TYPES = ["Bike", "Tuk", "Car", "Van"];

export default function PassengerPage() {
  const { session, ready } = useAuth();
  const router = useRouter();

  const [pickup, setPickup] = useState("");
  const [destination, setDestination] = useState("");
  const [distanceKm, setDistanceKm] = useState("");
  const [durationMin, setDurationMin] = useState("");
  const [vehicleType, setVehicleType] = useState("Car");
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  // The estimate shown before confirming. Cleared whenever the trip changes,
  // so the passenger always confirms against an up-to-date price.
  const [estimate, setEstimate] = useState<FareEstimateResponse | null>(null);
  const [estimating, setEstimating] = useState(false);
  const [estimateError, setEstimateError] = useState<string | null>(null);

  const [requesting, setRequesting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);

  const [allRides, setAllRides] = useState<Ride[]>([]);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const activeRide = allRides.find(r => !TERMINAL_STATUSES.has(r.status)) ?? null;
  const recentRides = allRides.filter(r => TERMINAL_STATUSES.has(r.status)).slice(0, 10);

  useEffect(() => {
    if (ready && (!session || session.role !== "PASSENGER")) router.push("/login");
  }, [ready, session, router]);

  const passengerId = session?.id;

  const refreshRides = useCallback(async () => {
    if (!passengerId) return;
    try {
      setAllRides(await rideApi.listByPassenger(passengerId));
    } catch {
      // Polling errors are transient; the next tick retries.
    }
  }, [passengerId]);

  // Load now, then poll every 4 s so the current ride's status stays live.
  useEffect(() => {
    if (!passengerId) return;
    const first = setTimeout(refreshRides, 0);
    const interval = setInterval(refreshRides, 4000);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
    };
  }, [passengerId, refreshRides]);

  // Any change to the trip invalidates the estimate.
  function changeTrip(setter: (v: string) => void, field: string) {
    return (value: string) => {
      setter(value);
      setFormErrors(p => ({ ...p, [field]: "" }));
      setEstimate(null);
    };
  }

  // Distance and duration are optional: Fare & Payment Service can work the
  // distance out from known place names and estimate the duration.
  function validateForm(): boolean {
    const errs: Record<string, string> = {};
    if (!pickup.trim()) errs.pickup = "Pickup is required.";
    if (!destination.trim()) errs.destination = "Destination is required.";
    if (distanceKm && !(Number(distanceKm) > 0)) errs.distanceKm = "Distance must be more than 0.";
    if (durationMin && !(Number(durationMin) >= 0)) errs.durationMin = "Duration can't be negative.";
    setFormErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function getEstimate() {
    if (!validateForm()) return;
    setEstimateError(null);
    setEstimating(true);
    setEstimate(null);
    try {
      setEstimate(await fareApi.estimate({
        pickupLocation: pickup.trim(),
        destinationLocation: destination.trim(),
        distanceKm: distanceKm ? Number(distanceKm) : undefined,
        durationMin: durationMin ? Number(durationMin) : undefined,
        vehicleType,
      }));
    } catch (e) {
      setEstimateError(errorMessage(e, "We couldn't get an estimate right now. Please try again."));
    } finally {
      setEstimating(false);
    }
  }

  async function requestRide() {
    if (!session || !estimate || !validateForm()) return;
    setRequestError(null);
    setRequesting(true);
    try {
      const ride = await rideApi.request({
        passengerName: session.name,
        pickup: pickup.trim(),
        destination: destination.trim(),
        vehicleType,
        // The figures the passenger just saw in the estimate.
        distanceKm: estimate.distanceKm,
        durationMin: estimate.durationMin,
        distance: estimate.distanceKm,
        duration: Math.round(estimate.durationMin),
        fare: estimate.estimatedFare,
      });
      setPickup(""); setDestination(""); setDistanceKm(""); setDurationMin(""); setEstimate(null);
      router.push(`/rides/${ride.id}`);
    } catch (e) {
      setRequestError(errorMessage(e, "We couldn't book your ride. Please try again."));
    } finally {
      setRequesting(false);
    }
  }

  async function cancelRide(rideId: string) {
    setCancelError(null);
    setCancellingId(rideId);
    try {
      await rideApi.cancel(rideId, { reason: "Cancelled by passenger" });
      await refreshRides();
    } catch (e) {
      setCancelError(errorMessage(e, "We couldn't cancel the ride. Please try again."));
      await refreshRides();
    } finally {
      setCancellingId(null);
    }
  }

  if (!ready || !session) return null;

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Ride Service · 8083" />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px" }}>
        {/* Request form */}
        <div style={{ background: "var(--card)", color: "var(--card-text)", borderRadius: 12, padding: 22 }}>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 16 }}>Request a ride</div>

          <FieldWithError label="Pickup" value={pickup} onChange={(e) => changeTrip(setPickup, "pickup")(e.target.value)} placeholder="SLIIT" error={formErrors.pickup} />
          <FieldWithError label="Destination" value={destination} onChange={(e) => changeTrip(setDestination, "destination")(e.target.value)} placeholder="Malabe Junction" error={formErrors.destination} />

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <FieldWithError label="Distance (km, optional)" type="number" step="0.1" min="0" value={distanceKm} onChange={(e) => changeTrip(setDistanceKm, "distanceKm")(e.target.value)} error={formErrors.distanceKm} />
            <FieldWithError label="Duration (min, optional)" type="number" step="1" min="0" value={durationMin} onChange={(e) => changeTrip(setDurationMin, "durationMin")(e.target.value)} error={formErrors.durationMin} />
          </div>
          <p style={{ marginTop: -6, marginBottom: 14, fontSize: 12, color: "#6b7280" }}>
            Leave these blank for known places (SLIIT, Malabe, Colombo Fort, Kandy...) and we&apos;ll work them out.
          </p>

          <div style={{ marginBottom: 16 }}>
            <label style={{ display: "block", fontSize: 13.5, color: "var(--muted)", marginBottom: 8 }}>Vehicle Type</label>
            <div style={{ display: "flex", gap: 10 }}>
              {VEHICLE_TYPES.map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => changeTrip(setVehicleType, "vehicleType")(type)}
                  style={{
                    flex: 1, padding: "8px 0", borderRadius: 8,
                    border: `1px solid ${vehicleType === type ? "var(--accent)" : "var(--line)"}`,
                    background: vehicleType === type ? "rgba(255, 196, 0, 0.1)" : "transparent",
                    color: vehicleType === type ? "var(--accent)" : "var(--text)",
                    fontWeight: vehicleType === type ? 600 : 400,
                    cursor: "pointer", fontSize: 13.5,
                  }}
                >
                  {type}
                </button>
              ))}
            </div>
          </div>

          <button
            type="button"
            onClick={getEstimate}
            disabled={estimating}
            style={{
              width: "100%", marginBottom: 14, fontFamily: "var(--font-heading)",
              fontSize: 13.5, fontWeight: 600, background: "transparent",
              border: "1px solid rgba(31,58,95,.4)", borderRadius: 8, padding: 12,
              cursor: "pointer", color: "var(--text)",
            }}
          >
            {estimating ? "Calculating..." : estimate ? "Recalculate estimate" : "Get fare estimate"}
          </button>
          <ErrorBanner message={estimateError} />

          {estimate && (
            <div style={{ borderTop: "1px solid rgba(31,58,95,.18)", paddingTop: 16, marginBottom: 18 }}>
              <Row label="Base fare" value={formatMoney(estimate.baseFare, estimate.currency)} />
              <Row label={`Distance (${estimate.perKmRate}/km × ${estimate.distanceKm} km)`} value={formatMoney(estimate.perKmRate * estimate.distanceKm)} />
              <Row label={`Time (${estimate.perMinRate}/min × ${estimate.durationMin} min${estimate.durationEstimated ? ", estimated" : ""})`} value={formatMoney(estimate.perMinRate * estimate.durationMin)} />
              {estimate.minimumApplied && estimate.minimumFare != null && (
                <Row label="Minimum fare applies" value={formatMoney(estimate.minimumFare)} />
              )}
              {estimate.vehicleMultiplier != null && estimate.vehicleMultiplier !== 1 && (
                <Row label={`${estimate.vehicleType ?? vehicleType} rate`} value={`× ${estimate.vehicleMultiplier}`} />
              )}
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginTop: 4 }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>Estimated total</span>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: 26, fontWeight: 700 }}>{formatMoney(estimate.estimatedFare, estimate.currency)}</span>
              </div>
              {estimate.distanceSource && estimate.distanceSource !== "PROVIDED" && (
                <p style={{ margin: "8px 0 0", fontSize: 12, color: "#6b7280" }}>
                  Distance is a straight-line estimate between the two places; the final fare uses the actual trip.
                </p>
              )}
            </div>
          )}

          <PrimaryButton onClick={requestRide} disabled={requesting || !!activeRide || !estimate}>
            {requesting ? "Requesting..." : activeRide ? "Ride already active" : estimate ? "Confirm ride request" : "Get an estimate to continue"}
          </PrimaryButton>
          {activeRide && <p style={{ marginTop: 8, fontSize: 12, color: "var(--muted)" }}>Complete or cancel your active ride first.</p>}
          <ErrorBanner message={requestError} />
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          {/* Active ride card */}
          <Card>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8, flexWrap: "wrap" }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600 }}>Current ride</div>
              {activeRide && <StatusBadge status={activeRide.status} />}
              {activeRide?.id && (
                <Link href={`/rides/${activeRide.id}`} style={{ marginLeft: "auto", fontSize: 12.5 }}>
                  Track {formatDisplayId(activeRide.id)} →
                </Link>
              )}
            </div>

            {!activeRide && (
              <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.6, color: "var(--muted)" }}>
                Nothing in progress. Request a ride and its status will track here.
              </p>
            )}

            {activeRide && (
              <div>
                <RideStepper status={activeRide.status} />
                <Row label="Pickup" value={activeRide.pickup} />
                <Row label="Destination" value={activeRide.destination} />
                <Row label="Driver" value={activeRide.driverId ? formatDisplayId(activeRide.driverId) : "Awaiting assignment…"} />
                {activeRide.estimatedFare != null && <Row label="Estimated fare" value={formatMoney(activeRide.estimatedFare)} />}

                {/* Cancel - allowed until the ride is in progress */}
                {CANCELLABLE_STATUSES.has(activeRide.status) && (
                  <div style={{ marginTop: 14 }}>
                    <button
                      type="button"
                      onClick={() => cancelRide(activeRide.id)}
                      disabled={cancellingId === activeRide.id}
                      style={{
                        fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600,
                        background: "transparent", border: "1px solid #ef4444",
                        borderRadius: 7, padding: "9px 16px", cursor: "pointer", color: "#ef4444",
                        opacity: cancellingId === activeRide.id ? 0.6 : 1,
                      }}
                    >
                      {cancellingId === activeRide.id ? "Cancelling…" : "Cancel ride"}
                    </button>
                  </div>
                )}
                <ErrorBanner message={cancelError} />
              </div>
            )}
          </Card>

          {/* History */}
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>Ride History</div>
            {recentRides.length === 0 ? (
              <InfoBanner>No completed or cancelled rides yet.</InfoBanner>
            ) : (
              <div style={{ display: "grid", gap: 8 }}>
                {recentRides.map((ride) => (
                  <Link key={ride.id} href={`/rides/${ride.id}`} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, fontSize: 13.5, padding: "8px 0", borderBottom: "1px solid var(--line)" }}>
                    <span>{ride.pickup} → {ride.destination}</span>
                    <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      {ride.status === "COMPLETED" && ride.finalFare != null && (
                        <span style={{ fontSize: 12, fontWeight: 600 }}>{formatMoney(ride.finalFare)}</span>
                      )}
                      <StatusBadge status={ride.status} />
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </Card>
        </div>
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
