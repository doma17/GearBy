import { describe, expect, it } from "vitest";
import { readApiResponse } from "./api-response";

describe("readApiResponse", () => {
  it("returns data from a successful envelope", async () => {
    await expect(readApiResponse<{ id: string }>(new Response(JSON.stringify({ success: true, data: { id: "store-1" } })))).resolves.toEqual({ id: "store-1" });
  });

  it("uses the API error message from a failed envelope", async () => {
    await expect(readApiResponse(new Response(JSON.stringify({ success: false, error: { message: "invalid filter" } }), { status: 400 }))).rejects.toThrow("invalid filter");
  });
});
