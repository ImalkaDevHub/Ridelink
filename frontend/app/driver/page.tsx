"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge } from "@/components/ui";
import { driverApi, rideApi, ApiError, type Ride } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDisplayId } from "@/lib/format";

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
  
  const [driverRides, setDriverRides] = useState<Ride[]>([]);
  const [dailyEarnings, setDailyEarnings] = useState(0);
  const [completedRidesCount, setCompletedRidesCount] = useState(0);
  const [profileError, setProfileError] = useState<string | null>(null);

  useEffect(() => {
    if (ready && (!session || session.role !== "DRIVER")) router.push("/login");
  }, [ready, session, router]);

  useEffect(() => {
    if (!session?.driverProfileId || !session?.token) {
      setDriverRides([]);
      setDailyEarnings(0);
      setCompletedRidesCount(0);
      return;
    }
    rideApi.listByDriver(session.driverProfileId, session.token)
      .then((rides) => {
        setDriverRides(rides);
        const completed = rides.filter((r) => r.status === "COMPLETED");
        setCompletedRidesCount(completed.length);
        setDailyEarnings(completed.reduce((sum, r) => sum + Number(r.fare || 0), 0));
      })
      .catch(() => {
        setDriverRides([]);
        setDailyEarnings(0);
        setCompletedRidesCount(0);
      });
  }, [session?.driverProfileId, session?.token, completedRide]);

  useEffect(() => {
    if (!ready || !session || session.role !== "DRIVER") return;
    
    // Auto-fetch the driver profile linked to this account
    driverApi.getMyProfile(session.token)
      .then((profile) => {
        setProfileError(null);
        setDriverProfileId(String(profile.id));
        setAvailable(profile.available);
        if (session.driverProfileId !== profile.id) {
          updateSession({ driverProfileId: profile.id });
        }
      })
      .catch((e) => {
        if (e instanceof ApiError && e.code === "DRIVER_NOT_FOUND") {
          setProfileError("No vehicle profile is linked to this account (likely because it was created before the recent update). Please log out and register a completely new driver account.");
        } else {
          setProfileError(e instanceof ApiError ? e.message : "Could not fetch driver profile. Ensure the Driver Service backend is running.");
        }
      });
  }, [ready, session?.id, session?.token, updateSession]);

  async function toggleOnline() {
    if (!session || !session.token) return;
    const id = driverProfileId;
    if (!id) {
      setTogglingError("Could not determine your Driver Profile ID. Please try refreshing.");
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

  // Logic to calculate stats from real backend data
  const dailyGoal = 10;
  const progressPercentage = Math.min((completedRidesCount / dailyGoal) * 100, 100);

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Driver Service &middot; 8082" />

      {profileError && (
        <div style={{ padding: "28px 32px 0", maxWidth: 1200, margin: "0 auto" }}>
          <ErrorBanner message={profileError} />
        </div>
      )}

      {/* Driver Stats & Daily Earnings Section */}
      <div style={{ padding: "28px 32px 0", maxWidth: 1200, margin: "0 auto", display: profileError ? "none" : "block" }}>
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
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 44, fontWeight: 800, letterSpacing: -1, lineHeight: 1 }}>LKR {dailyEarnings.toFixed(2)}</div>
            <div style={{ fontSize: 14.5, opacity: 0.85, marginTop: 10, fontWeight: 500 }}>from {completedRidesCount} completed rides</div>
          </div>
          
          <div style={{ display: "flex", alignItems: "center", gap: 24, background: "rgba(255, 255, 255, 0.15)", padding: "16px 24px", borderRadius: 12 }}>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 700 }}>Daily Goal</div>
              <div style={{ fontSize: 14, opacity: 0.9 }}>{completedRidesCount} / {dailyGoal} Rides</div>
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

      <div style={{ display: profileError ? "none" : "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px", maxWidth: 1200, margin: "0 auto" }}>
        <Card>
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 18 }}>Your driver profile</div>

          <div style={{ fontFamily: "var(--font-heading)", fontSize: 13, color: "var(--muted)", marginBottom: 18 }}>
            Automatically linked to Account {formatDisplayId(session?.id)}. Your Driver Profile ID is <strong>{formatDisplayId(driverProfileId)}</strong>.
          </div>

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
          <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>Live Queue (Assigned Rides)</div>
          
          {driverRides.filter(r => r.status === "ASSIGNED" || r.status === "IN_PROGRESS").length === 0 ? (
            <InfoBanner>No active rides assigned to you at the moment. Waiting for requests...</InfoBanner>
          ) : (
            <div style={{ display: "grid", gap: 12 }}>
              {driverRides
                .filter(r => r.status === "ASSIGNED" || r.status === "IN_PROGRESS")
                .map(ride => (
                  <div key={ride.id} style={{ border: `1px solid ${lookedUpRide?.id === ride.id ? "var(--accent)" : "var(--line)"}`, borderRadius: 8, padding: 14, background: lookedUpRide?.id === ride.id ? "rgba(255, 196, 0, 0.03)" : "transparent", transition: "all 0.2s" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
                      <span style={{ fontSize: 13.5, fontWeight: 600 }}>Ride {formatDisplayId(ride.id)}</span>
                      <StatusBadge status={ride.status} />
                    </div>
                    <div style={{ fontSize: 13, color: "var(--muted)", marginBottom: 12 }}>
                      {ride.pickup} &rarr; {ride.destination}
                    </div>
                    <button
                      type="button"
                      onClick={() => { setRideId(String(ride.id)); setLookedUpRide(ride); setCompleteError(null); setCompletedRide(null); }}
                      style={{
                        width: "100%",
                        fontFamily: "var(--font-heading)",
                        fontSize: 12.5,
                        fontWeight: 600,
                        background: lookedUpRide?.id === ride.id ? "var(--accent)" : "transparent",
                        border: `1px solid ${lookedUpRide?.id === ride.id ? "var(--accent)" : "var(--line)"}`,
                        borderRadius: 6,
                        padding: "8px",
                        cursor: "pointer",
                        color: lookedUpRide?.id === ride.id ? "#111" : "var(--text)",
                        transition: "all 0.2s"
                      }}
                    >
                      {lookedUpRide?.id === ride.id ? "Currently Viewing" : "View / Complete"}
                    </button>
                  </div>
                ))}
            </div>
          )}

          {lookedUpRide && (
            <div style={{ borderTop: "1px solid var(--line)", paddingTop: 18, marginTop: 18 }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 14, fontWeight: 600, marginBottom: 12 }}>
                Completing Ride {formatDisplayId(lookedUpRide.id)}
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, marginBottom: 14 }}>
                <Field label="Distance (km)" type="number" step="0.1" value={lookedUpRide.distance ?? ""} disabled onChange={() => {}} />
                <Field label="Duration (min)" type="number" step="1" value={lookedUpRide.duration ?? ""} disabled onChange={() => {}} />
                <Field label="Fare (LKR)" type="number" value={lookedUpRide.fare ?? ""} disabled onChange={() => {}} />
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
