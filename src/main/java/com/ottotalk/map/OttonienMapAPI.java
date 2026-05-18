package com.ottotalk.map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ottotalk.OttoTalkClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client gegen die Ottonien Map API (mcp-api.ottonien.com).
 * Zieht Region- und Faction-Daten die auch karte.ottonien.com nutzt.
 */
public class OttonienMapAPI {
    private static final String BASE_URL = "https://mcp-api.ottonien.com/api/public";
    private static final String REGIONS_URL = BASE_URL + "/regions";
    private static final String FACTIONS_URL = BASE_URL + "/factions";

    private static final Gson GSON = new Gson();
    private final OkHttpClient client;

    public OttonienMapAPI() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Alle Lehen von der API holen
     */
    public CompletableFuture<List<RegionData>> fetchRegions() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(REGIONS_URL)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        OttoTalkClient.LOGGER.warn("[OttonienMap] Failed to fetch regions: HTTP {}", response.code());
                        return Collections.emptyList();
                    }

                    String json = response.body().string();
                    Type listType = new TypeToken<List<RegionData>>() {}.getType();
                    List<RegionData> regions = GSON.fromJson(json, listType);
                    return regions;
                }
            } catch (Exception e) {
                OttoTalkClient.LOGGER.error("[OttonienMap] Error fetching regions", e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * Alle Gefolge von der API holen
     */
    public CompletableFuture<List<FactionData>> fetchFactions() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(FACTIONS_URL)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        OttoTalkClient.LOGGER.warn("[OttonienMap] Failed to fetch factions: HTTP {}", response.code());
                        return Collections.emptyList();
                    }

                    String json = response.body().string();
                    Type listType = new TypeToken<List<FactionData>>() {}.getType();
                    List<FactionData> factions = GSON.fromJson(json, listType);
                    return factions;
                }
            } catch (Exception e) {
                OttoTalkClient.LOGGER.error("[OttonienMap] Error fetching factions", e);
                return Collections.emptyList();
            }
        });
    }
}
