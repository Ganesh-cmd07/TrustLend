import { expect, test } from "vitest";
import { formatCurrency } from "./formatCurrency.js";

test("formats rupee amounts with Indian digit grouping", () => {
  expect(formatCurrency(100000)).toBe("₹1,00,000.00");
});

test("formats numeric strings returned by the API", () => {
  expect(formatCurrency("1250.5")).toBe("₹1,250.50");
});

test("uses a placeholder for non-finite amounts", () => {
  expect(formatCurrency("not-money")).toBe("—");
});
