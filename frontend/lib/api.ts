// Thin fetch wrappers over the 4 RideLink backend services. Every call here
// hits a real, already-implemented endpoint - there is no mock data or
// fabricated response anywhere in this file. Where the backend genuinely
// has no endpoint yet (e.g. "list of rides assigned to this driver"), the
// page that would need it says so instead of inventing one client-side.

const ACCOUNT_API = process.env.NEXT_PUBLIC_ACCOUNT_API_URL ?? "http://localhost:8081";
const DRIVER_API = process.env.NEXT_PUBLIC_DRIVER_API_URL ?? "http://localhost:8082";
const RIDE_API = process.env.NEXT_PUBLIC_RIDE_API_URL ?? "http://localhost:8083";
const FARE_API = process.env.NEXT_PUBLIC_FARE_API_URL ?? "http://localhost:8084";

// Every service answers errors as {"code": "...", "message": "..."} - this
// carries both straight through to the UI instead of a generic failure.
export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request<T>(url: string, options: RequestInit = {}, token?: string | null): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (options.headers) Object.assign(headers, options.headers as Record<string, string>);
  if (token) headers["Authorization"] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(url, { ...options, headers });
  } catch {
    throw new ApiError(0, "Could not reach the service. Is it running?");
  }

  const text = await res.text();
  const body = text ? safeJson(text) : null;

  if (!res.ok) {
    const message = (body && typeof body === "object" && "message" in body ? String((body as { message: unknown }).message) : null)
      ?? `Request failed (${res.status})`;
    const code = body && typeof body === "object" && "code" in body ? String((body as { code: unknown }).code) : undefined;
    throw new ApiError(res.status, message, code);
  }

  return body as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// ---- Shapes returned by the backend (trimmed to what the UI reads) -------

export type Role = "PASSENGER" | "DRIVER" | "ADMIN";

export interface AuthResponse {
  token: string;
  id: number;
  name: string;
  role: Role;
}

export interface Driver {
  id: number;
  name: string;
  vehicleNumber: string;
  vehicleType: string;
  serviceArea: string;
  available: boolean;
}

export interface Ride {
  id: number;
  passengerName: string;
  passengerId: string | null;
  pickup: string;
  destination: string;
  driverId: number | null;
  status: string;
  distance?: number | string;
  duration?: number | string;
  fare?: number | string;
}

export interface FareEstimateResponse {
  pickupLocation: string;
  destinationLocation: string;
  distanceKm: number;
  durationMin: number;
  baseFare: number;
  perKmRate: number;
  perMinRate: number;
  estimatedFare: number;
}

export interface PaymentResponse {
  id: number;
  rideId: string;
  finalFare: number;
  status: string;
  paymentMethod: string;
  createdAt: string;
  completedAt: string | null;
}

export interface ReceiptResponse {
  rideId: string;
  passengerId: string;
  finalFare: number;
  distanceKm: number;
  durationMin: number;
  paymentMethod: string;
  status: string;
  completedAt: string | null;
  breakdown: string;
}

// ---- account-service (8081) ----------------------------------------------

export const accountApi = {
  register: (body: { name: string; email: string; password: string; role?: string }) =>
    request<AuthResponse>(`${ACCOUNT_API}/api/auth/register`, { method: "POST", body: JSON.stringify(body) }),

  login: (body: { email: string; password: string }) =>
    request<AuthResponse>(`${ACCOUNT_API}/api/auth/login`, { method: "POST", body: JSON.stringify(body) }),
};

// ---- driver-service (8082) -----------------------------------------------

export const driverApi = {
  createProfile: (
    body: { name: string; vehicleNumber: string; vehicleType: string; serviceArea: string },
    token: string
  ) => request<Driver>(`${DRIVER_API}/api/drivers`, { method: "POST", body: JSON.stringify(body) }, token),

  available: () => request<Driver[]>(`${DRIVER_API}/api/drivers/available`),

  // Callable anonymously or with a DRIVER token - see driver-service's
  // SecurityConfig. We always pass the token when we have one.
  setAvailability: (driverId: number, available: boolean, token?: string | null) =>
    request<Driver>(
      `${DRIVER_API}/api/drivers/${driverId}/availability?available=${available}`,
      { method: "PATCH" },
      token
    ),
};

// ---- ride-service (8083) -------------------------------------------------

export const rideApi = {
  request: (body: { passengerName: string; pickup: string; destination: string; distance?: number; duration?: number; fare?: number }, token: string) =>
    request<Ride>(`${RIDE_API}/api/rides`, { method: "POST", body: JSON.stringify(body) }, token),

  get: (id: number | string, token: string) => request<Ride>(`${RIDE_API}/api/rides/${id}`, {}, token),

  complete: (id: number | string, body: { distanceKm: number; durationMin: number }, token: string) =>
    request<Ride>(`${RIDE_API}/api/rides/${id}/complete`, { method: "PATCH", body: JSON.stringify(body) }, token),
};

// ---- fare-payment-service (8084) -----------------------------------------

export const fareApi = {
  estimate: (body: { pickupLocation: string; destinationLocation: string; distanceKm: number; durationMin: number }) =>
    request<FareEstimateResponse>(`${FARE_API}/api/fares/estimate`, { method: "POST", body: JSON.stringify(body) }),

  status: (rideId: number | string, token: string) =>
    request<PaymentResponse>(`${FARE_API}/api/payments/${rideId}/status`, {}, token),

  receipt: (rideId: number | string, token: string) =>
    request<ReceiptResponse>(`${FARE_API}/api/payments/${rideId}/receipt`, {}, token),
};
