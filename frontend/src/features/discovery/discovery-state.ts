export type DiscoveryPanel = "places" | "filters";

export type DiscoveryViewState = {
  activePanel: DiscoveryPanel;
  error: string;
  feedbackStatus: string;
  near?: string;
  query: string;
  selectedCategories: string[];
  selectedId?: string;
  sort: "name" | "distance";
};

export type DiscoveryViewAction =
  | { type: "categoryToggled"; category: string }
  | { type: "errorChanged"; error: string }
  | { type: "feedbackStatusChanged"; feedbackStatus: string }
  | { type: "locationResolved"; near: string }
  | { type: "panelChanged"; panel: DiscoveryPanel }
  | { type: "queryChanged"; query: string }
  | { type: "selectedIdChanged"; selectedId?: string }
  | { type: "sortChanged"; sort: "name" | "distance" };

export const initialDiscoveryViewState: DiscoveryViewState = {
  activePanel: "places",
  error: "",
  feedbackStatus: "",
  query: "",
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
    case "locationResolved":
      return { ...state, near: action.near, sort: "distance" };
    case "panelChanged":
      return { ...state, activePanel: action.panel };
    case "queryChanged":
      return { ...state, query: action.query };
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

export function storeSearchParams(state: Pick<DiscoveryViewState, "near" | "query" | "selectedCategories" | "sort">): URLSearchParams {
  const parameters = new URLSearchParams({ sort: state.sort, limit: "100" });
  if (state.query.trim()) parameters.set("q", state.query.trim());
  if (state.near) parameters.set("near", state.near);
  state.selectedCategories.forEach((category) => parameters.append("category", category));
  return parameters;
}
