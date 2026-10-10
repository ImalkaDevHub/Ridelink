import { DRIVER_API, request } from "./client";
import type { Driver } from "./types";

export interface DriverProfileInput {
  name: string;
  email: string;
  vehicleNumber: string;
  vehicleType: string;
  serviceArea: string;
  vehicleModel?: string;
  vehicleColor?: string;
  seats?: number;
}

// driver-service (8082)
export const driverApi = {
  // DRIVER or ADMIN; the profile's accountId comes from the token.
  createProfile: (body: DriverProfileInput, token?: string | null) =>
    request<Driver>(`${DRIVER_API}/api/drivers`, { method: "POST", body: JSON.stringify(body), token }),

  getMyProfile: (token?: string | null) => request<Driver>(`${DRIVER_API}/api/drivers/me`, { token }),

  get: (id: string) => request<Driver>(`${DRIVER_API}/api/drivers/${id}`),

  // ADMIN only.
  all: () => request<Driver[]>(`${DRIVER_API}/api/drivers`),

  available: (type?: string) =>
    request<Driver[]>(`${DRIVER_API}/api/drivers/available${type ? `?type=${encodeURIComponent(type)}` : ""}`),

  // The owning driver or an ADMIN.
  setAvailability: (driverId: string, available: boolean, token?: string | null) =>
    request<Driver>(`${DRIVER_API}/api/drivers/${driverId}/availability?available=${available}`, {
      method: "PATCH",
      token,
    }),

  update: (driverId: string, body: Partial<Omit<DriverProfileInput, "serviceArea">>) =>
    request<Driver>(`${DRIVER_API}/api/drivers/${driverId}`, { method: "PATCH", body: JSON.stringify(body) }),

  updateLocation: (driverId: string, location: { lat: number; lng: number }) =>
    request<Driver>(`${DRIVER_API}/api/drivers/${driverId}/location`, {
      method: "PATCH",
      body: JSON.stringify(location),
    }),

  updateServiceArea: (driverId: string, serviceArea: string) =>
    request<Driver>(`${DRIVER_API}/api/drivers/${driverId}/service-area`, {
      method: "PATCH",
      body: JSON.stringify({ serviceArea }),
    }),
};
