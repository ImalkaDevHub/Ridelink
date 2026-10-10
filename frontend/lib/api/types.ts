// Shapes returned by the four backend services. Money fields are BigDecimal
// on the server and arrive as JSON numbers. Fields added to the backend
// after the first release are optional/nullable here too.

export type Role = "PASSENGER" | "DRIVER" | "ADMIN";
export type AccountStatus = "ACTIVE" | "SUSPENDED";

export type RideStatus = "REQUESTED" | "ASSIGNED" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type PaymentStatus = "PENDING" | "PAID" | "COMPLETED" | "FAILED";
export type PaymentMethod = "CARD" | "CASH";

// ---- account-service ------------------------------------------------------

export interface AuthResponse {
  token: string;
  id: string;
  name: string;
  role: Role;
}

export interface AccountResponse {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

// Kept for code written against the earlier client.
export type SessionResponse = AccountResponse;

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- driver-service -------------------------------------------------------

export interface Driver {
  id: string;
  accountId?: string | null;
  name: string;
  email?: string | null;
  vehicleNumber: string;
  vehicleType: string;
  vehicleModel?: string | null;
  vehicleColor?: string | null;
  seats?: number | null;
  available: boolean;
  serviceArea: string;
  currentLat?: number | null;
  currentLng?: number | null;
}

// ---- ride-service ---------------------------------------------------------

export interface Ride {
  id: string;
  passengerName: string;
  passengerId: string | null;
  pickup: string;
  destination: string;
  vehicleType?: string | null;
  pickupLat?: number | null;
  pickupLng?: number | null;
  destinationLat?: number | null;
  destinationLng?: number | null;
  driverId: string | null;
  driverAccountId?: string | null;
  status: RideStatus;
  // Client-supplied trip details from the original API.
  distance?: number | null;
  duration?: number | null;
  fare?: number | null;
  // Server-side figures.
  estimatedFare?: number | null;
  finalFare?: number | null;
  distanceKm?: number | null;
  durationMin?: number | null;
  requestedAt?: string | null;
  acceptedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  cancelledAt?: string | null;
  cancelReason?: string | null;
}

// ---- fare-payment-service -------------------------------------------------

export interface FareEstimateResponse {
  pickupLocation: string;
  destinationLocation: string;
  distanceKm: number;
  durationMin: number;
  baseFare: number;
  perKmRate: number;
  perMinRate: number;
  estimatedFare: number;
  vehicleType?: string;
  subtotal?: number;
  minimumFare?: number;
  minimumApplied?: boolean;
  vehicleMultiplier?: number;
  currency?: string;
  distanceSource?: "PROVIDED" | "COORDINATES" | "PLACE_TABLE";
  durationEstimated?: boolean;
  breakdown?: string;
}

export interface FareRecord {
  id: string;
  rideId: string;
  passengerId: string | null;
  vehicleType: string;
  distanceKm: number;
  durationMin: number;
  baseFare: number;
  perKmRate: number;
  perMinRate: number;
  distanceCharge: number;
  timeCharge: number;
  subtotal: number;
  minimumFare: number;
  minimumApplied: boolean;
  multiplier: number;
  totalFare: number;
  currency: string;
  breakdown: string;
  createdAt: string;
}

export interface PaymentResponse {
  id: string;
  rideId: string;
  finalFare: number;
  status: PaymentStatus;
  paymentMethod: string;
  createdAt: string;
  completedAt: string | null;
  method?: PaymentMethod | null;
  cardLast4?: string | null;
  currency?: string | null;
  passengerId?: string | null;
  payerId?: string | null;
  receiptNumber?: string | null;
  failureReason?: string | null;
}

export interface ReceiptResponse {
  rideId: string;
  passengerId: string | null;
  finalFare: number;
  distanceKm: number;
  durationMin: number;
  paymentMethod: string;
  status: PaymentStatus;
  completedAt: string | null;
  breakdown: string;
  receiptNumber?: string;
  paymentId?: string;
  method?: string;
  currency?: string;
  timestamp?: string;
  fareBreakdown?: FareRecord | null;
}
