import assert from "node:assert/strict";
import test from "node:test";
import { directionsUrls } from "./directions.mjs";

test("directions URLs preserve verified coordinates and encode the store name", () => {
  const urls = directionsUrls({
    name: "Gear & Go",
    address: "1 Map Street",
    coordinates: { latitude: 37.5665, longitude: 126.978 },
  }, "https://gearby.cloud");

  assert.match(urls.app, /^nmap:\/\/route\/public\?/);
  assert.match(urls.app, /dlat=37.5665/);
  assert.match(urls.app, /dlng=126.978/);
  assert.match(urls.app, /dname=Gear\+%26\+Go/);
  assert.match(urls.app, /appname=https%3A%2F%2Fgearby.cloud/);
  assert.equal(urls.web, "https://map.naver.com/p/search/Gear%20%26%20Go%201%20Map%20Street");
});
