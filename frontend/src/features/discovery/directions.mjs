export function directionsUrls(store, appName) {
  const { latitude, longitude } = store.coordinates;
  const destination = new URLSearchParams({
    dlat: String(latitude),
    dlng: String(longitude),
    dname: store.name,
    appname: appName,
  });

  return {
    app: `nmap://route/public?${destination}`,
    web: `https://map.naver.com/p/search/${encodeURIComponent(`${store.name} ${store.address}`)}`,
  };
}
