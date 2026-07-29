import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const css = await readFile(new URL("../../../app/styles.css", import.meta.url), "utf8");

test("public discovery keeps its adaptive accessibility contract", () => {
  assert.match(css, /@media \(max-width: 900px\)/);
  assert.match(css, /@media \(max-width: 680px\)/);
  assert.match(css, /\.map-app[^}]*font-family:/);
  assert.match(css, /\.map-app a:focus-visible/);
  assert.match(css, /\.panel-tabs button[^}]*min-height: 2\.75rem/);
  assert.match(css, /\.chip[^}]*min-height: 3rem/);
  assert.match(css, /\.detail-close[^}]*2\.75rem/);
  assert.match(css, /\.store-list[^}]*align-content: start/);
  assert.match(css, /\.places-content:has\(\.place-detail\)[^{]*\.store-list[^}]*display: none/);
  assert.match(css, /\.workspace-notice \{ top: 10\.75rem; max-width: calc\(100% - 28rem\)/);
});
