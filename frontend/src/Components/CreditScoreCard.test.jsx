import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import CreditScoreCard from "./CreditScoreCard";
import { apiFetch } from "../api/axios";

vi.mock("../AuthContext", () => ({ useAuth: () => ({ token: "borrower-token" }) }));
vi.mock("../api/axios", () => ({ apiFetch: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CreditScoreCard", () => {
  it("loads the current score and renders the borrower's score history", async () => {
    const score = { creditScore: 600, riskTier: "HIGH", eligible: true, maxEligibleLoan: 25000 };
    apiFetch.mockImplementation(async (url) => ({
      ok: true,
      status: 200,
      json: async () => url.endsWith("/history") ? [{
        id: 1,
        creditScore: 600,
        riskTier: "HIGH",
        maxEligibleLoan: 25000,
        recordedAt: "2026-01-01T10:00:00"
      }] : score
    }));
    const onScoreData = vi.fn();

    render(<CreditScoreCard onScoreData={onScoreData} />);

    expect(await screen.findByLabelText("Credit score 600")).toBeTruthy();
    expect(await screen.findByText("Recent score history")).toBeTruthy();
    expect(onScoreData).toHaveBeenCalledWith(score);
    expect(apiFetch).toHaveBeenNthCalledWith(1, "/api/v1/credit-score/me", expect.objectContaining({
      headers: { Authorization: "Bearer borrower-token" }
    }));
    expect(apiFetch).toHaveBeenNthCalledWith(2, "/api/v1/credit-score/history", expect.objectContaining({
      headers: { Authorization: "Bearer borrower-token" }
    }));
  });
});
