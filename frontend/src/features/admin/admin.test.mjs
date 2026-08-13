import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./admin.tsx", import.meta.url), "utf8");

test("manual category flags require a real store selection", () => {
  assert(!source.includes('value="">General'));
  assert(source.includes('select name="storeId" required'));
  assert(source.includes('Manual review reason<input name="reason" required maxLength={500}'));
});

test("admin operations use the server session instead of storing a JWT", () => {
  assert(!source.includes("gearby-admin-token"));
  assert(source.includes('credentials: "include"'));
  assert(source.includes("/auth/login"));
  assert(source.includes("/auth/logout"));
});

test("candidate review uses the protected candidate-ingestion API", () => {
  assert(source.includes('/candidate-ingestion/runs?size=10'));
  assert(source.includes('/candidate-ingestion/items?size=50&latestOutcome=QUARANTINED'));
  assert(source.includes('resolutionType: "LINK_EXISTING"'));
  assert(source.includes('\"CREATE_DRAFT\"'));
});
