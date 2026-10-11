const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

export function formatCurrency(amount) {
  const numericAmount = Number(amount);
  return Number.isFinite(numericAmount) ? inrFormatter.format(numericAmount) : "—";
}
