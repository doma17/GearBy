import Admin from "../../src/features/admin/admin";

export default function AdminPage() {
  return <Admin apiBaseUrl={process.env.API_BASE_URL ?? "http://localhost:8080"} />;
}
