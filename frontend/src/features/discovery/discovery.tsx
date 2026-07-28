"use client";

import Link from "next/link";
import { FormEvent, useEffect, useReducer, useRef, useState } from "react";
import type { components } from "../../generated/api";
import { directionsUrls } from "./directions.mjs";
import { readApiResponse } from "../../shared/api/api-response";
import { discoveryViewReducer, initialDiscoveryViewState, storeSearchParams } from "./discovery-state";

type Category = components["schemas"]["Category"];
type Store = components["schemas"]["Store"];
type StorePage = components["schemas"]["StorePage"];
type FeedbackKind = components["schemas"]["FeedbackInput"]["kind"];
type NaverMap = { setCenter: (position: unknown) => void };
type NaverMarker = { setMap: (map: NaverMap | null) => void };
type NaverMaps = {
  Map: new (element: HTMLElement, options: { center: unknown; zoom: number }) => NaverMap;
  LatLng: new (latitude: number, longitude: number) => unknown;
  Marker: new (options: { position: unknown; map: NaverMap; title: string }) => NaverMarker;
  Event: { addListener: (target: NaverMarker, event: string, listener: () => void) => void };
};

declare global {
  interface Window { naver?: { maps: NaverMaps } }
}

const fallbackCategories: Category[] = [
  { slug: "HIKING", displayName: "Hiking" },
  { slug: "BACKPACKING", displayName: "Backpacking" },
  { slug: "CAMPING", displayName: "Camping" },
  { slug: "CLIMBING", displayName: "Climbing" },
];

