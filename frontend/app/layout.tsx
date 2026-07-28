import type { Metadata } from "next";
import type { components } from "../src/generated/api";
import "./styles.css";

export const metadata: Metadata = { title: "GearBy", description: "GearBy foundation" };
type Health = components["schemas"]["Health"];

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const foundation: Health = { status: "UP" };
  return <html lang="en"><body data-api-status={foundation.status}>{children}</body></html>;
}
