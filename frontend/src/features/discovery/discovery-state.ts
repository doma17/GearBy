export type DiscoveryPanel = "places" | "filters";

export type DiscoveryViewState = {
  activePanel: DiscoveryPanel;
  applyCorrection: boolean;
  bbox?: string;
  error: string;
  feedbackStatus: string;
  near?: string;
  pendingBbox?: string;
  query: string;
  queryDraft: string;
  selectedCategories: string[];
  selectedId?: string;
  sort: "name" | "distance";
};

export type DiscoveryViewAction =
  | { type: "categoryToggled"; category: string }
  | { type: "correctionRolledBack"; query: string }
  | { type: "errorChanged"; error: string }
  | { type: "feedbackStatusChanged"; feedbackStatus: string }
  | { type: "locationResolved"; near: string }
  | { type: "mapBoundsChanged"; bbox: string }
  | { type: "mapBoundsCommitted" }
  | { type: "panelChanged"; panel: DiscoveryPanel }
  | { type: "queryChanged"; query: string }
  | { type: "querySubmitted" }
  | { type: "selectedIdChanged"; selectedId?: string }
  | { type: "sortChanged"; sort: "name" | "distance" };

export const initialDiscoveryViewState: DiscoveryViewState = {
  activePanel: "places",
  applyCorrection: true,
  error: "",
  feedbackStatus: "",
  query: "",
  queryDraft: "",
  selectedCategories: [],
  sort: "name",
};

// The reducer owns ephemeral controls; fetched catalog data stays with its effects.
export function discoveryViewReducer(state: DiscoveryViewState, action: DiscoveryViewAction): DiscoveryViewState {
  switch (action.type) {
    case "categoryToggled":
      return {
        ...state,
        selectedCategories: state.selectedCategories.includes(action.category)
          ? state.selectedCategories.filter((category) => category !== action.category)
          : [...state.selectedCategories, action.category],
      };
    case "correctionRolledBack":
      return { ...state, applyCorrection: false, query: action.query, queryDraft: action.query };
    case "locationResolved":
      return { ...state, near: action.near, sort: "distance" };
    case "mapBoundsChanged":
      return { ...state, pendingBbox: action.bbox };
    case "mapBoundsCommitted":
      return state.pendingBbox ? { ...state, bbox: state.pendingBbox } : state;
    case "panelChanged":
      return { ...state, activePanel: action.panel };
    case "queryChanged":
      return { ...state, queryDraft: action.query };
    case "querySubmitted":
      return { ...state, applyCorrection: true, query: state.queryDraft.trim() };
    case "selectedIdChanged":
      return { ...state, selectedId: action.selectedId };
    case "sortChanged":
      return { ...state, sort: action.sort };
    case "errorChanged":
      return { ...state, error: action.error };
    case "feedbackStatusChanged":
      return { ...state, feedbackStatus: action.feedbackStatus };
  }
}

export function storeSearchParams(state: Pick<DiscoveryViewState, "applyCorrection" | "bbox" | "near" | "query" | "selectedCategories" | "sort">): URLSearchParams {
  const parameters = new URLSearchParams({ sort: state.sort, limit: "100" });
  if (state.query.trim()) parameters.set("q", state.query.trim());
  if (!state.applyCorrection) parameters.set("applyCorrection", "false");
  if (state.bbox) parameters.set("bbox", state.bbox);
  if (state.near) parameters.set("near", state.near);
  state.selectedCategories.forEach((category) => parameters.append("category", category));
  return parameters;
}
