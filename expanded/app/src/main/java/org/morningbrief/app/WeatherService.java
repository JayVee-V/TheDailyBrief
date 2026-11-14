package org.morningbrief.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class WeatherService {
    private WeatherService() {}

    static WeatherData fetch(LocationInfo loc) throws Exception {
        String tz = URLEncoder.encode(loc.timezone == null || loc.timezone.trim().isEmpty() ? "auto" : loc.timezone,
                StandardCharsets.UTF_8.toString());
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + loc.latitude
                + "&longitude=" + loc.longitude
                + "&hourly=temperature_2m,precipitation_probability,weather_code"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&temperature_unit=fahrenheit&forecast_days=3&timezone=" + tz;
        JSONObject root = new JSONObject(Net.getString(url, 800_000));
        JSONObject hourly = root.getJSONObject("hourly");
        JSONObject daily = root.getJSONObject("daily");

        WeatherData out = new WeatherData();
        out.label = (loc.label() + " WEATHER").toUpperCase(Locale.US);

        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray rain = hourly.getJSONArray("precipitation_probability");
        JSONArray codes = hourly.getJSONArray("weather_code");
        int[] targetHours = {6, 11, 16, 21};
        String[] labels = {"6 AM", "11 AM", "4 PM", "9 PM"};
        boolean[] filled = new boolean[targetHours.length];
        String today = times.length() > 0 ? times.optString(0).substring(0, 10) : "";
        for (int i = 0; i < times.length(); i++) {
            String raw = times.optString(i, "");
            if (!raw.startsWith(today)) continue;
            try {
                int hour = LocalDateTime.parse(raw).getHour();
                for (int j = 0; j < targetHours.length; j++) {
                    if (!filled[j] && hour == targetHours[j]) {
                        WeatherData.Hour h = new WeatherData.Hour();
                        h.label = labels[j];
                        h.temp = (int) Math.round(temps.optDouble(i));
                        h.rain = rain.isNull(i) ? 0 : rain.optInt(i, 0);
                        h.condition = condition(codes.optInt(i, 0));
                        out.hours.add(h);
                        filled[j] = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        JSONArray dates = daily.getJSONArray("time");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        JSONArray dailyRain = daily.getJSONArray("precipitation_probability_max");

        Map<String, Integer> dayCodes = new HashMap<>();
        for (int i = 0; i < times.length(); i++) {
            String raw = times.optString(i, "");
            if (raw.length() < 13) continue;
            try {
                LocalDateTime dt = LocalDateTime.parse(raw);
                if (dt.getHour() == 14) dayCodes.put(raw.substring(0, 10), codes.optInt(i, 0));
            } catch (Exception ignored) {}
        }

        for (int i = 0; i < Math.min(3, dates.length()); i++) {
            WeatherData.Day d = new WeatherData.Day();
            String ds = dates.optString(i);
            try {
                d.label = java.time.LocalDate.parse(ds).format(DateTimeFormatter.ofPattern("EEE", Locale.US)).toUpperCase(Locale.US);
            } catch (Exception e) {
                d.label = ds;
            }
            d.high = (int) Math.round(highs.optDouble(i));
            d.low = (int) Math.round(lows.optDouble(i));
            d.rain = dailyRain.isNull(i) ? 0 : dailyRain.optInt(i, 0);
            d.condition = condition(dayCodes.getOrDefault(ds, 0));
            out.days.add(d);
        }
        out.available = true;
        return out;
    }

    static String condition(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1 -> "Mostly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61 -> "Light rain";
            case 63 -> "Rain";
            case 65 -> "Heavy rain";
            case 66, 67 -> "Freezing rain";
            case 71 -> "Light snow";
            case 73 -> "Snow";
            case 75, 77 -> "Heavy snow";
            case 80, 81 -> "Rain showers";
            case 82 -> "Heavy showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Storm + hail";
            default -> "Weather";
        };
    }
}
