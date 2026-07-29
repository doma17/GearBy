"use client";

import Link from "next/link";
import { FormEvent, useEffect, useReducer, useRef, useState } from "react";
import type { components } from "../../generated/api";
import { readApiResponse } from "../../shared/api/api-response";
import { discoveryViewReducer, initialDiscoveryViewState, storeSearchParams } from "./discovery-state";

type Category = components["schemas"]["Category"];
type Store = components["schemas"]["Store"];
type StorePage = components["schemas"]["StorePage"];
type FeedbackKind = components["schemas"]["FeedbackInput"]["kind"];
type NaverLatLng = { lat: () => number; lng: () => number };
type NaverBounds = { getNE: () => NaverLatLng; getSW: () => NaverLatLng };
type NaverMap = { getBounds: () => NaverBounds; setCenter: (position: unknown) => void };
type NaverMarker = { setMap: (map: NaverMap | null) => void };
type NaverMaps = {
  Map: new (element: HTMLElement, options: { center: unknown; zoom: number }) => NaverMap;
  LatLng: new (latitude: number, longitude: number) => unknown;
  Marker: new (options: { position: unknown; map: NaverMap; title: string }) => NaverMarker;
  Event: { addListener: (target: NaverMap | NaverMarker, event: string, listener: () => void) => void };
};

declare global {
  interface Window { naver?: { maps: NaverMaps } }
}

const fallbackCategories: Category[] = [
  { slug: "HIKING", displayName: "등산" },
  { slug: "BACKPACKING", displayName: "백패킹" },
  { slug: "CAMPING", displayName: "캠핑" },
  { slug: "CLIMBING", displayName: "클라이밍" },
];

