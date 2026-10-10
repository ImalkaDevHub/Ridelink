// Typed clients for the four RideLink backend services. Every call hits a
// real endpoint - there is no mock data here.

export * from "./types";
export { ApiError, errorMessage, getToken, setToken, clearToken, decodeTokenPayload, TOKEN_COOKIE } from "./client";
export { friendlyMessage } from "./errors";
export { accountApi } from "./account";
export { driverApi, type DriverProfileInput } from "./driver";
export { rideApi, CANCELLABLE_STATUSES, TERMINAL_STATUSES, type RideRequestInput } from "./ride";
export { fareApi, type FareEstimateInput } from "./fare";
