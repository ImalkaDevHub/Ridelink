"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge } from "@/components/ui";
import { fareApi, rideApi, ApiError, type FareEstimateResponse, type Ride } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { addRecentRideId, getRecentRideIds } from "@/lib/recentRides";

export default function PassengerPage() {
  const { session, ready } = useAuth();
  const router = useRouter();

  const [pickup, setPickup] = useState("");
  const [destination, setDestination] = useState("");
  const [distanceKm, setDistanceKm] = useState("");
  const [durationMin, setDurationMin] = useState("");

  const [estimate, setEstimate] = useState<FareEstimateResponse | null>(null);
  const [estimating, setEstimating] = useState(false);
  const [estimateError, setEstimateError] = useState<string | null>(null);

  const [requesting, setRequesting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);

  const [currentRideId, setCurrentRideId] = useState<number | null>(null);
  const [currentRide, setCurrentRide] = useState<Ride | null>(null);
  const [recentIds, setRecentIds] = useState<number[]>([]);

  useEffect(() => {
    if (ready && (!session || session.role !== "PASSENGER")) router.push("/login");
  }, [ready, session, router]);

  useEffect(() => {
    // Deferred to an effect (not a lazy useState initializer): the server
    // has no localStorage, so the first client render must also start
    // empty to match the server-rendered HTML, or React flags a hydration
    // mismatch.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRecentIds(getRecentRideIds());
    try {
      const stored = window.localStorage.getItem("ridelink.currentRideId");
      if (stored) setCurrentRideId(Number(stored));
    } catch {
      // ignore
    }
  }, []);

  const refreshRide = useCallback(async () => {
    if (!currentRideId || !session) return;
    try {
      const ride = await rideApi.get(currentRideId, session.token);
      setCurrentRide(ride);
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 404) {
        setCurrentRideId(null);
        window.localStorage.removeItem("ridelink.currentRideId");
      }
      // A stale/invalid id shouldn't crash the page, but we stop polling if 404.
    }
  }, [currentRideId, session]);

  useEffect(() => {
    // Polling an external system (Ride Service) on an interval - a
    // deliberate, ongoing subscription, not state derivable during render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshRide();
    if (!currentRideId) return;
    const interval = setInterval(() => {
      if (currentRide?.status !== "COMPLETED") refreshRide();
    }, 5000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentRideId]);

  async function getEstimate() {
    setEstimateError(null);
    setEstimating(true);
    setEstimate(null);
    try {
      const result = await fareApi.estimate({
        pickupLocation: pickup,
        destinationLocation: destination,
        distanceKm: Number(distanceKm),
        durationMin: Number(durationMin),
      });
      setEstimate(result);
    } catch (e) {
      setEstimateError(e instanceof ApiError ? e.message : "Could not reach Fare & Payment Service.");
    } finally {
      setEstimating(false);
    }
  }

  async function requestRide() {
    if (!session) return;
    setRequestError(null);
    setRequesting(true);
    try {
      const ride = await rideApi.request({ passengerName: session.name, pickup, destination }, session.token);
      setCurrentRide(ride);
      setCurrentRideId(ride.id);
      addRecentRideId(ride.id);
      setRecentIds(getRecentRideIds());
      try {
        window.localStorage.setItem("ridelink.currentRideId", String(ride.id));
      } catch {
        // ignore
      }
    } catch (e) {
      setRequestError(e instanceof ApiError ? e.message : "Could not reach Ride Service.");
    } finally {
      setRequesting(false);
    }
  }

  if (!ready || !session) return null;

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Ride Service &middot; 8083" />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px" }}>
        <div style={{ background: "var(--card)", color: "var(--card-text)", borderRadius: 12, padding: 22 }}>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 16 }}>Request a ride</div>

          <Field label="Pickup" value={pickup} onChange={(e) => setPickup(e.target.value)} />
          <Field label="Destination" value={destination} onChange={(e) => setDestination(e.target.value)} />

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Distance (km)" type="number" step="0.1" value={distanceKm} onChange={(e) => setDistanceKm(e.target.value)} />
            <Field label="Duration (min)" type="number" step="1" value={durationMin} onChange={(e) => setDurationMin(e.target.value)} />
          </div>
          <p style={{ marginTop: -6, marginBottom: 14, fontSize: 12, color: "#6b7280" }}>
            Fare & Payment Service calculates from distance and duration - there is no mapping/routing
            engine in this system, so these are entered directly.
          </p>

          <button
            type="button"
            onClick={getEstimate}
            disabled={estimating}
            style={{
              width: "100%",
              marginBottom: 14,
              fontFamily: "var(--font-heading)",
              fontSize: 13.5,
              fontWeight: 600,
              background: "transparent",
              border: "1px solid rgba(31,58,95,.4)",
              borderRadius: 8,
              padding: 12,
              cursor: "pointer",
              color: "#14161a",
            }}
          >
            {estimating ? "Calculating..." : "Get fare estimate"}
          </button>
          <ErrorBanner message={estimateError} />

          {estimate && (
            <div style={{ borderTop: "1px solid rgba(31,58,95,.18)", paddingTop: 16, marginBottom: 18 }}>
              <Row label="Base fare" value={estimate.baseFare.toFixed(2)} />
              <Row label={`Distance (${estimate.perKmRate}/km × ${estimate.distanceKm}km)`} value={(estimate.perKmRate * estimate.distanceKm).toFixed(2)} />
              <Row label={`Time (${estimate.perMinRate}/min × ${estimate.durationMin}min)`} value={(estimate.perMinRate * estimate.durationMin).toFixed(2)} />
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginTop: 4 }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>Fare estimate</span>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: 26, fontWeight: 700 }}>{estimate.estimatedFare.toFixed(2)}</span>
              </div>
            </div>
          )}

          <PrimaryButton onClick={requestRide} disabled={requesting || !pickup || !destination}>
            {requesting ? "Requesting..." : "Confirm ride request"}
          </PrimaryButton>
          <ErrorBanner message={requestError} />
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          <Card>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 20 }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600 }}>Current ride</div>
              {currentRide && <StatusBadge status={currentRide.status} />}
              {currentRideId && (
                <Link href={`/rides/${currentRideId}`} style={{ marginLeft: "auto", fontSize: 12.5 }}>
                  Ride #{currentRideId}
                </Link>
              )}
            </div>

            {!currentRide && (
              <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.6, color: "var(--muted)" }}>
                Nothing in progress. Request a ride and its status will track here.
              </p>
            )}

            {currentRide && (
              <div>
                <Row label="Pickup" value={currentRide.pickup} />
                <Row label="Destination" value={currentRide.destination} />
                <Row label="Driver id" value={currentRide.driverId ? String(currentRide.driverId) : "unassigned"} />
                {currentRide.status === "COMPLETED" && (
                  <Link
                    href={`/rides/${currentRide.id}`}
                    style={{
                      display: "inline-block",
                      marginTop: 12,
                      fontFamily: "var(--font-heading)",
                      fontSize: 13,
                      fontWeight: 600,
                      color: "var(--accent)",
                    }}
                  >
                    View receipt {"→"}
                  </Link>
                )}
              </div>
            )}
          </Card>

          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>Recent on this device</div>
            {recentIds.length === 0 ? (
              <InfoBanner>
                Ride Service has no &quot;list my rides&quot; endpoint yet, only lookup by id - so
                this is just what this browser has requested, not a real history.
              </InfoBanner>
            ) : (
              <div style={{ display: "grid", gap: 8 }}>
                {recentIds.map((id) => (
                  <Link key={id} href={`/rides/${id}`} style={{ fontSize: 13.5 }}>
                    Ride #{id}
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
      <span>{value}</span>
    </div>
  );
}
