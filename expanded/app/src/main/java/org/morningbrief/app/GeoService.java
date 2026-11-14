package org.morningbrief.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class GeoService {
    private GeoService() {}

    static LocationInfo lookupZip(String zip) throws Exception {
        String q = URLEncoder.encode(zip.trim(), StandardCharsets.UTF_8.toString());
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + q
                + "&count=10&language=en&format=json&countryCode=US";
        JSONObject root = new JSONObject(Net.getString(url, 300_000));
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) throw new Exception("ZIP code not found");

        JSONObject best = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            JSONArray postcodes = item.optJSONArray("postcodes");
            boolean exact = false;
            if (postcodes != null) {
                for (int j = 0; j < postcodes.length(); j++) {
                    if (zip.trim().equalsIgnoreCase(postcodes.optString(j))) {
                        exact = true;
                        break;
                    }
                }
            }
            if (exact) { best = item; break; }
            if (best == null) best = item;
        }
        if (best == null) throw new Exception("ZIP code not found");

        LocationInfo out = new LocationInfo();
        out.zip = zip.trim();
        out.city = best.optString("name", zip.trim());
        out.state = best.optString("admin1", "");
        out.county = best.optString("admin2", "");
        out.timezone = best.optString("timezone", "America/Detroit");
        out.latitude = best.getDouble("latitude");
        out.longitude = best.getDouble("longitude");
        return out;
    }
}
