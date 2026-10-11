import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import Admin from "./Admin";
import { apiFetch } from "../api/axios";

vi.mock("../AuthContext", () => ({ useAuth: () => ({ token: "admin-token" }) }));
vi.mock("../api/axios", () => ({ apiFetch: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Admin user directory", () => {
  it("filters users and paginates the displayed rows", async () => {
    const users = Array.from({ length: 21 }, (_, index) => ({
      id: index + 1,
      name: `Borrower ${index + 1}`,
      email: `user${index + 1}@example.test`,
      role: "BORROWER"
    }));
    apiFetch.mockImplementation(async (url) => {
      if (url.includes("/users?")) {
        const params = new URL(url, "http://localhost").searchParams;
        const query = (params.get("q") || "").toLowerCase();
        const role = params.get("role");
        const filtered = users.filter((user) => (!role || user.role === role)
          && `${user.name} ${user.email}`.toLowerCase().includes(query));
        const page = Number(params.get("page"));
        const size = Number(params.get("size"));
        return {
          ok: true,
          status: 200,
          json: async () => ({
            content: filtered.slice(page * size, (page + 1) * size),
            page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size)
          })
        };
      }
      return { ok: true, status: 200, json: async () => [] };
    });

    render(<Admin />);

    expect(await screen.findByText("Borrower 1")).toBeTruthy();
    expect(screen.queryByText("Borrower 11")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Next users" }));
    expect(await screen.findByText("Borrower 11")).toBeTruthy();
    expect(screen.queryByText("Borrower 1")).toBeNull();

    fireEvent.change(screen.getByRole("searchbox", { name: "Search name or email" }), {
      target: { value: "user21@example.test" }
    });
    expect(await screen.findByText("Borrower 21")).toBeTruthy();
    expect(screen.queryByText("Borrower 11")).toBeNull();
    await waitFor(() => expect(apiFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/admin/users?page=0&size=10&q=user21%40example.test"),
      expect.objectContaining({ headers: { Authorization: "Bearer admin-token" } })
    ));
  });
});
