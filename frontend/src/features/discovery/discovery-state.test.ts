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
