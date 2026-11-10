export async function searchPlaces(query: string, countryCode: string) {
  const url = new URL("https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/suggest");
  url.searchParams.set("text", query);
  url.searchParams.set("f", "json");
  url.searchParams.set("maxSuggestions", "5");
  if (countryCode) url.searchParams.set("countryCode", countryCode);
  const response = await fetch(url);
  if (!response.ok) throw new Error("Place search failed");
  const data = (await response.json()) as { suggestions?: Array<{ text?: string }> };
  const seen = new Set<string>();
  const places: string[] = [];
  for (const item of data.suggestions ?? []) {
    const text = item.text?.trim();
    if (!text || seen.has(text)) continue;
    seen.add(text);
    places.push(text);
  }
  return places;
}

export async function locatePlace(address: string) {
  const url = new URL("https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates");
  url.searchParams.set("SingleLine", address);
  url.searchParams.set("f", "json");
  url.searchParams.set("maxLocations", "1");
  const response = await fetch(url);
  if (!response.ok) return null;
  const data = (await response.json()) as { candidates?: Array<{ location?: { x?: number; y?: number } }> };
  const location = data.candidates?.[0]?.location;
  if (typeof location?.x !== "number" || typeof location.y !== "number") return null;
  return { lat: location.y, lng: location.x };
}