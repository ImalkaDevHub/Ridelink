// Formats an amount the way the rest of the UI shows money: "LKR 1,020.00".
export function formatMoney(amount: number | string | null | undefined, currency = "LKR"): string {
  const value = Number(amount ?? 0);
  return `${currency} ${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
