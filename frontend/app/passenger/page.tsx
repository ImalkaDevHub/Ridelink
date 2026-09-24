"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge } from "@/components/ui";
import { fareApi, rideApi, ApiError, type FareEstimateResponse, type Ride } from "@/lib/api";
import { useAuth } from "@/lib/auth";

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

  const [allRides, setAllRides] = useState<Ride[]>([]);

  const currentRide = allRides.find(r => r.status !== "COMPLETED" && r.status !== "CANCELLED") || null;
  const recentRides = allRides.filter(r => r.status === "COMPLETED" || r.status === "CANCELLED").slice(0, 10);

  useEffect(() => {
    if (ready && (!session || session.role !== "PASSENGER")) router.push("/login");
  }, [ready, session, router]);

  const refreshRide = useCallback(async () => {
    if (!session?.id) return;
    try {
      const rides = await rideApi.listByPassenger(session.id, session.token);
      setAllRides(rides);
    } catch (err: any) {
      // Error handling
    }
  }, [session]);

  useEffect(() => {
    if (!session?.id) {
      setAllRides([]);
      return;
    }
    
    refreshRide();
    const interval = setInterval(() => {
      // We can check if there's an active ride to decide if we want to keep polling
      refreshRide();
    }, 5000);
    return () => clearInterval(interval);
  }, [session?.id, refreshRide]);

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
      const distance = Number(distanceKm || 0);
      const duration = Number(durationMin || 0);
      const fare = estimate?.estimatedFare;
      await rideApi.request({ passengerName: session.name, pickup, destination, distance, duration, fare }, session.token);
      refreshRide();
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
              {currentRide?.id && (
                <Link href={`/rides/${currentRide.id}`} style={{ marginLeft: "auto", fontSize: 12.5 }}>
                  Ride #{currentRide.id}
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
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>Your Ride History</div>
            {recentRides.length === 0 ? (
              <InfoBanner>
                No completed or cancelled rides found in your history yet.
              </InfoBanner>
            ) : (
              <div style={{ display: "grid", gap: 8 }}>
                {recentRides.map((ride) => (
                  <Link key={ride.id} href={`/rides/${ride.id}`} style={{ fontSize: 13.5 }}>
                    Ride #{ride.id} ({ride.status})
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