function loadNaverMaps(clientId: string) {
  if (window.naver?.maps) return Promise.resolve();
  const existing = document.querySelector<HTMLScriptElement>("script[data-naver-map]");
  if (existing) return new Promise<void>((resolve, reject) => {
    existing.addEventListener("load", () => resolve(), { once: true });
    existing.addEventListener("error", () => reject(new Error("NAVER 지도를 불러오지 못했습니다.")), { once: true });
  });

  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.dataset.naverMap = "true";
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}`;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("NAVER 지도를 불러오지 못했습니다."));
    document.head.append(script);
  });
}

/** Keeps the public map, result list, and store detail in sync. */
export default function Discovery({ apiBaseUrl, naverMapClientId }: { apiBaseUrl: string; naverMapClientId: string }) {
  const api = `${apiBaseUrl.replace(/\/$/, "")}/api/v1`;
  const mapElement = useRef<HTMLDivElement>(null);
  const map = useRef<NaverMap | null>(null);
  const markers = useRef<NaverMarker[]>([]);
  const userLocationMarker = useRef<NaverMarker | null>(null);
  const [categories, setCategories] = useState(fallbackCategories);
  const [categoryNotice, setCategoryNotice] = useState("");
  const [stores, setStores] = useState<Store[]>([]);
  const [search, setSearch] = useState<StorePage["search"]>();
  const [detail, setDetail] = useState<Store>();
  const [locationStatus, setLocationStatus] = useState("");
  const [userPosition, setUserPosition] = useState<{ latitude: number; longitude: number }>();
  const [panelVisible, setPanelVisible] = useState(true);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [retryRequest, setRetryRequest] = useState(0);
  const [view, dispatch] = useReducer(discoveryViewReducer, initialDiscoveryViewState);
  const searchParameters = storeSearchParams({ applyCorrection: view.applyCorrection, bbox: view.bbox, near: view.near, query: view.query, selectedCategories: view.selectedCategories, sort: view.sort }).toString();
  const requestKey = `${retryRequest}:${searchParameters}`;
  const [completedRequest, setCompletedRequest] = useState("");
  const loading = completedRequest !== requestKey;
  const [mapError, setMapError] = useState(naverMapClientId ? "" : "NAVER 지도가 설정되지 않았습니다. 아래 매장 목록을 이용해 주세요.");

  useEffect(() => {
    // Keep the local category fallback when the catalog endpoint is unavailable.
    fetch(`${api}/categories`).then(readApiResponse<Category[]>)
      .then((items) => { setCategories(items); setCategoryNotice(""); })
      .catch(() => setCategoryNotice("카테고리 정보를 불러오지 못해 기본 카테고리를 표시합니다."));
  }, [api]);

  useEffect(() => {
    // Abort stale searches so rapid filter changes cannot overwrite newer results.
    const controller = new AbortController();
    fetch(`${api}/stores?${searchParameters}`, { signal: controller.signal })
      .then((response) => readApiResponse<StorePage>(response))
      .then((page) => { setStores(page.items); setSearch(page.search); setCompletedRequest(requestKey); dispatch({ type: "errorChanged", error: "" }); })
      .catch((reason: Error) => { if (reason.name !== "AbortError") { setSearch(undefined); setCompletedRequest(requestKey); dispatch({ type: "errorChanged", error: "매장 정보를 불러오지 못했습니다." }); } });
    return () => controller.abort();
  }, [api, requestKey, searchParameters]);

  useEffect(() => {
    if (!view.selectedId) return;
    fetch(`${api}/stores/${view.selectedId}`).then((response) => readApiResponse<Store>(response))
      .then(setDetail).catch(() => dispatch({ type: "errorChanged", error: "매장 상세 정보를 불러오지 못했습니다." }));
  }, [api, view.selectedId]);

  useEffect(() => {
    if (!naverMapClientId || !mapElement.current) return;
    // The vendor SDK owns a global namespace, so load its script only once.
    loadNaverMaps(naverMapClientId).then(() => {
      const naver = window.naver?.maps;
      if (!naver || !mapElement.current) throw new Error("NAVER 지도를 사용할 수 없습니다.");
      if (!map.current) {
        map.current = new naver.Map(mapElement.current, { center: new naver.LatLng(37.5665, 126.978), zoom: 11 });
        naver.Event.addListener(map.current, "idle", () => {
          const bounds = map.current!.getBounds();
          const southWest = bounds.getSW();
          const northEast = bounds.getNE();
          dispatch({ type: "mapBoundsChanged", bbox: [southWest.lng(), southWest.lat(), northEast.lng(), northEast.lat()].join(",") });
        });
      }
      const activeMap = map.current;
      if (userPosition && activeMap) {
        const position = new naver.LatLng(userPosition.latitude, userPosition.longitude);
        activeMap.setCenter(position);
        userLocationMarker.current?.setMap(null);
        userLocationMarker.current = new naver.Marker({ position, map: activeMap, title: "내 위치" });
      }
      setMapError("");
    }).catch((reason: Error) => setMapError(`${reason.message} 아래 매장 목록을 이용해 주세요.`));
  }, [naverMapClientId, userPosition]);

  useEffect(() => {
    const naver = window.naver?.maps;
    if (!naver || !map.current) return;
    // Rebuild markers from the current result set to keep map and list state aligned.
    markers.current.forEach((marker) => marker.setMap(null));
    markers.current = stores.map((store) => {
      const marker = new naver.Marker({ position: new naver.LatLng(store.coordinates.latitude, store.coordinates.longitude), map: map.current!, title: store.name });
      naver.Event.addListener(marker, "click", () => {
        setPanelVisible(true);
        dispatch({ type: "selectedIdChanged", selectedId: store.id });
      });
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

  function closeDetail() {
    const selectedId = view.selectedId;
    setFeedbackOpen(false);
    dispatch({ type: "selectedIdChanged" });
    requestAnimationFrame(() => document.getElementById(`store-${selectedId}`)?.focus());
  }

  function useLocation() {
    setLocationStatus("");
    if (!navigator.geolocation) { dispatch({ type: "errorChanged", error: "이 브라우저에서는 위치 정보를 사용할 수 없습니다." }); return; }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        const near = `${coords.latitude},${coords.longitude}`;
        dispatch({ type: "locationResolved", near });
        setUserPosition({ latitude: coords.latitude, longitude: coords.longitude });
        setLocationStatus("현재 위치를 기준으로 거리순 매장을 표시합니다.");
      },
      () => dispatch({ type: "errorChanged", error: "현재 위치를 가져오지 못했습니다. 브라우저 위치 권한을 확인해 주세요." }),
      { enableHighAccuracy: true, maximumAge: 60_000, timeout: 10_000 },
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
      dispatch({ type: "feedbackStatusChanged", feedbackStatus: "의견을 검토 요청으로 보냈습니다." });
      event.currentTarget.reset();
      setFeedbackOpen(false);
    } catch {
      dispatch({ type: "feedbackStatusChanged", feedbackStatus: "의견을 보내지 못했습니다. 다시 시도해 주세요." });
    }
  }

  const selectedDetail = detail?.id === view.selectedId ? detail : undefined;

  return <main className="map-app">
    <aside className="nav-rail" aria-label="주요 탐색">
      <Link className="brand-mark" href="/" aria-label="GearBy 홈">G</Link>
      <nav>
        <Link className="rail-link active" href="/" aria-current="page"><span aria-hidden="true">⌖</span><span>탐색</span></Link>
        <button className="rail-link" type="button" onClick={() => { setPanelVisible(true); dispatch({ type: "panelChanged", panel: "filters" }); }}><span aria-hidden="true">⌘</span><span>필터</span></button>
      </nav>
    </aside>

    <section className="map-workspace">
      <header className="map-toolbar">
        <div className="toolbar-title"><p className="eyebrow">GEARBY</p><h1>NearBy Gear</h1></div>
        <form className="search-field" role="search" onSubmit={(event) => { event.preventDefault(); dispatch({ type: "querySubmitted" }); }}><span aria-hidden="true">⌕</span><input value={view.queryDraft} onChange={(event) => dispatch({ type: "queryChanged", query: event.target.value })} placeholder="매장 · 브랜드 · 활동" aria-label="검색" /><button className="text-action" type="submit">검색</button></form>
        <button className="location-button" type="button" onClick={useLocation}>내 위치</button>
      </header>

      {loading && <p className="notice workspace-notice" role="status">매장을 불러오는 중입니다.</p>}
      {!loading && view.error && <div className="error workspace-notice" role="alert"><span>{view.error}</span>{view.error === "매장 정보를 불러오지 못했습니다." && <button className="text-action" type="button" onClick={() => setRetryRequest((attempt) => attempt + 1)}>다시 시도</button>}</div>}
      {!loading && search?.correction && <div className="notice workspace-notice" role="status">“{search.appliedQuery}” 검색 결과입니다. 원문: “{search.originalQuery}”. <button className="text-action" type="button" onClick={() => dispatch({ type: "correctionRolledBack", query: search.originalQuery })}>원문으로 검색</button></div>}
      {locationStatus && <p className="notice location-notice" role="status">{locationStatus}</p>}

      <section className="map-stage" aria-label="매장 탐색">
        <div className="map-canvas">
          <div className="map" ref={mapElement} aria-label="매장 지도" />
          {!naverMapClientId && <div className="map-grid" aria-hidden="true" />}
          {mapError && <p className="map-fallback" role="status">{mapError}</p>}
          {view.pendingBbox && view.pendingBbox !== view.bbox && <button className="primary-action area-search" type="button" onClick={() => dispatch({ type: "mapBoundsCommitted" })}>이 지역 검색</button>}
          <p className="map-caption">GEARBY / CURATED STORES</p>
        </div>

        <button
          className={panelVisible ? "panel-toggle panel-open" : "panel-toggle"}
          type="button"
          aria-label={panelVisible ? "목록 닫기" : "목록 열기"}
          aria-controls="places-panel"
          aria-expanded={panelVisible}
          onClick={() => setPanelVisible((visible) => !visible)}
        >
          {panelVisible ? "×" : "<<"}
        </button>

        <aside id="places-panel" className={panelVisible ? "places-panel" : "places-panel is-hidden"} aria-label="매장 검색 결과" aria-hidden={!panelVisible}>
          <div className="panel-tabs" role="tablist" aria-label="탐색 패널">
            <button type="button" role="tab" aria-selected={view.activePanel === "places"} className={view.activePanel === "places" ? "active" : ""} onClick={() => dispatch({ type: "panelChanged", panel: "places" })}>매장</button>
            <button type="button" role="tab" aria-selected={view.activePanel === "filters"} className={view.activePanel === "filters" ? "active" : ""} onClick={() => dispatch({ type: "panelChanged", panel: "filters" })}>필터</button>
          </div>
          {categoryNotice && <p className="notice category-notice" role="status">{categoryNotice}</p>}

          {view.activePanel === "filters" ? <section className="filter-panel">
            <div><p className="panel-kicker">FILTERS</p><h2>Find your next stop</h2><p>활동과 정렬을 선택하세요.</p></div>
            <fieldset><legend>활동</legend><div className="chips">{categories.map((category) => <button type="button" className={view.selectedCategories.includes(category.slug) ? "chip selected" : "chip"} aria-pressed={view.selectedCategories.includes(category.slug)} key={category.slug} onClick={() => toggleCategory(category.slug)}>{category.displayName}</button>)}</div></fieldset>
            <label>검색 결과 정렬<select value={view.sort} onChange={(event) => dispatch({ type: "sortChanged", sort: event.target.value as "name" | "distance" })}><option value="name">이름순</option><option value="distance" disabled={!view.near}>거리순</option></select></label>
            <button className="primary-action" type="button" onClick={() => dispatch({ type: "panelChanged", panel: "places" })}>{stores.length}개 매장 보기</button>
          </section> : <section className="places-content" aria-live="polite">
            <div className="panel-heading"><div><p className="panel-kicker">CURATED STORES</p><h2>{stores.length} places</h2></div><button type="button" className="filter-trigger" onClick={() => dispatch({ type: "panelChanged", panel: "filters" })}>필터</button></div>
            <div className="store-list">{stores.map((store) => <button id={`store-${store.id}`} type="button" key={store.id} className={store.id === view.selectedId ? "store-card selected" : "store-card"} onClick={() => dispatch({ type: "selectedIdChanged", selectedId: store.id })}><span className="store-index">{stores.indexOf(store) + 1}</span><span><strong>{store.name}</strong><small>{store.address}</small><small>{store.hours ?? "영업시간 정보 없음"}</small></span></button>)}{!stores.length && <p className="empty-state">조건에 맞는 매장이 없습니다.</p>}</div>
            {selectedDetail && <section className="place-detail" aria-labelledby="store-detail-title"><div className="detail-controls"><button className="report-button" type="button" aria-label="매장 정보 신고" onClick={() => { dispatch({ type: "feedbackStatusChanged", feedbackStatus: "" }); setFeedbackOpen(true); }}>🚨</button><button className="detail-close" type="button" onClick={closeDetail} aria-label="매장 상세 닫기">×</button></div><p className="panel-kicker">선택한 매장</p><h3 id="store-detail-title">{selectedDetail.name}</h3><p>{selectedDetail.description ?? "등록된 설명이 없습니다."}</p><dl><div><dt>주소</dt><dd>{selectedDetail.address}</dd></div><div><dt>영업시간</dt><dd>{selectedDetail.hours ?? "정보 없음"}</dd></div><div><dt>활동</dt><dd>{selectedDetail.categories.map((slug) => categories.find((category) => category.slug === slug)?.displayName ?? slug).join(", ")}</dd></div></dl><div className="actions"><a href={`https://map.naver.com/p/search/${encodeURIComponent(`${selectedDetail.name} ${selectedDetail.address}`)}`} target="_blank" rel="noreferrer">Naver Map Link</a></div>{view.feedbackStatus && <p className="feedback-status" role="status">{view.feedbackStatus}</p>}</section>}
          </section>}
        </aside>
        {feedbackOpen && selectedDetail && <div className="feedback-backdrop" onClick={() => setFeedbackOpen(false)}><section className="feedback-dialog" role="dialog" aria-modal="true" aria-labelledby="feedback-title" onClick={(event) => event.stopPropagation()}><button className="dialog-close" type="button" onClick={() => setFeedbackOpen(false)} aria-label="신고 창 닫기">×</button><p className="panel-kicker">REPORT STORE</p><h2 id="feedback-title">매장 정보 신고</h2><p>{selectedDetail.name} 정보에 문제가 있나요?</p><form className="feedback-form" onSubmit={submitFeedback}><label>문제 내용<textarea name="content" required maxLength={2000} placeholder="잘못되었거나 변경된 정보를 알려주세요" /></label><input name="kind" type="hidden" value="CORRECTION" readOnly /><div className="dialog-actions"><button className="text-action" type="button" onClick={() => setFeedbackOpen(false)}>취소</button><button className="primary-action" type="submit">전송</button></div>{view.feedbackStatus && <p role="status">{view.feedbackStatus}</p>}</form></section></div>}
      </section>
    </section>
  </main>;
}
