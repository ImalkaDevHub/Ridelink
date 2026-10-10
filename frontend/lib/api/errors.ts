// Calm, human wording for the stable error codes the backend returns in
// {"code", "message"}. Anything not listed falls back to the server's own
// message, which is already written for people.

const FRIENDLY_MESSAGES: Record<string, string> = {
  NO_DRIVER_AVAILABLE: "All drivers are busy right now. Please try again in a minute or two.",
  DRIVER_SERVICE_UNAVAILABLE:
    "We can't reach our driver system at the moment, so no ride was booked. Please try again shortly.",
  FARE_SERVICE_UNAVAILABLE:
    "Fares can't be calculated right now. Nothing has changed - please try again in a moment.",
  INVALID_STATUS_TRANSITION:
    "This ride has already moved on, so that step isn't available any more. The latest status is shown.",
  PAYMENT_FAILED:
    "The payment didn't go through and you haven't been charged. You can try another card or pay in cash.",
  ACCOUNT_SUSPENDED: "This account is currently suspended. Please contact support if you think this is a mistake.",
  EMAIL_ALREADY_EXISTS: "An account with this email already exists. Try signing in instead.",
  FORBIDDEN: "You don't have access to that. If you think you should, try signing in again.",
  INVALID_CREDENTIALS: "That email and password don't match. Please check them and try again.",
  WRONG_PASSWORD: "Your current password isn't right. Please try again.",
  SAME_PASSWORD: "Your new password needs to be different from your current one.",
  PAYMENT_ALREADY_EXISTS: "This ride has already been paid - there's nothing more to pay.",
  FARE_NOT_FINALIZED: "The final fare isn't ready yet. Please try again once the driver has completed the ride.",
  DISTANCE_UNRESOLVED:
    "We couldn't work out the distance for those places. Please enter the distance in km.",
  VEHICLE_ALREADY_EXISTS: "A driver with this vehicle number is already registered.",
  DRIVER_NOT_FOUND: "We couldn't find a driver profile for this account.",
  RIDE_NOT_FOUND: "We couldn't find that ride.",
  PAYMENT_NOT_FOUND: "There's no payment for this ride yet.",
  VALIDATION_ERROR: "Some details need a second look - please check the highlighted fields.",
};

export const NETWORK_ERROR_MESSAGE = "We can't reach RideLink right now. Please check your connection and try again.";

export function friendlyMessage(code: string | undefined, fallback: string): string {
  return (code && FRIENDLY_MESSAGES[code]) || fallback;
}
