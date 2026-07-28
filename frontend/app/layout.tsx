import type { Metadata } from "next";
import type { components } from "../src/generated/api";
import "./styles.css";

export const metadata: Metadata = { title: "GearBy", description: "아웃도어 장비점 탐색" };
type Health = components["schemas"]["Health"];

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const foundation: Health = { status: "UP" };
  return <html lang="ko"><body data-api-status={foundation.status}>{children}</body></html>;
}
