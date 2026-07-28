import { describe, expect, it } from "vitest";
import { discoveryViewReducer, initialDiscoveryViewState, storeSearchParams } from "./discovery-state";

describe("discoveryViewReducer", () => {
  it("toggles a category without changing other view state", () => {
    const selected = discoveryViewReducer(initialDiscoveryViewState, { type: "categoryToggled", category: "HIKING" });
    const cleared = discoveryViewReducer(selected, { type: "categoryToggled", category: "HIKING" });

    expect(selected.selectedCategories).toEqual(["HIKING"]);
    expect(cleared.selectedCategories).toEqual([]);
    expect(cleared.sort).toBe("name");
  });

  it("uses distance sorting only after a location is resolved", () => {
    expect(discoveryViewReducer(initialDiscoveryViewState, { type: "locationResolved", near: "37.5665,126.978" })).toMatchObject({
      near: "37.5665,126.978",
      sort: "distance",
    });
  });

  it("keeps draft search text separate until it is submitted", () => {
    const typed = discoveryViewReducer(initialDiscoveryViewState, { type: "queryChanged", query: "  pack  " });
    const submitted = discoveryViewReducer(typed, { type: "querySubmitted" });

    expect(typed.query).toBe("");
    expect(typed.queryDraft).toBe("  pack  ");
    expect(submitted.query).toBe("pack");
  });

  it("opts out of correction for rollback and resets for a newly submitted query", () => {
    const rolledBack = discoveryViewReducer(initialDiscoveryViewState, { type: "correctionRolledBack", query: "trail" });
    const typed = discoveryViewReducer(rolledBack, { type: "queryChanged", query: "tent" });
    const submitted = discoveryViewReducer(typed, { type: "querySubmitted" });

    expect(storeSearchParams(rolledBack).get("applyCorrection")).toBe("false");
    expect(typed.query).toBe("trail");
    expect(submitted).toMatchObject({ query: "tent", applyCorrection: true });
    expect(storeSearchParams(submitted).has("applyCorrection")).toBe(false);
  });

  it("serializes only committed map bounds", () => {
    const located = { ...initialDiscoveryViewState, near: "37.5665,126.978", sort: "distance" as const };
    const pending = discoveryViewReducer(located, { type: "mapBoundsChanged", bbox: "126.7,37.4,127.2,37.7" });
    const committed = discoveryViewReducer(pending, { type: "mapBoundsCommitted" });

    expect(storeSearchParams(pending).has("bbox")).toBe(false);
    expect(storeSearchParams(committed).get("bbox")).toBe("126.7,37.4,127.2,37.7");
    expect(committed).toMatchObject({ near: "37.5665,126.978", pendingBbox: committed.bbox, sort: "distance" });
  });
});

describe("storeSearchParams", () => {
  it("serializes trimmed search text, location, and repeated category filters", () => {
    const parameters = storeSearchParams({
      ...initialDiscoveryViewState,
      near: "37.5665,126.978",
      query: "  pack  ",
      selectedCategories: ["HIKING", "BACKPACKING"],
      sort: "distance",
    });

    expect(parameters.toString()).toBe("sort=distance&limit=100&q=pack&near=37.5665%2C126.978&category=HIKING&category=BACKPACKING");
  });
});
