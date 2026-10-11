import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Wallet from "./Wallet";
import { apiFetch } from "../api/axios";

vi.mock("../AuthContext", () => ({
  useAuth: () => ({ token: "test-token", logout: vi.fn() })
}));

vi.mock("../api/axios", () => ({
  apiFetch: vi.fn()
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Wallet transaction pagination", () => {
  it("loads and changes the displayed server page", async () => {
    apiFetch.mockImplementation(async (url) => {
      if (url === "/api/v1/wallet/me") {
        return { ok: true, status: 200, json: async () => ({ balance: 42, transactions: [] }) };
      }
      const page = Number(new URL(url, "http://localhost").searchParams.get("page"));
      const row = page === 0
        ? { id: 20, type: "DEPOSIT", amount: 10, balanceAfter: 10, timestamp: "2026-01-01T10:00:00" }
        : { id: 21, type: "REPAYMENT", amount: 5, balanceAfter: 5, timestamp: "2026-01-02T10:00:00" };
      return {
        ok: true,
        status: 200,
        json: async () => ({ content: [row], page, size: 20, totalElements: 21, totalPages: 2 })
      };
    });

    render(<MemoryRouter><Wallet /></MemoryRouter>);

    expect(await screen.findByText("Wallet deposit")).toBeTruthy();
    expect(screen.getByText("Page 1 of 2 · 21 transactions")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Loan installment paid")).toBeTruthy();
    expect(screen.getByText("Page 2 of 2 · 21 transactions")).toBeTruthy();
    await waitFor(() => expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/wallet/transactions?page=1&size=20",
      expect.objectContaining({ headers: { Authorization: "Bearer test-token" } })
    ));
  });
});
