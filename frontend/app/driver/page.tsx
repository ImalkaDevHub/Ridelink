"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge } from "@/components/ui";
import { driverApi, rideApi, ApiError, type Ride } from "@/lib/api";
import { useAuth } from "@/lib/auth";

// useSearchParams() (read below, for the ?newDriverId= redirect after
// registration) requires a Suspense boundary around anything that calls it,
// or Next.js can't statically bail out to client rendering for this page.
export default function DriverPage() {
  return (
    <Suspense fallback={null}>
      <DriverDashboard />
    </Suspense>
  );
}

function DriverDashboard() {
  const { session, ready, updateSession } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [driverProfileId, setDriverProfileId] = useState<string>("");
  const [available, setAvailable] = useState(false);
  const [togglingError, setTogglingError] = useState<string | null>(null);
  const [toggling, setToggling] = useState(false);

  const [rideId, setRideId] = useState("");
  const [lookedUpRide, setLookedUpRide] = useState<Ride | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const [completedRide, setCompletedRide] = useState<Ride | null>(null);

  useEffect(() => {
    if (ready && (!session || session.role !== "DRIVER")) router.push("/login");
  }, [ready, session, router]);

  useEffect(() => {
    // Syncing local form state (and the persisted session) to the
    // ?newDriverId= query param set right after registration - a one-time
    // reconciliation with an external source (the URL), not state that
    // could instead be computed during render.
    const fromRegistration = searchParams.get("newDriverId");
    if (fromRegistration) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDriverProfileId(fromRegistration);
      updateSession({ driverProfileId: Number(fromRegistration) });
    } else if (session?.driverProfileId) {
      setDriverProfileId(String(session.driverProfileId));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, session?.driverProfileId]);

  function saveDriverProfileId() {
    const id = Number(driverProfileId);
    if (Number.isFinite(id) && id > 0) updateSession({ driverProfileId: id });
  }

  async function toggleOnline() {
    if (!session) return;
    const id = Number(driverProfileId);
    if (!Number.isFinite(id) || id <= 0) {
      setTogglingError("Enter your driver profile id first (shown when you created your vehicle).");
      return;
    }
    setTogglingError(null);
    setToggling(true);
    try {
      const driver = await driverApi.setAvailability(id, !available, session.token);
      setAvailable(driver.available);
    } catch (e) {
      setTogglingError(e instanceof ApiError ? e.message : "Could not reach Driver Service.");
    } finally {
      setToggling(false);
    }
  }

  async function lookupRide() {
    if (!session || !rideId) return;
    setLookupError(null);
    setCompletedRide(null);
    setLookedUpRide(null);
    try {
      const ride = await rideApi.get(rideId, session.token);
      console.log("Fetched Ride:", ride);
      setLookedUpRide(ride);
    } catch (e) {
      setLookupError(e instanceof ApiError ? e.message : "Could not reach Ride Service.");
    }
  }

  async function completeRide() {
    if (!session || !rideId) return;
    setCompleteError(null);
    setCompleting(true);
    try {
      const distance = Number(lookedUpRide?.distance || 0);
      const duration = Number(lookedUpRide?.duration || 0);
      const ride = await rideApi.complete(rideId, { distanceKm: distance, durationMin: duration }, session.token);
      setCompletedRide(ride);
      setLookedUpRide(ride);
    } catch (e) {
      setCompleteError(e instanceof ApiError ? e.message : "Could not reach Ride Service.");
    } finally {
      setCompleting(false);
    }
  }

  if (!ready || !session) return null;

  // Mock daily ride history since the backend does not currently provide an endpoint for this.
  const mockDailyRides = [
    { id: 101, status: "COMPLETED", fare: 450, distance: 3.2 },
    { id: 102, status: "COMPLETED", fare: 820, distance: 7.1 },
    { id: 103, status: "CANCELLED", fare: 0, distance: 2.0 },
    { id: 104, status: "COMPLETED", fare: 300, distance: 1.5 },
  ];

  // Logic to calculate stats
  const completedRides = mockDailyRides.filter((ride) => ride.status === "COMPLETED");
  const totalEarnings = completedRides.reduce((sum, ride) => sum + (ride.fare || 0), 0);
  const dailyGoal = 10;
  const completedCount = completedRides.length;
  const progressPercentage = Math.min((completedCount / dailyGoal) * 100, 100);

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Driver Service &middot; 8082" />

      {/* Driver Stats & Daily Earnings Section */}
      <div style={{ padding: "28px 32px 0", maxWidth: 1200, margin: "0 auto" }}>
        <div style={{ 
          background: "linear-gradient(135deg, #FFC400 0%, #F5B800 100%)", 
          borderRadius: 16, 
          padding: "28px 32px", 
          boxShadow: "0 10px 25px rgba(255, 196, 0, 0.2)",
          display: "flex",
          flexWrap: "wrap",
          gap: 32,
          alignItems: "center",
          justifyContent: "space-between",
          color: "#111111"
        }}>
          <div>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, opacity: 0.8, marginBottom: 8, textTransform: "uppercase", letterSpacing: 0.5 }}>Daily Earnings</div>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 44, fontWeight: 800, letterSpacing: -1, lineHeight: 1 }}>LKR {totalEarnings.toFixed(2)}</div>
            <div style={{ fontSize: 14.5, opacity: 0.85, marginTop: 10, fontWeight: 500 }}>from {completedCount} completed rides</div>
          </div>
          
          <div style={{ display: "flex", alignItems: "center", gap: 24, background: "rgba(255, 255, 255, 0.15)", padding: "16px 24px", borderRadius: 12 }}>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 700 }}>Daily Goal</div>
              <div style={{ fontSize: 14, opacity: 0.9 }}>{completedCount} / {dailyGoal} Rides</div>
            </div>
            
            {/* SVG Circular Progress Chart */}
            <div style={{ position: "relative", width: 72, height: 72 }}>
              <svg width="72" height="72" viewBox="0 0 100 100" style={{ transform: "rotate(-90deg)" }}>
                <circle cx="50" cy="50" r="40" fill="none" stroke="rgba(17, 17, 17, 0.15)" strokeWidth="10" />
                <circle 
                  cx="50" cy="50" r="40" 
                  fill="none" 
                  stroke="#111111" 
                  strokeWidth="10" 
                  strokeDasharray={`${2 * Math.PI * 40}`} 
                  strokeDashoffset={`${2 * Math.PI * 40 * (1 - progressPercentage / 100)}`} 
                  strokeLinecap="round" 
                  style={{ transition: "stroke-dashoffset 1s ease-in-out" }}
                />
              </svg>
              <div style={{ position: "absolute", top: 0, left: 0, right: 0, bottom: 0, display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 700 }}>
                {Math.round(progressPercentage)}%
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px", maxWidth: 1200, margin: "0 auto" }}>
        <Card>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 18 }}>Your driver profile</div>

          <Field
            label="Driver Service profile id"
            value={driverProfileId}
            onChange={(e) => setDriverProfileId(e.target.value)}
            onBlur={saveDriverProfileId}
            placeholder="e.g. 3"
          />
          <p style={{ marginTop: -6, marginBottom: 18, fontSize: 12, color: "var(--muted)" }}>
            Account Service and Driver Service don&apos;t share an id today, so this is the id
            Driver Service gave your vehicle profile - shown right after you registered, or ask
            whoever created it.
          </p>

          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14 }}>
            <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>{available ? "Online" : "Offline"}</span>
            <button
              type="button"
              onClick={toggleOnline}
              disabled={toggling}
              aria-pressed={available}
              style={{
                width: 50,
                height: 28,
                borderRadius: 999,
                border: `1px solid ${available ? "var(--accent)" : "var(--line)"}`,
                background: available ? "var(--accent)" : "transparent",
                cursor: "pointer",
                padding: 3,
                display: "flex",
                justifyContent: available ? "flex-end" : "flex-start",
              }}
            >
              <span style={{ width: 20, height: 20, borderRadius: "50%", background: available ? "#14161a" : "#6b7280", display: "block" }} />
            </button>
          </div>
          <ErrorBanner message={togglingError} />
        </Card>

        <Card>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 6 }}>Complete a ride</div>
          <InfoBanner>
            Ride Service has no &quot;rides assigned to me&quot; endpoint yet, so there is no live
            queue here - enter the ride id a passenger (or the assignment flow) gave you.
          </InfoBanner>
          <div style={{ height: 16 }} />

          <Field label="Ride id" value={rideId} onChange={(e) => setRideId(e.target.value)} placeholder="e.g. 1" />

          <button
            type="button"
            onClick={lookupRide}
            disabled={!rideId}
            style={{
              width: "100%",
              marginBottom: 14,
              fontFamily: "var(--font-heading)",
              fontSize: 13.5,
              fontWeight: 600,
              background: "transparent",
              border: "1px solid var(--line)",
              borderRadius: 8,
              padding: 12,
              cursor: "pointer",
              color: "var(--text)",
            }}
          >
            Look up ride
          </button>
          <ErrorBanner message={lookupError} />

          {lookedUpRide && (
            <div style={{ borderTop: "1px solid var(--line)", paddingTop: 14, marginTop: 4, marginBottom: 18 }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
                <span style={{ fontSize: 13.5 }}>
                  {lookedUpRide.pickup} &rarr; {lookedUpRide.destination}
                </span>
                <StatusBadge status={lookedUpRide.status} />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, marginBottom: 14 }}>
                <Field label="Distance (km)" type="number" step="0.1" value={lookedUpRide.distance ?? ""} disabled onChange={(e) => setLookedUpRide({ ...lookedUpRide, distance: e.target.value })} />
                <Field label="Duration (min)" type="number" step="1" value={lookedUpRide.duration ?? ""} disabled onChange={(e) => setLookedUpRide({ ...lookedUpRide, duration: e.target.value })} />
                <Field label="Fare (LKR)" type="number" value={lookedUpRide.fare ?? ""} disabled onChange={(e) => setLookedUpRide({ ...lookedUpRide, fare: e.target.value })} />
              </div>

              <PrimaryButton onClick={completeRide} disabled={completing || lookedUpRide.status === "COMPLETED"}>
                {completing ? "Completing..." : lookedUpRide.status === "COMPLETED" ? "Already completed" : "Complete ride"}
              </PrimaryButton>
              <ErrorBanner message={completeError} />

              {completedRide && (
                <p style={{ marginTop: 12, fontSize: 13, color: "var(--muted)" }}>
                  Marked COMPLETED and a real payment was recorded in Fare &amp; Payment Service.
                </p>
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
