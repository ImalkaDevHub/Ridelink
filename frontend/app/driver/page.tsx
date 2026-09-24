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
  const [distanceKm, setDistanceKm] = useState("");
  const [durationMin, setDurationMin] = useState("");
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
      const ride = await rideApi.complete(rideId, { distanceKm: Number(distanceKm), durationMin: Number(durationMin) }, session.token);
      setCompletedRide(ride);
      setLookedUpRide(ride);
    } catch (e) {
      setCompleteError(e instanceof ApiError ? e.message : "Could not reach Ride Service.");
    } finally {
      setCompleting(false);
    }
  }

  if (!ready || !session) return null;

  return (
    <div style={{ minHeight: "100vh" }}>
      <NavBar label="Driver Service &middot; 8082" />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))", gap: 24, padding: "28px 32px" }}>
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

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 14 }}>
                <Field label="Distance (km)" type="number" step="0.1" value={distanceKm} onChange={(e) => setDistanceKm(e.target.value)} />
                <Field label="Duration (min)" type="number" step="1" value={durationMin} onChange={(e) => setDurationMin(e.target.value)} />
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
