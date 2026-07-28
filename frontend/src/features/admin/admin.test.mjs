import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./admin.tsx", import.meta.url), "utf8");

test("manual category flags require a real store selection", () => {
  assert(!source.includes('value="">General'));
  assert(source.includes('select name="storeId" required'));
  assert(source.includes('Manual review reason<input name="reason" required maxLength={500}'));
});
