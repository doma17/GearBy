import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { expect, it } from "vitest";

it("keeps the adaptive accessibility contract", async () => {
  const css = await readFile(resolve(process.cwd(), "app/styles.css"), "utf8");

  expect(css).toMatch(/@media \(max-width: 900px\)/);
  expect(css).toMatch(/@media \(max-width: 680px\)/);
  expect(css).toMatch(/\.map-app[^}]*font-family:/);
  expect(css).toMatch(/\.map-app a:focus-visible/);
  expect(css).toMatch(/\.panel-tabs button[^}]*min-height: 2\.75rem/);
  expect(css).toMatch(/\.chip[^}]*min-height: 3rem/);
  expect(css).toMatch(/\.detail-close[^}]*2\.75rem/);
  expect(css).toMatch(/\.store-list[^}]*align-content: start/);
  expect(css).toMatch(/\.places-content:has\(\.place-detail\)[^{]*\.store-list[^}]*display: none/);
  expect(css).toMatch(/\.workspace-notice \{ top: 10\.75rem; max-width: calc\(100% - 28rem\)/);
});
