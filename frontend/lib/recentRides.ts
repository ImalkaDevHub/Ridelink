// ride-service has no "list my past rides" endpoint - only get-by-id - so
// there is no real per-passenger ride history to fetch. This keeps a small
// per-browser list of ride ids the current passenger has created, purely
// as a local convenience so they don't have to remember ride numbers. It
// is NOT a substitute for a real history and the UI labels it as such.

const STORAGE_KEY = "ridelink.recentRideIds";
const MAX_ENTRIES = 10;

export function getRecentRideIds(): number[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as number[]) : [];
  } catch {
    return [];
  }
}

export function addRecentRideId(id: number): void {
  try {
    const existing = getRecentRideIds().filter((existingId) => existingId !== id);
    const next = [id, ...existing].slice(0, MAX_ENTRIES);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Best-effort only.
  }
}
