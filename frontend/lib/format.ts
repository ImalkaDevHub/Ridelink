export function formatDisplayId(id: string | number | undefined | null): string {
  if (!id) return "...";
  const idStr = String(id);
  const truncated = idStr.length > 7 ? idStr.slice(-7).toUpperCase() : idStr.toUpperCase();
  return `#${truncated}`;
}