function loadNaverMaps(clientId: string) {
  if (window.naver?.maps) return Promise.resolve();
  const existing = document.querySelector<HTMLScriptElement>("script[data-naver-map]");
  if (existing) return new Promise<void>((resolve, reject) => {
    existing.addEventListener("load", () => resolve(), { once: true });
    existing.addEventListener("error", () => reject(new Error("NAVER Map failed to load.")), { once: true });
  });

  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.dataset.naverMap = "true";
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}`;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("NAVER Map failed to load."));
    document.head.append(script);
  });
}

export default function Discovery({ apiBaseUrl, naverMapClientId }: { apiBaseUrl: string; naverMapClientId: string }) {
  const api = `${apiBaseUrl.replace(/\/$/, "")}/api/v1`;
  const mapElement = useRef<HTMLDivElement>(null);
  const map = useRef<NaverMap | null>(null);
  const markers = useRef<NaverMarker[]>([]);
  const [categories, setCategories] = useState(fallbackCategories);
  const [stores, setStores] = useState<Store[]>([]);
  const [search, setSearch] = useState<StorePage["search"]>();
  const [detail, setDetail] = useState<Store>();
  const [view, dispatch] = useReducer(discoveryViewReducer, initialDiscoveryViewState);
  const [mapError, setMapError] = useState(naverMapClientId ? "" : "NAVER Map is not configured. Use the accessible store list below.");

  useEffect(() => {
    // Keep the local category fallback when the catalog endpoint is unavailable.
    fetch(`${api}/categories`).then(readApiResponse<Category[]>)
      .then(setCategories).catch(() => undefined);
  }, [api]);

  useEffect(() => {
    // Abort stale searches so rapid filter changes cannot overwrite newer results.
    const controller = new AbortController();
    const parameters = storeSearchParams({
      near: view.near,
      query: view.query,
      selectedCategories: view.selectedCategories,
      sort: view.sort,
    });
    fetch(`${api}/stores?${parameters}`, { signal: controller.signal })
      .then((response) => readApiResponse<StorePage>(response))
      .then((page) => { setStores(page.items); setSearch(page.search); dispatch({ type: "errorChanged", error: "" }); })
      .catch((reason: Error) => { if (reason.name !== "AbortError") dispatch({ type: "errorChanged", error: "Stores could not be loaded. Please retry." }); });
    return () => controller.abort();
  }, [api, view.near, view.query, view.selectedCategories, view.sort]);

  useEffect(() => {
    if (!view.selectedId) return;
    fetch(`${api}/stores/${view.selectedId}`).then((response) => readApiResponse<Store>(response))
      .then(setDetail).catch(() => dispatch({ type: "errorChanged", error: "Store details could not be loaded." }));
  }, [api, view.selectedId]);

  useEffect(() => {
    if (!naverMapClientId || !mapElement.current) return;
    // The vendor SDK owns a global namespace, so load its script only once.
    loadNaverMaps(naverMapClientId).then(() => {
      const naver = window.naver?.maps;
      if (!naver || !mapElement.current) throw new Error("NAVER Map is unavailable.");
      map.current ??= new naver.Map(mapElement.current, { center: new naver.LatLng(37.5665, 126.978), zoom: 11 });
      setMapError("");
    }).catch((reason: Error) => setMapError(`${reason.message} Use the accessible store list below.`));
  }, [naverMapClientId]);

  useEffect(() => {
    const naver = window.naver?.maps;
    if (!naver || !map.current) return;
    // Rebuild markers from the current result set to keep map and list state aligned.
    markers.current.forEach((marker) => marker.setMap(null));
    markers.current = stores.map((store) => {
      const marker = new naver.Marker({ position: new naver.LatLng(store.coordinates.latitude, store.coordinates.longitude), map: map.current!, title: store.name });
      naver.Event.addListener(marker, "click", () => dispatch({ type: "selectedIdChanged", selectedId: store.id }));
      return marker;
    });
  }, [stores]);

  useEffect(() => {
    const store = stores.find((item) => item.id === view.selectedId);
    const naver = window.naver?.maps;
    if (store && naver && map.current) map.current.setCenter(new naver.LatLng(store.coordinates.latitude, store.coordinates.longitude));
  }, [view.selectedId, stores]);

  function toggleCategory(category: string) {
    dispatch({ type: "categoryToggled", category });
  }

  function useLocation() {
    if (!navigator.geolocation) { dispatch({ type: "errorChanged", error: "Location is unavailable in this browser." }); return; }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => dispatch({ type: "locationResolved", near: `${coords.latitude},${coords.longitude}` }),
      () => dispatch({ type: "errorChanged", error: "Location permission is needed for distance sorting." }),
    );
  }

  async function submitFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const contactConsent = form.get("contactConsent") === "on";
    const payload: components["schemas"]["FeedbackInput"] = {
      kind: form.get("kind") as FeedbackKind,
      content: String(form.get("content") ?? ""),
      categoryRelated: form.get("categoryRelated") === "on",
      ...(selectedDetail ? { storeId: selectedDetail.id } : {}),
      ...(contactConsent && form.get("replyEmail") ? { replyEmail: String(form.get("replyEmail")), contactConsent } : { contactConsent }),
    };
    const response = await fetch(`${api}/feedback`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    try {
      await readApiResponse(response);
      dispatch({ type: "feedbackStatusChanged", feedbackStatus: "Thanks—your feedback was sent for review." });
      event.currentTarget.reset();
    } catch {
      dispatch({ type: "feedbackStatusChanged", feedbackStatus: "Feedback could not be sent. Please try again." });
    }
  }

  const selectedDetail = detail?.id === view.selectedId ? detail : undefined;
  const directions = selectedDetail && directionsUrls(selectedDetail, window.location.origin);

  return <main className="map-app">
    <aside className="nav-rail" aria-label="Primary navigation">
      <Link className="brand-mark" href="/" aria-label="GearBy home">G</Link>
      <nav>
        <Link className="rail-link active" href="/" aria-current="page"><span aria-hidden="true">⌖</span><span>Explore</span></Link>
        <button className="rail-link" type="button" onClick={() => dispatch({ type: "panelChanged", panel: "filters" })}><span aria-hidden="true">⌘</span><span>Filters</span></button>
        <Link className="rail-link" href="/admin"><span aria-hidden="true">▦</span><span>Admin</span></Link>
      </nav>
      <p className="rail-footer">Seoul<br />outdoors</p>
    </aside>

    <section className="map-workspace">
      <header className="map-toolbar">
        <div className="toolbar-title"><p className="eyebrow">GEARBY</p><h1>Find outdoor gear nearby</h1></div>
        <label className="search-field"><span aria-hidden="true">⌕</span><input value={view.query} onChange={(event) => dispatch({ type: "queryChanged", query: event.target.value })} placeholder="Search stores or activities" aria-label="Search stores or activities" /></label>
        <button className="location-button" type="button" onClick={useLocation}>Use my location</button>
      </header>

      {view.error && <p className="error workspace-notice" role="alert">{view.error}</p>}
      {search?.correction && <p className="notice workspace-notice">Showing results for “{search.appliedQuery}” (corrected from “{search.originalQuery}”).</p>}

      <section className="map-stage" aria-label="Store discovery workspace">
        <div className="map-canvas">
          <div className="map" ref={mapElement} aria-label="Store map" />
          {!naverMapClientId && <div className="map-grid" aria-hidden="true" />}
          {mapError && <p className="map-fallback" role="status">{mapError}</p>}
          <p className="map-caption">NAVER MAP · GEARBY VERIFIED PLACES</p>
        </div>

        <aside className="places-panel" aria-label="Store results">
          <div className="panel-tabs" role="tablist" aria-label="Discovery panels">
            <button type="button" role="tab" aria-selected={view.activePanel === "places"} className={view.activePanel === "places" ? "active" : ""} onClick={() => dispatch({ type: "panelChanged", panel: "places" })}>Places</button>
            <button type="button" role="tab" aria-selected={view.activePanel === "filters"} className={view.activePanel === "filters" ? "active" : ""} onClick={() => dispatch({ type: "panelChanged", panel: "filters" })}>Filters</button>
          </div>

          {view.activePanel === "filters" ? <section className="filter-panel">
            <div><p className="panel-kicker">DISCOVER</p><h2>Refine your map</h2><p>Choose the activities and order that fit today&apos;s trip.</p></div>
            <fieldset><legend>Activities</legend><div className="chips">{categories.map((category) => <button type="button" className={view.selectedCategories.includes(category.slug) ? "chip selected" : "chip"} aria-pressed={view.selectedCategories.includes(category.slug)} key={category.slug} onClick={() => toggleCategory(category.slug)}>{category.displayName}</button>)}</div></fieldset>
            <label>Sort results<select value={view.sort} onChange={(event) => dispatch({ type: "sortChanged", sort: event.target.value as "name" | "distance" })}><option value="name">Name</option><option value="distance" disabled={!view.near}>Distance</option></select></label>
            <button className="primary-action" type="button" onClick={() => dispatch({ type: "panelChanged", panel: "places" })}>Show {stores.length} places</button>
          </section> : <section className="places-content" aria-live="polite">
            <div className="panel-heading"><div><p className="panel-kicker">PLACES NEAR YOU</p><h2>{stores.length} verified stores</h2></div><button type="button" className="filter-trigger" onClick={() => dispatch({ type: "panelChanged", panel: "filters" })}>Filter</button></div>
            <div className="store-list">{stores.map((store) => <button type="button" key={store.id} className={store.id === view.selectedId ? "store-card selected" : "store-card"} onClick={() => dispatch({ type: "selectedIdChanged", selectedId: store.id })}><span className="store-index">{stores.indexOf(store) + 1}</span><span><strong>{store.name}</strong><small>{store.address}</small><small>{store.hours ?? "Hours not listed"}</small></span></button>)}{!stores.length && <p className="empty-state">No stores matched these filters.</p>}</div>
            {selectedDetail && <section className="place-detail" aria-labelledby="store-detail-title"><button className="detail-close" type="button" onClick={() => dispatch({ type: "selectedIdChanged" })} aria-label="Close store details">×</button><p className="panel-kicker">SELECTED PLACE</p><h3 id="store-detail-title">{selectedDetail.name}</h3><p>{selectedDetail.description ?? "No description has been added yet."}</p><dl><div><dt>Address</dt><dd>{selectedDetail.address}</dd></div><div><dt>Hours</dt><dd>{selectedDetail.hours ?? "Not listed"}</dd></div><div><dt>Activities</dt><dd>{selectedDetail.categories.join(", ")}</dd></div></dl><div className="actions">{selectedDetail.phone && <a href={`tel:${selectedDetail.phone}`}>Call</a>}<a href={directions!.app}>Directions</a><a href={directions!.web} target="_blank" rel="noreferrer">Open map</a></div><form className="feedback-form" onSubmit={submitFeedback}><label>Report an update<textarea name="content" required maxLength={2000} placeholder="Tell us what changed" /></label><input name="kind" type="hidden" value="CORRECTION" readOnly /><button className="text-action" type="submit">Send feedback</button>{view.feedbackStatus && <p role="status">{view.feedbackStatus}</p>}</form></section>}
          </section>}
        </aside>
      </section>
    </section>
  </main>;
}
