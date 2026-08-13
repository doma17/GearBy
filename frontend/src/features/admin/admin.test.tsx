import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Admin from "./admin";

const candidate = (id: string, name: string) => ({
  id, firstSeenRunId: "run-1", lastSeenRunId: "run-1", provider: "NAVER", sourceUrl: `https://example.com/${id}`,
  normalizedName: name, roadAddress: "서울시 중구", roundedLatitude: 37.56, roundedLongitude: 126.97, phone: "02-1234-5678",
  latestOutcome: "QUARANTINED", latestMatchStatus: "NO_MATCH", createdAt: "2026-08-01T00:00:00Z", updatedAt: "2026-08-01T00:00:00Z",
});

const firstCandidate = candidate("candidate-1", "첫 번째 후보");
const secondCandidate = candidate("candidate-2", "두 번째 후보");

function success(data: unknown) {
  return new Response(JSON.stringify({ success: true, timestamp: "2026-08-01T00:00:00Z", data, error: null }));
}

function page(items: unknown[]) {
  return { items, page: 0, size: 50, total: items.length };
}

describe("Admin candidate review", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("submits both generated candidate resolution contracts and restores focus", async () => {
    const resolutions: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/auth/session")) return success({ authenticated: true, csrfToken: "csrf-token" });
      if (url.includes("/candidate-ingestion/items/") && url.endsWith("/resolve")) {
        resolutions.push(JSON.parse(String(init?.body)));
        return success({ itemId: "candidate-1", outcome: "RESOLVED", matchStatus: "RESOLVED_EXISTING", resolvedStoreId: "store-1" });
      }
      if (url.includes("/candidate-ingestion/runs")) return success(page([]));
      if (url.includes("/candidate-ingestion/items")) return success(page(resolutions.length === 0 ? [firstCandidate, secondCandidate] : resolutions.length === 1 ? [secondCandidate] : []));
      if (url.endsWith("/stores")) return success({ DRAFT: [{ id: "store-1", name: "기존 매장", address: "서울", categories: ["HIKING"] }] });
      if (url.endsWith("/correction-rules") || url.endsWith("/feedback") || url.endsWith("/category-health") || url.endsWith("/category-review-flags")) return success([]);
      if (url.endsWith("/dashboard")) return success({ stores: {}, feedback: {}, activeCorrectionRules: 0, categoryHealth: [] });
      throw new Error(`Unexpected request: ${url}`);
    }));

    const user = userEvent.setup();
    render(<Admin apiBaseUrl="http://api.example" />);

    await user.selectOptions(await screen.findByLabelText("첫 번째 후보 기존 매장"), "store-1");
    await user.click(within(screen.getByText("첫 번째 후보").closest("article")!).getByRole("button", { name: "기존 매장 연결" }));
    await waitFor(() => expect(resolutions[0]).toEqual({ resolutionType: "LINK_EXISTING", storeId: "store-1" }));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText("두 번째 후보 기존 매장")));

    await user.click(screen.getByText("검수용 초안 만들기"));
    await user.clear(screen.getByLabelText("매장명"));
    await user.type(screen.getByLabelText("매장명"), "새 초안 매장");
    await user.clear(screen.getByLabelText("주소"));
    await user.type(screen.getByLabelText("주소"), "서울시 강남구");
    await user.clear(screen.getByLabelText("위도"));
    await user.type(screen.getByLabelText("위도"), "37.5");
    await user.clear(screen.getByLabelText("경도"));
    await user.type(screen.getByLabelText("경도"), "127.0");
    await user.selectOptions(screen.getByLabelText("카테고리"), "CAMPING");
    await user.clear(screen.getByLabelText("전화"));
    await user.type(screen.getByLabelText("전화"), "02-9999-9999");
    await user.click(screen.getByRole("button", { name: "초안 생성" }));

    await waitFor(() => expect(resolutions[1]).toEqual({
      resolutionType: "CREATE_DRAFT", name: "새 초안 매장", address: "서울시 강남구",
      coordinates: { latitude: 37.5, longitude: 127 }, categories: ["CAMPING"], phone: "02-9999-9999",
    }));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText("검수 대기 후보 상태")));
  });

  it("keeps existing operations visible when candidate requests fail", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/auth/session")) return success({ authenticated: true, csrfToken: "csrf-token" });
      if (url.includes("/candidate-ingestion/")) throw new Error("candidate unavailable");
      if (url.endsWith("/stores")) return success({ DRAFT: [{ id: "store-1", name: "기존 매장", address: "서울", categories: ["HIKING"] }] });
      if (url.endsWith("/correction-rules") || url.endsWith("/feedback") || url.endsWith("/category-health") || url.endsWith("/category-review-flags")) return success([]);
      if (url.endsWith("/dashboard")) return success({ stores: {}, feedback: {}, activeCorrectionRules: 0, categoryHealth: [] });
      throw new Error(`Unexpected request: ${url}`);
    }));

    render(<Admin apiBaseUrl="http://api.example" />);
    expect(await screen.findByText("기존 매장", { selector: "strong" })).toBeTruthy();
    expect((await screen.findByRole("alert")).textContent).toContain("candidate unavailable");
  });
});
