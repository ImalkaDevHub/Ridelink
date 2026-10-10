import { FARE_API, request } from "./client";
import type { FareEstimateResponse, FareRecord, PaymentMethod, PaymentResponse, ReceiptResponse } from "./types";

export interface FareEstimateInput {
  pickupLocation: string;
  destinationLocation: string;
  vehicleType: string;
  // Optional: without distanceKm the service uses coordinates or known place names;
  // without durationMin it estimates the duration from the distance.
  distanceKm?: number;
  durationMin?: number;
  pickupLat?: number;
  pickupLng?: number;
  destinationLat?: number;
  destinationLng?: number;
}

// fare-payment-service (8084)
export const fareApi = {
  // Public.
  estimate: (body: FareEstimateInput) =>
    request<FareEstimateResponse>(`${FARE_API}/api/fares/estimate`, { method: "POST", body: JSON.stringify(body) }),

  // DRIVER or ADMIN (normally done by ride-service on completion).
  finalize: (body: { rideId: string; distanceKm: number; durationMin: number; vehicleType: string }) =>
    request<FareRecord>(`${FARE_API}/api/fares/final`, { method: "POST", body: JSON.stringify(body) }),

  // PASSENGER only; the amount always comes from the stored final fare.
  pay: (body: { rideId: string; method: PaymentMethod; cardLast4?: string }) =>
    request<PaymentResponse>(`${FARE_API}/api/payments`, { method: "POST", body: JSON.stringify(body) }),

  payment: (paymentId: string) => request<PaymentResponse>(`${FARE_API}/api/payments/${paymentId}`),

  paymentsForRide: (rideId: string) => request<PaymentResponse[]>(`${FARE_API}/api/payments/ride/${rideId}`),

  status: (rideId: string, token?: string | null) =>
    request<PaymentResponse>(`${FARE_API}/api/payments/${rideId}/status`, { token }),

  // Payer or ADMIN. Accepts a payment id or a ride id.
  receipt: (paymentOrRideId: string, token?: string | null) =>
    request<ReceiptResponse>(`${FARE_API}/api/payments/${paymentOrRideId}/receipt`, { token }),
};
