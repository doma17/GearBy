"use client";

import { FormEvent, useMemo, useState } from "react";
import type { components } from "../../generated/api";
import { readApiResponse } from "../../shared/api/api-response";

type Store = { id: string; name: string; address: string; categories: string[]; status: string };
type Rule = { id: string; source: string; targetType: "CATEGORY" | "STORE"; target: string; active: boolean };
type Feedback = { id: string; storeName?: string | null; kind: string; content: string; submittedAt: string; resolutionStatus: string; notificationStatus: string };
type CategoryHealth = { category: string; publishedStoreCount: number; storesByLifecycle: Record<string, number>; openReviewFlagCount: number };
type CategoryReviewFlag = components["schemas"]["CategoryReviewFlag"];
type Dashboard = { stores: Record<string, number>; feedback: Record<string, number>; activeCorrectionRules: number; categoryHealth: CategoryHealth[] };

export default function Admin({ apiBaseUrl }: { apiBaseUrl: string }) {
  const api = `${apiBaseUrl.replace(/\/$/, "")}/api/v1/admin`;
  const [token, setToken] = useState(() => typeof window === "undefined" ? "" : sessionStorage.getItem("gearby-admin-token") ?? "");
  const [stores, setStores] = useState<Store[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [feedback, setFeedback] = useState<Feedback[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard>();
  const [categoryHealth, setCategoryHealth] = useState<CategoryHealth[]>([]);
  const [categoryFlags, setCategoryFlags] = useState<CategoryReviewFlag[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const headers = useMemo(() => {
    const value = new Headers();
    if (token) value.set("Authorization", `Bearer ${token}`);
    return value;
  }, [token]);

  async function request(path: string, init?: RequestInit) {
    // Preserve the current admin JWT while allowing each action to add its own headers.
    const requestHeaders = new Headers(headers);
    new Headers(init?.headers).forEach((value, key) => requestHeaders.set(key, value));
    requestHeaders.set("Content-Type", "application/json");
    const response = await fetch(`${api}${path}`, { ...init, headers: requestHeaders });
    return readApiResponse(response);
  }

  async function load() {
    if (!token) { setError("Enter an ADMIN JWT to load operations."); return; }
    setLoading(true);
    try {
      const [storeGroups, nextRules, nextFeedback, nextDashboard, nextCategoryHealth, nextCategoryFlags] = await Promise.all([
        request("/stores"), request("/correction-rules"), request("/feedback"), request("/dashboard"), request("/category-health"), request("/category-review-flags"),
      ]);
      setStores(Object.entries(storeGroups as Record<string, components["schemas"]["Store"][]>).flatMap(([status, items]) => items.map((store) => ({ ...store, status }))));
      setRules(nextRules as Rule[]);
      setFeedback(nextFeedback as Feedback[]);
      setDashboard(nextDashboard as Dashboard);
      setCategoryHealth(nextCategoryHealth as CategoryHealth[]);
      setCategoryFlags(nextCategoryFlags as CategoryReviewFlag[]);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Operations could not be loaded.");
    } finally {
      setLoading(false);
    }
  }

  function saveToken(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    sessionStorage.setItem("gearby-admin-token", token);
    void load();
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

  return <main className="admin">
    <header><p className="eyebrow">GEARBY ADMIN</p><h1>Operations</h1><p>Review stores, category consistency, corrections, and feedback.</p></header>
    <form className="admin-token" onSubmit={saveToken}><label>ADMIN JWT<input type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" /></label><button>Load operations</button></form>
    {error && <p className="error" role="alert">{error}</p>}
    {loading && <p role="status">Loading operations…</p>}
    {dashboard && <section className="admin-section"><h2>Dashboard</h2><div className="admin-cards">{Object.entries(dashboard.stores).map(([status, count]) => <p key={status}><strong>{count}</strong>{status.replaceAll("_", " ")}</p>)}<p><strong>{dashboard.activeCorrectionRules}</strong>active rules</p>{Object.entries(dashboard.feedback).map(([status, count]) => <p key={status}><strong>{count}</strong>feedback {status.replaceAll("_", " ").toLowerCase()}</p>)}</div><h3>Category health</h3><ul>{categoryHealth.map((item) => <li key={item.category} className={item.openReviewFlagCount ? "health-flag" : ""}>{item.category}: {item.publishedStoreCount} published · {item.openReviewFlagCount} open flags</li>)}</ul></section>}
    <section className="admin-section"><h2>Store review</h2><div className="admin-list">{stores.map((store) => <article key={store.id}><strong>{store.name}</strong><span>{store.address} · {store.categories.join(", ")} · {store.status}</span><div>{store.status === "DRAFT" || store.status === "REJECTED" ? <button onClick={() => void transitionStore(store, "review")}>Send to review</button> : null}{store.status === "IN_REVIEW" ? <><button onClick={() => void transitionStore(store, "publish")}>Publish</button><button onClick={() => void transitionStore(store, "reject")}>Reject</button></> : null}</div></article>)}{!stores.length && <p>No stores loaded.</p>}</div></section>
    <section className="admin-section"><h2>Correction rules</h2><form className="admin-rule" onSubmit={addRule}><label>Source<input name="source" required maxLength={120} /></label><label>Target type<select name="targetType" defaultValue="CATEGORY"><option>CATEGORY</option><option>STORE</option></select></label><label>Target<input name="target" required maxLength={200} /></label><button>Add rule</button></form><ul>{rules.map((rule) => <li key={rule.id}>{rule.source} → {rule.targetType}: {rule.target} {!rule.active && "(inactive)"} <button onClick={() => void updateRule(rule)}>{rule.active ? "Deactivate" : "Activate"}</button> <button onClick={() => void deleteRule(rule)}>Delete</button></li>)}</ul></section>
    <section className="admin-section"><h2>Category review</h2><form className="admin-rule" onSubmit={addCategoryFlag}><label>Store<select name="storeId" required><option value="" disabled>Select store</option>{stores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}</select></label><label>Manual review reason<input name="reason" required maxLength={500} /></label><button>Add flag</button></form><ul>{categoryFlags.map((flag) => <li key={flag.id}>{flag.storeName && `${flag.storeName} · `}{flag.source}: {flag.reason} · {flag.state} {flag.state === "OPEN" && <button onClick={() => void updateCategoryFlag(flag.id, "RESOLVED")}>Resolve</button>}</li>)}</ul></section>
    <section className="admin-section"><h2>Feedback</h2><div className="admin-list">{feedback.map((item) => <article key={item.id}><strong>{item.kind} {item.storeName && `· ${item.storeName}`}</strong><span>{item.content}</span><small>{new Date(item.submittedAt).toLocaleString()} · {item.resolutionStatus} · notification: {item.notificationStatus}</small>{item.resolutionStatus === "PENDING" && <form onSubmit={(event) => void resolveFeedback(event, item.id)}><label>Resolution<select name="resolutionStatus" defaultValue="RESOLVED"><option>RESOLVED</option><option>REJECTED</option></select></label><label>Summary<input name="resolutionSummary" required maxLength={1000} /></label><button>Save resolution</button></form>}</article>)}{!feedback.length && <p>No feedback loaded.</p>}</div></section>
  </main>;
}
