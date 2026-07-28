import Discovery from "../src/features/discovery/discovery";

export default function Home() {
  return (
    <Discovery
      apiBaseUrl={process.env.API_BASE_URL ?? "http://localhost:8080"}
      naverMapClientId={process.env.NAVER_MAP_CLIENT_ID ?? ""}
    />
  );
}
