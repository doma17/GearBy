import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Discovery from "./discovery";

const store = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Trail House",
  address: "Seoul",
  coordinates: { latitude: 37.5665, longitude: 126.978 },
  categories: ["HIKING"],
  hours: "10:00-20:00",
};

function success(data: unknown) {
  return new Response(JSON.stringify({ success: true, timestamp: "2026-07-28T00:00:00Z", data, error: null }));
}

describe("Discovery", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("loads stores and applies a typed search to the request", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([{ slug: "HIKING", displayName: "Hiking" }]));
      if (url.startsWith("http://api.example/api/v1/stores?")) return Promise.resolve(success({ items: [store] }));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example/" naverMapClientId="" />);

    expect(await screen.findByText("Trail House")).toBeTruthy();
    await user.type(screen.getByRole("textbox", { name: "Search stores or activities" }), "trail");

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "http://api.example/api/v1/stores?sort=name&limit=100&q=trail",
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
    });
  });

  it("shows a retryable error when the store request fails", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([{ slug: "HIKING", displayName: "Hiking" }]));
      return Promise.reject(new Error("network unavailable"));
    }));

    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);

    expect((await screen.findByRole("alert")).textContent).toContain("Stores could not be loaded. Please retry.");
  });

  it("keeps the built-in categories when the category request fails", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.reject(new Error("network unavailable"));
      return Promise.resolve(success({ items: [] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await user.click(screen.getByRole("button", { name: "Filter" }));

    expect(await screen.findByRole("button", { name: "Hiking" })).toBeTruthy();
  });
});
