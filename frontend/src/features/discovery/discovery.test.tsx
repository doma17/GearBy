import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
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
  verifiedAt: "2026-07-20T12:00:00Z",
  informationStatus: "VERIFIED",
};

const reviewDueStore = {
  ...store,
  id: "22222222-2222-2222-2222-222222222222",
  name: "Peak Supply",
  informationStatus: "REVIEW_DUE",
};

function success(data: unknown) {
  return new Response(JSON.stringify({ success: true, timestamp: "2026-07-28T00:00:00Z", data, error: null }));
}

describe("Discovery", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); delete window.naver; });

  it("waits for deliberate search submission before requesting typed text", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([{ slug: "HIKING", displayName: "등산" }]));
      if (url.startsWith("http://api.example/api/v1/stores?")) return Promise.resolve(success({ items: [store] }));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example/" naverMapClientId="" />);

    expect(await screen.findByText("Trail House")).toBeTruthy();
    await user.type(screen.getByRole("textbox", { name: "검색" }), "trail");

    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("q=trail"),
      expect.anything(),
    );
    await user.click(screen.getByRole("button", { name: "검색" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "http://api.example/api/v1/stores?sort=name&limit=100&q=trail",
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
    });
  });

  it("shows a retryable error when the store request fails", async () => {
    let storeAttempts = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([{ slug: "HIKING", displayName: "등산" }]));
      storeAttempts += 1;
      return storeAttempts === 1 ? Promise.reject(new Error("network unavailable")) : Promise.resolve(success({ items: [] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);

    expect((await screen.findByRole("alert")).textContent).toContain("매장 정보를 불러오지 못했습니다.");
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(storeAttempts).toBe(2));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("announces loading until the initial store request resolves", async () => {
    let resolveStores!: (response: Response) => void;
    const stores = new Promise<Response>((resolve) => { resolveStores = resolve; });
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      return stores;
    }));

    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    expect(await screen.findByText("매장을 불러오는 중입니다.")).toBeTruthy();
    resolveStores(success({ items: [] }));

    await waitFor(() => expect(screen.queryByText("매장을 불러오는 중입니다.")).toBeNull());
  });

  it("keeps the built-in categories when the category request fails", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.reject(new Error("network unavailable"));
      return Promise.resolve(success({ items: [] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await user.click(screen.getByRole("tab", { name: "필터" }));

    expect(await screen.findByRole("button", { name: "등산" })).toBeTruthy();
    expect(screen.getByText(/기본 카테고리/)).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Admin" })).toBeNull();
    expect(document.querySelector(".rail-footer")).toBeNull();
  });

  it("renders Korean labels returned by the category API", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([{ slug: "CLIMBING", displayName: "클라이밍" }]));
      return Promise.resolve(success({ items: [] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await user.click(screen.getByRole("tab", { name: "필터" }));

    expect(await screen.findByRole("button", { name: "클라이밍" })).toBeTruthy();
    expect(screen.queryByText(/기본 카테고리/)).toBeNull();
  });

  it("uses concise brand-led discovery copy", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      return Promise.resolve(success({ items: [store] }));
    }));

    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await screen.findByText("Trail House");

    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe("NearBy Gear");
    expect(screen.getByText("CURATED STORES")).toBeTruthy();
    expect(document.querySelector(".map-caption")?.textContent).toBe("GEARBY / CURATED STORES");
    expect(screen.queryByText("전국 아웃도어 장비점을 찾아보세요")).toBeNull();
  });

  it("retries a corrected search with the original query and correction disabled", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([]));
      if (url.includes("q=trail") && !url.includes("applyCorrection=false")) {
        return Promise.resolve(success({ items: [], search: { originalQuery: "trail", appliedQuery: "trek", correction: "trek" } }));
      }
      if (url.includes("q=trail") && url.includes("applyCorrection=false")) return Promise.resolve(success({ items: [] }));
      if (url.includes("/stores?")) return Promise.resolve(success({ items: [] }));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await user.type(screen.getByRole("textbox", { name: "검색" }), "trail");
    await user.click(screen.getByRole("button", { name: "검색" }));
    const correctionStatus = (await screen.findByText(/“trek” 검색 결과입니다/)).closest("[role='status']");
    expect(correctionStatus?.tagName).toBe("DIV");
    expect(screen.getByRole("button", { name: "원문으로 검색" })).toBeTruthy();
    await user.click(await screen.findByRole("button", { name: "원문으로 검색" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      "http://api.example/api/v1/stores?sort=name&limit=100&q=trail&applyCorrection=false",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    ));
  });

  it("removes stale correction guidance when the next search fails", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([]));
      if (url.includes("q=trail")) {
        return Promise.resolve(success({ items: [store], search: { originalQuery: "trail", appliedQuery: "trek", correction: "trek" } }));
      }
      if (url.includes("q=broken")) return Promise.reject(new Error("network unavailable"));
      if (url.includes("/stores?")) return Promise.resolve(success({ items: [store] }));
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    const input = screen.getByRole("textbox", { name: "검색" });
    await user.type(input, "trail");
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(await screen.findByRole("button", { name: "원문으로 검색" })).toBeTruthy();

    await user.clear(input);
    await user.type(input, "broken");
    await user.click(screen.getByRole("button", { name: "검색" }));

    expect((await screen.findByRole("alert")).textContent).toContain("매장 정보를 불러오지 못했습니다.");
    expect(screen.queryByRole("button", { name: "원문으로 검색" })).toBeNull();
    expect(screen.getByText("Trail House")).toBeTruthy();
  });

  it("waits for explicit area search before requesting pending map bounds", async () => {
    let idleListener: (() => void) | undefined;
    class MockMap {
      getBounds() {
        return {
          getSW: () => ({ lat: () => 37.4, lng: () => 126.7 }),
          getNE: () => ({ lat: () => 37.7, lng: () => 127.2 }),
        };
      }
      setCenter() {}
    }
    window.naver = { maps: {
      Map: MockMap,
      LatLng: class {},
      Marker: class { setMap() {} },
      Event: { addListener: (_target, event, listener) => { if (event === "idle") idleListener = listener; } },
    } };
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      return Promise.resolve(success({ items: [] }));
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="client-id" />);
    await waitFor(() => expect(idleListener).toBeTypeOf("function"));
    act(() => idleListener?.());

    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("bbox="), expect.anything());
    await user.click(screen.getByRole("button", { name: "이 지역 검색" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      "http://api.example/api/v1/stores?sort=name&limit=100&bbox=126.7%2C37.4%2C127.2%2C37.7",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    ));
    expect(screen.queryByRole("button", { name: "이 지역 검색" })).toBeNull();
  });

  it("hides and shows the result panel without losing the active map", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      return Promise.resolve(success({ items: [store] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);

    expect(await screen.findByLabelText("매장 검색 결과")).toBeTruthy();
    const close = screen.getByRole("button", { name: "목록 닫기" });
    expect(close.textContent).toBe("×");
    await user.click(close);
    expect(document.getElementById("places-panel")?.getAttribute("aria-hidden")).toBe("true");

    const open = screen.getByRole("button", { name: "목록 열기" });
    expect(open.textContent).toBe("<<");
    await user.click(open);
    expect((await screen.findByLabelText("매장 검색 결과")).getAttribute("aria-hidden")).toBe("false");
  });

  it("centers the map on the browser location instead of the default center", async () => {
    const centers: unknown[] = [];
    const markerTitles: string[] = [];
    class MockLatLng {
      constructor(readonly latitude: number, readonly longitude: number) {}
    }
    class MockMap {
      getBounds() {
        return {
          getSW: () => ({ lat: () => 37.4, lng: () => 126.7 }),
          getNE: () => ({ lat: () => 37.7, lng: () => 127.2 }),
        };
      }
      setCenter(position: unknown) { centers.push(position); }
    }
    window.naver = { maps: {
      Map: MockMap,
      LatLng: MockLatLng,
      Marker: class {
        constructor({ title }: { title: string }) { markerTitles.push(title); }
        setMap() {}
      },
      Event: { addListener: () => undefined },
    } };
    vi.stubGlobal("navigator", {
      geolocation: {
        getCurrentPosition: (success: PositionCallback) => success({ coords: { latitude: 35.1796, longitude: 129.0756 } } as GeolocationPosition),
      },
    });
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      return Promise.resolve(success({ items: [] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="client-id" />);
    await user.click(await screen.findByRole("button", { name: "내 위치" }));

    await waitFor(() => expect(centers).toHaveLength(1));
    expect(centers[0]).toMatchObject({ latitude: 35.1796, longitude: 129.0756 });
    expect(markerTitles).toEqual(["내 위치"]);
  });

  it("opens store detail when a map marker is selected", async () => {
    let markerClick: (() => void) | undefined;
    class MockMap {
      getBounds() {
        return {
          getSW: () => ({ lat: () => 37.4, lng: () => 126.7 }),
          getNE: () => ({ lat: () => 37.7, lng: () => 127.2 }),
        };
      }
      setCenter() {}
    }
    class MockMarker {
      constructor(readonly options: { title: string }) {}
      setMap() {}
    }
    window.naver = { maps: {
      Map: MockMap,
      LatLng: class {},
      Marker: MockMarker,
      Event: { addListener: (target, event, listener) => { if (target instanceof MockMarker && event === "click" && target.options.title === store.name) markerClick = listener; } },
    } };
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith("/categories")) return Promise.resolve(success([]));
      if (String(input).endsWith(`/stores/${store.id}`)) return Promise.resolve(success(store));
      return Promise.resolve(success({ items: [store] }));
    }));

    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="client-id" />);
    await waitFor(() => expect(markerClick).toBeTypeOf("function"));
    act(() => markerClick?.());

    expect(await screen.findByRole("heading", { name: store.name, level: 3 })).toBeTruthy();
  });

  it("keeps freshness metadata out of public results and detail, then returns focus to the selected result", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([]));
      if (url.endsWith(`/stores/${reviewDueStore.id}`)) return Promise.resolve(success(reviewDueStore));
      return Promise.resolve(success({ items: [store, reviewDueStore] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    const result = await screen.findByRole("button", { name: /Peak Supply/ });
    expect(result.textContent).not.toMatch(/최근 확인|재확인 필요/);
    await user.click(result);

    expect(await screen.findByRole("heading", { name: "Peak Supply", level: 3 })).toBeTruthy();
    expect(screen.queryByText(/최근 확인|재확인 필요/)).toBeNull();
    expect(screen.getByRole("link", { name: "Naver Map Link" })).toBeTruthy();
    expect(screen.queryByRole("link", { name: /전화|길찾기|지도 열기/ })).toBeNull();
    await user.click(screen.getByRole("button", { name: "매장 상세 닫기" }));
    await waitFor(() => expect(document.activeElement).toBe(result));
  });

  it("opens a compact report form from the selected store detail", async () => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/categories")) return Promise.resolve(success([]));
      if (url.endsWith(`/stores/${store.id}`)) return Promise.resolve(success(store));
      return Promise.resolve(success({ items: [store] }));
    }));

    const user = userEvent.setup();
    render(<Discovery apiBaseUrl="http://api.example" naverMapClientId="" />);
    await user.click(await screen.findByRole("button", { name: /Trail House/ }));
    await screen.findByRole("heading", { name: store.name, level: 3 });

    expect(screen.queryByRole("dialog", { name: "매장 정보 신고" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "매장 정보 신고" }));
    expect(await screen.findByRole("dialog", { name: "매장 정보 신고" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "문제 내용" })).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "취소" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "매장 정보 신고" })).toBeNull());
  });
});
