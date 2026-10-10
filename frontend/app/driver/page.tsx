"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import NavBar from "@/components/NavBar";
import { Field, PrimaryButton, Card, ErrorBanner, InfoBanner, StatusBadge } from "@/components/ui";
import { driverApi, rideApi, ApiError, errorMessage, TERMINAL_STATUSES, type Ride } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDisplayId } from "@/lib/format";
import { formatMoney } from "@/lib/money";

export default function DriverPage() {
  return (
    <Suspense fallback={null}>
      <DriverDashboard />
    </Suspense>
  );
}

// The one button shown per status - only transitions ride-service allows
// for the assigned driver (cancelled/completed rides get none).
const NEXT_ACTION: Record<string, { label: string; action: "accept" | "start" | "complete" } | null> = {
  REQUESTED: null,
  ASSIGNED: { label: "Accept ride", action: "accept" },
  ACCEPTED: { label: "Start ride", action: "start" },
  IN_PROGRESS: { label: "Complete ride", action: "complete" },
};

function DriverDashboard() {
  const { session, ready, updateSession } = useAuth();

  const [driverProfileId, setDriverProfileId] = useState<string>("");
  const [available, setAvailable] = useState(false);
  const [togglingError, setTogglingError] = useState<string | null>(null);
  const [toggling, setToggling] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [driverRides, setDriverRides] = useState<Ride[]>([]);
  const [actionBusy, setActionBusy] = useState<string | null>(null); // rideId being acted on
  const [actionError, setActionError] = useState<string | null>(null);
  // Trip figures entered when completing, per ride (pre-filled from the request).
  const [tripInputs, setTripInputs] = useState<Record<string, { distanceKm: string; durationMin: string }>>({});

  // Location / service area
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [serviceArea, setServiceArea] = useState("");
  const [locationBusy, setLocationBusy] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [locationOk, setLocationOk] = useState(false);
  const [saBusy, setSaBusy] = useState(false);
  const [saError, setSaError] = useState<string | null>(null);
  const [saOk, setSaOk] = useState(false);

  // Stats from real ride data - the final fare once known, else the estimate.
  const completedRides = driverRides.filter(r => r.status === "COMPLETED");
  const dailyEarnings = completedRides.reduce((s, r) => s + rideFare(r), 0);
  const completedCount = completedRides.length;
  const dailyGoal = 10;
  const progress = Math.min((completedCount / dailyGoal) * 100, 100);

  const profileIdForRides = session?.driverProfileId;

  // Rides and the online flag together: reserving a driver for a ride takes
  // them offline and completing/cancelling it puts them back online, both on
  // the server, so the toggle has to follow along.
  const refreshRides = useCallback(async () => {
    if (!profileIdForRides) return;
    try {
      const [rides, profile] = await Promise.all([rideApi.listByDriver(profileIdForRides), driverApi.getMyProfile()]);
      setDriverRides(rides);
      setAvailable(profile.available);
    } catch {
      // Polling errors are transient; the next tick retries.
    }
  }, [profileIdForRides]);

  // Load driver profile once
  useEffect(() => {
    if (!ready || !session || session.role !== "DRIVER") return;
    driverApi.getMyProfile(session.token)
      .then((profile) => {
        setProfileError(null);
        setDriverProfileId(String(profile.id));
        setAvailable(profile.available);
        setServiceArea(profile.serviceArea ?? "");
        if (profile.currentLat != null) setLat(String(profile.currentLat));
        if (profile.currentLng != null) setLng(String(profile.currentLng));
        if (session.driverProfileId !== profile.id) {
          updateSession({ driverProfileId: profile.id });
        }
      })
      .catch((e) => {
        if (e instanceof ApiError && e.code === "DRIVER_NOT_FOUND") {
          setProfileError("No vehicle profile found for this account. Log out and register a new driver account.");
        } else {
          setProfileError(errorMessage(e, "We couldn't load your driver profile. Please try again."));
        }
      });
  }, [ready, session?.id, session?.token]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load now, then poll rides every 4 s
  useEffect(() => {
    if (!profileIdForRides) return;
    const first = setTimeout(refreshRides, 0);
    const interval = setInterval(refreshRides, 4000);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
    };
  }, [profileIdForRides, refreshRides]);

  async function toggleOnline() {
    if (!session || !driverProfileId) return;
    setTogglingError(null);
    setToggling(true);
    try {
      const driver = await driverApi.setAvailability(driverProfileId, !available, session.token);
      setAvailable(driver.available);
    } catch (e) {
      setTogglingError(errorMessage(e, "We couldn't change your availability. Please try again."));
    } finally {
      setToggling(false);
    }
  }

  function tripFor(ride: Ride) {
    return tripInputs[ride.id] ?? {
      distanceKm: String(ride.distanceKm ?? ride.distance ?? ""),
      durationMin: String(ride.durationMin ?? ride.duration ?? ""),
    };
  }

  function setTrip(ride: Ride, patch: Partial<{ distanceKm: string; durationMin: string }>) {
    setTripInputs(prev => ({ ...prev, [ride.id]: { ...tripFor(ride), ...patch } }));
  }

  async function doRideAction(ride: Ride, action: "accept" | "start" | "complete") {
    setActionError(null);
    if (action === "complete") {
      const trip = tripFor(ride);
      if (!(Number(trip.distanceKm) > 0) || !(Number(trip.durationMin) > 0)) {
        setActionError("Enter the trip's distance and duration (both more than 0) to complete the ride.");
        return;
      }
    }
    setActionBusy(ride.id);
    try {
      if (action === "accept") await rideApi.accept(ride.id);
      else if (action === "start") await rideApi.start(ride.id);
      else {
        const trip = tripFor(ride);
        await rideApi.complete(ride.id, { distanceKm: Number(trip.distanceKm), durationMin: Number(trip.durationMin) });
      }
    } catch (e) {
      setActionError(errorMessage(e, "That didn't work. Please try again."));
    } finally {
      // Refresh either way so the card shows the ride's real status.
      await refreshRides();
      setActionBusy(null);
    }
  }

  async function updateLocation() {
    if (!driverProfileId || !lat || !lng) return;
    setLocationError(null);
    setLocationOk(false);
    const latNum = Number(lat);
    const lngNum = Number(lng);
    if (!(latNum >= -90 && latNum <= 90) || !(lngNum >= -180 && lngNum <= 180)) {
      setLocationError("Latitude must be between -90 and 90, and longitude between -180 and 180.");
      return;
    }
    setLocationBusy(true);
    try {
      await driverApi.updateLocation(driverProfileId, { lat: latNum, lng: lngNum });
      setLocationOk(true);
    } catch (e) {
      setLocationError(errorMessage(e, "We couldn't update your location. Please try again."));
    } finally {
      setLocationBusy(false);
    }
  }

  async function updateServiceArea() {
    if (!driverProfileId || !serviceArea.trim()) return;
    setSaBusy(true);
    setSaError(null);
    setSaOk(false);
    try {
      await driverApi.updateServiceArea(driverProfileId, serviceArea.trim());
      setSaOk(true);
    } catch (e) {
      setSaError(errorMessage(e, "We couldn't update your service area. Please try again."));
    } finally {
      setSaBusy(false);
    }
  }

  if (!ready || !session) return null;

  const activeRides = driverRides.filter(r => !TERMINAL_STATUSES.has(r.status));
  const historyRides = driverRides.filter(r => TERMINAL_STATUSES.has(r.status)).slice(0, 10);

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Driver Service · 8082" />

      {profileError && (
        <div style={{ padding: "28px 32px 0", maxWidth: 1200, margin: "0 auto" }}>
          <ErrorBanner message={profileError} />
        </div>
      )}

      {/* Stats banner */}
      <div style={{ padding: "28px 32px 0", maxWidth: 1200, margin: "0 auto", display: profileError ? "none" : "block" }}>
        <div style={{
          background: "linear-gradient(135deg, #FFC400 0%, #F5B800 100%)",
          borderRadius: 16, padding: "28px 32px",
          boxShadow: "0 10px 25px rgba(255, 196, 0, 0.2)",
          display: "flex", flexWrap: "wrap", gap: 32,
          alignItems: "center", justifyContent: "space-between", color: "#111",
        }}>
          <div>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, opacity: 0.8, marginBottom: 8, textTransform: "uppercase", letterSpacing: 0.5 }}>Daily Earnings</div>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 44, fontWeight: 800, letterSpacing: -1, lineHeight: 1 }}>{formatMoney(dailyEarnings)}</div>
            <div style={{ fontSize: 14.5, opacity: 0.85, marginTop: 10 }}>from {completedCount} completed rides</div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 24, background: "rgba(255,255,255,.15)", padding: "16px 24px", borderRadius: 12 }}>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 700 }}>Daily Goal</div>
              <div style={{ fontSize: 14, opacity: 0.9 }}>{completedCount} / {dailyGoal} Rides</div>
            </div>
            <div style={{ position: "relative", width: 72, height: 72 }}>
              <svg width="72" height="72" viewBox="0 0 100 100" style={{ transform: "rotate(-90deg)" }}>
                <circle cx="50" cy="50" r="40" fill="none" stroke="rgba(17,17,17,.15)" strokeWidth="10" />
                <circle cx="50" cy="50" r="40" fill="none" stroke="#111" strokeWidth="10"
                  strokeDasharray={`${2 * Math.PI * 40}`}
                  strokeDashoffset={`${2 * Math.PI * 40 * (1 - progress / 100)}`}
                  strokeLinecap="round" style={{ transition: "stroke-dashoffset 1s ease-in-out" }}
                />
              </svg>
              <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-heading)", fontSize: 16, fontWeight: 700 }}>
                {Math.round(progress)}%
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: profileError ? "none" : "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px", maxWidth: 1200, margin: "0 auto" }}>
        {/* Profile card */}
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 18 }}>Your driver profile</div>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 13, color: "var(--muted)", marginBottom: 18 }}>
              Account <strong>{formatDisplayId(session.id)}</strong> · Driver <strong>{formatDisplayId(driverProfileId)}</strong>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14 }}>
              <span style={{ fontFamily: "var(--font-heading)", fontSize: 13, fontWeight: 600 }}>{available ? "Online" : "Offline"}</span>
              <button
                type="button"
                onClick={toggleOnline}
                disabled={toggling}
                aria-pressed={available}
                style={{
                  width: 50, height: 28, borderRadius: 999,
                  border: `1px solid ${available ? "var(--accent)" : "var(--line)"}`,
                  background: available ? "var(--accent)" : "transparent",
                  cursor: "pointer", padding: 3,
                  display: "flex", justifyContent: available ? "flex-end" : "flex-start",
                }}
              >
                <span style={{ width: 20, height: 20, borderRadius: "50%", background: available ? "#14161a" : "#6b7280", display: "block" }} />
              </button>
            </div>
            <ErrorBanner message={togglingError} />
          </Card>

          {/* Location update */}
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 14, fontWeight: 600, marginBottom: 14 }}>Update Location</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <Field label="Latitude" type="number" step="any" value={lat} onChange={e => setLat(e.target.value)} placeholder="6.9271" />
              <Field label="Longitude" type="number" step="any" value={lng} onChange={e => setLng(e.target.value)} placeholder="79.8612" />
            </div>
            <PrimaryButton onClick={updateLocation} disabled={locationBusy || !lat || !lng}>
              {locationBusy ? "Saving…" : "Update location"}
            </PrimaryButton>
            <ErrorBanner message={locationError} />
            {locationOk && <p style={{ marginTop: 10, fontSize: 12, color: "#3ddc84" }}>✓ Location updated.</p>}
          </Card>

          {/* Service area update */}
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 14, fontWeight: 600, marginBottom: 14 }}>Update Service Area</div>
            <Field label="Service area" value={serviceArea} onChange={e => setServiceArea(e.target.value)} placeholder="Colombo" />
            <PrimaryButton onClick={updateServiceArea} disabled={saBusy || !serviceArea.trim()}>
              {saBusy ? "Saving…" : "Update service area"}
            </PrimaryButton>
            <ErrorBanner message={saError} />
            {saOk && <p style={{ marginTop: 10, fontSize: 12, color: "#3ddc84" }}>✓ Service area updated.</p>}
          </Card>
        </div>

        {/* Active rides */}
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>
              Active Rides ({activeRides.length})
            </div>
            <ErrorBanner message={actionError} />
            {activeRides.length === 0 ? (
              <InfoBanner>No active rides assigned to you. Go online to receive requests.</InfoBanner>
            ) : (
              <div style={{ display: "grid", gap: 12 }}>
                {activeRides.map(ride => {
                  const next = NEXT_ACTION[ride.status] ?? null;
                  const isBusy = actionBusy === ride.id;
                  return (
                    <div key={ride.id} style={{ border: "1px solid var(--line)", borderRadius: 10, padding: 14 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                        <span style={{ fontSize: 13.5, fontWeight: 600 }}>Ride {formatDisplayId(ride.id)}</span>
                        <StatusBadge status={ride.status} />
                      </div>
                      <div style={{ fontSize: 13, color: "var(--muted)", marginBottom: 10 }}>
                        {ride.pickup} → {ride.destination}
                      </div>
                      {(ride.distanceKm ?? ride.distance) != null && (
                        <div style={{ fontSize: 12, color: "var(--muted)", marginBottom: 10 }}>
                          {ride.distanceKm ?? ride.distance} km · {ride.durationMin ?? ride.duration} min
                          {rideFare(ride) > 0 && <> · est. {formatMoney(rideFare(ride))}</>}
                        </div>
                      )}
                      {next?.action === "complete" && (
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                          <Field label="Distance (km)" type="number" step="0.1" min="0" value={tripFor(ride).distanceKm} onChange={e => setTrip(ride, { distanceKm: e.target.value })} />
                          <Field label="Duration (min)" type="number" step="1" min="0" value={tripFor(ride).durationMin} onChange={e => setTrip(ride, { durationMin: e.target.value })} />
                        </div>
                      )}
                      {next && (
                        <PrimaryButton
                          onClick={() => doRideAction(ride, next.action)}
                          disabled={isBusy}
                          style={{ fontSize: 13, padding: "10px 14px" }}
                        >
                          {isBusy ? "Please wait…" : next.label}
                        </PrimaryButton>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </Card>

          {/* History */}
          <Card>
            <div style={{ fontFamily: "var(--font-heading)", fontSize: 15, fontWeight: 600, marginBottom: 14 }}>History</div>
            {historyRides.length === 0 ? (
              <InfoBanner>No completed rides yet.</InfoBanner>
            ) : (
              <div style={{ display: "grid", gap: 8 }}>
                {historyRides.map(ride => (
                  <div key={ride.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderBottom: "1px solid var(--line)" }}>
                    <span style={{ fontSize: 13 }}>{ride.pickup} → {ride.destination}</span>
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      {ride.status === "COMPLETED" && <span style={{ fontSize: 12, fontWeight: 600 }}>{formatMoney(rideFare(ride))}</span>}
                      <StatusBadge status={ride.status} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

// What a ride earned: the final fare once completed, else the estimate.
function rideFare(ride: Ride): number {
  return Number(ride.finalFare ?? ride.estimatedFare ?? ride.fare ?? 0);
}
