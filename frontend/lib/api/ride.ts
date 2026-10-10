import { RIDE_API, request } from "./client";
import type { Ride, RideStatus } from "./types";

export interface RideRequestInput {
  passengerName: string;
  pickup: string;
  destination: string;
  vehicleType: string;
  distanceKm?: number;
  durationMin?: number;
  // Original fields, still accepted by the backend.
  distance?: number;
  duration?: number;
  fare?: number;
}

// Which actions each status allows - mirrors ride-service's transition map.
export const CANCELLABLE_STATUSES: ReadonlySet<RideStatus> = new Set(["REQUESTED", "ASSIGNED", "ACCEPTED"]);
export const TERMINAL_STATUSES: ReadonlySet<RideStatus> = new Set(["COMPLETED", "CANCELLED"]);

// ride-service (8083)
export const rideApi = {
  // PASSENGER only; passengerId comes from the token.
  request: (body: RideRequestInput, token?: string | null) =>
    request<Ride>(`${RIDE_API}/api/rides`, { method: "POST", body: JSON.stringify(body), token }),

  get: (id: string, token?: string | null) => request<Ride>(`${RIDE_API}/api/rides/${id}`, { token }),

  // The caller's own rides (passengers: as passenger, drivers: assigned to them).
  mine: (status?: RideStatus) => request<Ride[]>(`${RIDE_API}/api/rides${status ? `?status=${status}` : ""}`),

  listByPassenger: (passengerId: string, token?: string | null) =>
    request<Ride[]>(`${RIDE_API}/api/rides?passengerId=${encodeURIComponent(passengerId)}`, { token }),

  listByDriver: (driverId: string, token?: string | null) =>
    request<Ride[]>(`${RIDE_API}/api/rides?driverId=${encodeURIComponent(driverId)}`, { token }),

  // Assigned driver only.
  accept: (id: string) => request<Ride>(`${RIDE_API}/api/rides/${id}/accept`, { method: "PATCH" }),

  start: (id: string) => request<Ride>(`${RIDE_API}/api/rides/${id}/start`, { method: "PATCH" }),

  complete: (id: string, body: { distanceKm: number; durationMin: number }, token?: string | null) =>
    request<Ride>(`${RIDE_API}/api/rides/${id}/complete`, { method: "PATCH", body: JSON.stringify(body), token }),

  // Passenger, assigned driver or ADMIN, before the ride is IN_PROGRESS.
  cancel: (id: string, body?: { reason?: string }) =>
    request<Ride>(`${RIDE_API}/api/rides/${id}/cancel`, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    }),
};
