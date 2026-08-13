"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import type { components } from "../../generated/api";
import { readApiResponse } from "../../shared/api/api-response";

type Store = { id: string; name: string; address: string; categories: string[]; status: string };
type Rule = { id: string; source: string; targetType: "CATEGORY" | "STORE"; target: string; active: boolean };
type Feedback = { id: string; storeName?: string | null; kind: string; content: string; submittedAt: string; resolutionStatus: string; notificationStatus: string };
type CategoryHealth = { category: string; publishedStoreCount: number; storesByLifecycle: Record<string, number>; openReviewFlagCount: number };
type CategoryReviewFlag = components["schemas"]["CategoryReviewFlag"];
type Dashboard = { stores: Record<string, number>; feedback: Record<string, number>; activeCorrectionRules: number; categoryHealth: CategoryHealth[] };
type Session = components["schemas"]["AdminSession"];
type CandidateRun = components["schemas"]["CandidateRun"];
type CandidateItem = components["schemas"]["CandidateItem"];
type CandidateRunPage = components["schemas"]["CandidateRunPage"];
type CandidateItemPage = components["schemas"]["CandidateItemPage"];
type Category = components["schemas"]["CategorySlug"];

export default function Admin({ apiBaseUrl }: { apiBaseUrl: string }) {
  const api = `${apiBaseUrl.replace(/\/$/, "")}/api/v1/admin`;
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authenticated, setAuthenticated] = useState(false);
  const [csrfToken, setCsrfToken] = useState("");
  const [sessionReady, setSessionReady] = useState(false);
  const [stores, setStores] = useState<Store[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [feedback, setFeedback] = useState<Feedback[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard>();
  const [categoryHealth, setCategoryHealth] = useState<CategoryHealth[]>([]);
  const [categoryFlags, setCategoryFlags] = useState<CategoryReviewFlag[]>([]);
  const [candidateRuns, setCandidateRuns] = useState<CandidateRun[]>([]);
  const [candidateItems, setCandidateItems] = useState<CandidateItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function request(path: string, init?: RequestInit) {
    const requestHeaders = new Headers(init?.headers);
    if (init?.body) requestHeaders.set("Content-Type", "application/json");
    if (csrfToken && !["GET", "HEAD"].includes(init?.method ?? "GET")) requestHeaders.set("X-XSRF-TOKEN", csrfToken);
    const response = await fetch(`${api}${path}`, { ...init, credentials: "include", headers: requestHeaders });
    return readApiResponse(response);
  }

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const get = async (path: string) => readApiResponse(await fetch(`${api}${path}`, { credentials: "include" }));
      const [storeGroups, nextRules, nextFeedback, nextDashboard, nextCategoryHealth, nextCategoryFlags, nextCandidateRuns, nextCandidateItems] = await Promise.all([
        get("/stores"), get("/correction-rules"), get("/feedback"), get("/dashboard"), get("/category-health"), get("/category-review-flags"),
        get("/candidate-ingestion/runs?size=10"), get("/candidate-ingestion/items?size=50&latestOutcome=QUARANTINED"),
      ]);
      setStores(Object.entries(storeGroups as Record<string, components["schemas"]["Store"][]>).flatMap(([status, items]) => items.map((store) => ({ ...store, status }))));
      setRules(nextRules as Rule[]);
      setFeedback(nextFeedback as Feedback[]);
      setDashboard(nextDashboard as Dashboard);
      setCategoryHealth(nextCategoryHealth as CategoryHealth[]);
      setCategoryFlags(nextCategoryFlags as CategoryReviewFlag[]);
      setCandidateRuns((nextCandidateRuns as CandidateRunPage).items);
      setCandidateItems((nextCandidateItems as CandidateItemPage).items);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Operations could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [api]);

  const restoreSession = useCallback(async () => {
    try {
      const response = await fetch(`${api}/auth/session`, { credentials: "include" });
      const session = await readApiResponse<Session>(response);
      setAuthenticated(session.authenticated);
      setCsrfToken(session.csrfToken);
      if (session.authenticated) await load();
    } catch {
      setError("관리자 세션을 확인하지 못했습니다.");
    } finally {
      setSessionReady(true);
    }
  }, [api, load]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void restoreSession(); });
    return () => window.clearTimeout(timer);
  }, [restoreSession]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await fetch(`${api}/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}) },
        body: JSON.stringify({ email, password }),
      });
      const session = await readApiResponse<Session>(response);
      setAuthenticated(session.authenticated);
      setCsrfToken(session.csrfToken);
      setPassword("");
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그인하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    try {
      const response = await fetch(`${api}/auth/logout`, { method: "POST", credentials: "include", headers: { "X-XSRF-TOKEN": csrfToken } });
      const session = await readApiResponse<Session>(response);
      setAuthenticated(session.authenticated);
      setCsrfToken(session.csrfToken);
      setStores([]); setRules([]); setFeedback([]); setDashboard(undefined); setCategoryHealth([]); setCategoryFlags([]); setCandidateRuns([]); setCandidateItems([]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그아웃하지 못했습니다.");
    }
  }

  async function transitionStore(store: Store, action: "review" | "publish" | "reject") {
    const reason = action === "reject" ? window.prompt("Rejection reason (optional)")?.trim() : undefined;
    try { await request(`/stores/${store.id}/${action}`, { method: "POST", ...(reason ? { body: JSON.stringify({ reason }) } : {}) }); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Store update failed."); }
  }

  async function updateRule(rule: Rule) {
    try {
      await request(`/correction-rules/${rule.id}`, { method: "PATCH", body: JSON.stringify({ ...rule, active: !rule.active }) });
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Rule could not be updated."); }
  }

  async function deleteRule(rule: Rule) {
    try {
      await request(`/correction-rules/${rule.id}`, { method: "DELETE" });
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Rule could not be deleted."); }
  }

  async function addRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await request("/correction-rules", { method: "POST", body: JSON.stringify({ source: form.get("source"), targetType: form.get("targetType"), target: form.get("target") }) });
      event.currentTarget.reset();
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Rule could not be added."); }
  }

  async function updateCategoryFlag(id: string, state: "OPEN" | "RESOLVED") {
    try { await request(`/category-review-flags/${id}`, { method: "PATCH", body: JSON.stringify({ state }) }); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Category flag could not be updated."); }
  }

  async function addCategoryFlag(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try { await request("/category-review-flags", { method: "POST", body: JSON.stringify({ storeId: form.get("storeId") || null, reason: form.get("reason") }) }); event.currentTarget.reset(); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Category flag could not be added."); }
  }

  async function resolveFeedback(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await request(`/feedback/${id}`, { method: "PATCH", body: JSON.stringify({ resolutionStatus: form.get("resolutionStatus"), resolutionSummary: form.get("resolutionSummary") }) });
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Feedback update failed."); }
  }

  async function resolveCandidate(event: FormEvent<HTMLFormElement>, item: CandidateItem, resolutionType: "LINK_EXISTING" | "CREATE_DRAFT") {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const payload = resolutionType === "LINK_EXISTING"
      ? { resolutionType, storeId: form.get("storeId") }
      : {
          resolutionType,
          name: form.get("name"),
          address: form.get("address"),
          coordinates: { latitude: Number(form.get("latitude")), longitude: Number(form.get("longitude")) },
          categories: [form.get("category") as Category],
          phone: form.get("phone") || undefined,
        };
    try {
      await request(`/candidate-ingestion/items/${item.id}/resolve`, { method: "POST", body: JSON.stringify(payload) });
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "후보를 해결하지 못했습니다."); }
  }

  return <main className="admin">
    <header><p className="eyebrow">GEARBY ADMIN</p><h1>운영 관리</h1><p>매장, 카테고리, 검색 보정, 사용자 의견을 관리합니다.</p>{authenticated && <button type="button" onClick={() => void logout()}>로그아웃</button>}</header>
    {!sessionReady && <p role="status">관리자 세션을 확인하는 중입니다.</p>}
    {sessionReady && !authenticated && <form className="admin-token" onSubmit={(event) => void login(event)}><label>이메일<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /></label><label>비밀번호<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label><button>로그인</button></form>}
    {error && <p className="error" role="alert">{error}</p>}
    {loading && <p role="status">운영 정보를 불러오는 중입니다.</p>}
    {authenticated && <>
    <section className="admin-section"><h2>후보 수집 검수</h2><p className="admin-help">검수 대기 후보만 표시합니다. 연결은 기존 매장을 바꾸지 않으며, 초안 생성은 공개하지 않습니다.</p><div className="admin-cards">{candidateRuns.map((run) => <p key={run.id}><strong>{run.seenCount}</strong>{run.provider} · {run.status}<small>격리 {run.quarantinedCount} · 실패 {run.failedCount}</small></p>)}</div><div className="admin-list">{candidateItems.map((item) => <article key={item.id}><strong>{item.normalizedName}</strong><span>{item.roadAddress ?? "주소 없음"} · {item.provider} · {item.latestMatchStatus}</span><a href={item.sourceUrl} target="_blank" rel="noreferrer">출처 열기</a><form className="admin-rule" onSubmit={(event) => void resolveCandidate(event, item, "LINK_EXISTING")}><label>기존 매장<select name="storeId" required defaultValue=""><option value="" disabled>연결할 매장 선택</option>{stores.map((store) => <option key={store.id} value={store.id}>{store.name} · {store.status}</option>)}</select></label><button>기존 매장 연결</button></form><details><summary>검수용 초안 만들기</summary><form className="admin-rule candidate-draft" onSubmit={(event) => void resolveCandidate(event, item, "CREATE_DRAFT")}><label>매장명<input name="name" required defaultValue={item.normalizedName} /></label><label>주소<input name="address" required defaultValue={item.roadAddress ?? ""} /></label><label>위도<input name="latitude" type="number" step="any" required defaultValue={item.roundedLatitude ?? ""} /></label><label>경도<input name="longitude" type="number" step="any" required defaultValue={item.roundedLongitude ?? ""} /></label><label>카테고리<select name="category" defaultValue="HIKING"><option>HIKING</option><option>BACKPACKING</option><option>CAMPING</option><option>CLIMBING</option></select></label><label>전화<input name="phone" defaultValue={item.phone ?? ""} /></label><button>초안 생성</button></form></details></article>)}{!candidateItems.length && <p>검수 대기 후보가 없습니다.</p>}</div></section>
    {dashboard && <section className="admin-section"><h2>Dashboard</h2><div className="admin-cards">{Object.entries(dashboard.stores).map(([status, count]) => <p key={status}><strong>{count}</strong>{status.replaceAll("_", " ")}</p>)}<p><strong>{dashboard.activeCorrectionRules}</strong>active rules</p>{Object.entries(dashboard.feedback).map(([status, count]) => <p key={status}><strong>{count}</strong>feedback {status.replaceAll("_", " ").toLowerCase()}</p>)}</div><h3>Category health</h3><ul>{categoryHealth.map((item) => <li key={item.category} className={item.openReviewFlagCount ? "health-flag" : ""}>{item.category}: {item.publishedStoreCount} published · {item.openReviewFlagCount} open flags</li>)}</ul></section>}
    <section className="admin-section"><h2>Store review</h2><div className="admin-list">{stores.map((store) => <article key={store.id}><strong>{store.name}</strong><span>{store.address} · {store.categories.join(", ")} · {store.status}</span><div>{store.status === "DRAFT" || store.status === "REJECTED" ? <button onClick={() => void transitionStore(store, "review")}>Send to review</button> : null}{store.status === "IN_REVIEW" ? <><button onClick={() => void transitionStore(store, "publish")}>Publish</button><button onClick={() => void transitionStore(store, "reject")}>Reject</button></> : null}</div></article>)}{!stores.length && <p>No stores loaded.</p>}</div></section>
    <section className="admin-section"><h2>Correction rules</h2><form className="admin-rule" onSubmit={addRule}><label>Source<input name="source" required maxLength={120} /></label><label>Target type<select name="targetType" defaultValue="CATEGORY"><option>CATEGORY</option><option>STORE</option></select></label><label>Target<input name="target" required maxLength={200} /></label><button>Add rule</button></form><ul>{rules.map((rule) => <li key={rule.id}>{rule.source} → {rule.targetType}: {rule.target} {!rule.active && "(inactive)"} <button onClick={() => void updateRule(rule)}>{rule.active ? "Deactivate" : "Activate"}</button> <button onClick={() => void deleteRule(rule)}>Delete</button></li>)}</ul></section>
    <section className="admin-section"><h2>Category review</h2><form className="admin-rule" onSubmit={addCategoryFlag}><label>Store<select name="storeId" required><option value="" disabled>Select store</option>{stores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}</select></label><label>Manual review reason<input name="reason" required maxLength={500} /></label><button>Add flag</button></form><ul>{categoryFlags.map((flag) => <li key={flag.id}>{flag.storeName && `${flag.storeName} · `}{flag.source}: {flag.reason} · {flag.state} {flag.state === "OPEN" && <button onClick={() => void updateCategoryFlag(flag.id, "RESOLVED")}>Resolve</button>}</li>)}</ul></section>
    <section className="admin-section"><h2>Feedback</h2><div className="admin-list">{feedback.map((item) => <article key={item.id}><strong>{item.kind} {item.storeName && `· ${item.storeName}`}</strong><span>{item.content}</span><small>{new Date(item.submittedAt).toLocaleString()} · {item.resolutionStatus} · notification: {item.notificationStatus}</small>{item.resolutionStatus === "PENDING" && <form onSubmit={(event) => void resolveFeedback(event, item.id)}><label>Resolution<select name="resolutionStatus" defaultValue="RESOLVED"><option>RESOLVED</option><option>REJECTED</option></select></label><label>Summary<input name="resolutionSummary" required maxLength={1000} /></label><button>Save resolution</button></form>}</article>)}{!feedback.length && <p>No feedback loaded.</p>}</div></section>
    </>}
  </main>;
}
