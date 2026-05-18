package com.ottotalk.map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ottotalk.OttoTalkClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holt und cacht die Ottonien Map-Daten (Lehen + Fraktionen) und merged sie zusammen.
 * Den fertigen Kram fragt der Overlay-Renderer dann ab.
 */
public class MapDataManager {
    private static MapDataManager instance;

    private static final String POLYGON_RESOURCE = "/assets/ottotalk/lehen_polygons.json";

    private final OttonienMapAPI api;
    private List<RegionData> regions = Collections.emptyList();
    private List<FactionData> factions = Collections.emptyList();
    private Map<String, RegionData> regionById = Collections.emptyMap();
    private Map<String, FactionData> factionByRegion = Collections.emptyMap();
    private Map<String, int[][]> polygonData = Collections.emptyMap();

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private boolean dataLoaded = false;
    private long lastFetchTime = 0;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private MapDataManager() {
        this.api = new OttonienMapAPI();
        loadPolygonData();
    }

    public static MapDataManager getInstance() {
        if (instance == null) {
            instance = new MapDataManager();
        }
        return instance;
    }

    /**
     * Daten-Refresh anstoßen wenn nötig regelmäßig oder beim Öffnen der Karte.
     */
    public void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (!dataLoaded || (now - lastFetchTime > REFRESH_INTERVAL_MS)) {
            fetchData();
        }
    }

    /**
     * Force fetch fresh data from the API.
     */
    public void fetchData() {
        if (loading.getAndSet(true)) {
            return; // lädt eh schon
        }

        CompletableFuture<List<RegionData>> regionsFuture = api.fetchRegions();
        CompletableFuture<List<FactionData>> factionsFuture = api.fetchFactions();

        CompletableFuture.allOf(regionsFuture, factionsFuture).thenRun(() -> {
            try {
                List<RegionData> fetchedRegions = regionsFuture.join();
                List<FactionData> fetchedFactions = factionsFuture.join();

                if (!fetchedRegions.isEmpty()) {
                    mergeData(fetchedRegions, fetchedFactions);
                    dataLoaded = true;
                    lastFetchTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                OttoTalkClient.LOGGER.error("[OttonienMap] Error merging data", e);
            } finally {
                loading.set(false);
            }
        });
    }

    /**
     * Regions und Fraktionen zusammenführen jede Fraktion ihrer Region zuweisen.
     */
    private void mergeData(List<RegionData> newRegions, List<FactionData> newFactions) {
        Map<String, RegionData> byId = new HashMap<>();
        for (RegionData region : newRegions) {
            byId.put(region.getId(), region);
        }

        Map<String, FactionData> byRegion = new HashMap<>();
        for (FactionData faction : newFactions) {
            String regionId = faction.getRegion();
            if (regionId != null && !regionId.isEmpty()) {
                byRegion.put(regionId, faction);
                RegionData region = byId.get(regionId);
                if (region != null) {
                    region.setFaction(faction);
                }
            }
        }

        // Polygon-Daten an die Regionen dranhängen
        for (RegionData region : byId.values()) {
            int[][] poly = polygonData.get(region.getId());
            if (poly != null) {
                region.setPolygon(poly);
            }
        }

        // apply local overrides for Lehen not yet in teh API
        applyLocalOverrides(byId, byRegion);

        // Swap all four collections together so renderers never observe a partial state.
        this.regionById = byId;
        this.factionByRegion = byRegion;
        this.regions = new ArrayList<>(newRegions);
        this.factions = new ArrayList<>(newFactions);
    }

    /**
     * Lokale Fraktions-Overrides für Lehen die noch nicht in der Live-API stehen.
     * wird nur angewendet wenn die Region grad keine Fraktion aus der API hat.
     */
    private static void applyLocalOverrides(Map<String, RegionData> byId, Map<String, FactionData> byRegion) {
        applyIfMissing(byId, byRegion, "lehen_27", "Holdern", "Herzogtum", 95, "ottonien");
    }

    private static void applyIfMissing(Map<String, RegionData> byId, Map<String, FactionData> byRegion,
                                        String lehenId, String name, String rankName, int rankPriority, String banner) {
        if (byRegion.containsKey(lehenId)) return; // API hat schon Daten also skip
        FactionData override = FactionData.localOverride(name, lehenId, rankName, rankPriority, banner);
        byRegion.put(lehenId, override);
        RegionData region = byId.get(lehenId);
        if (region != null) region.setFaction(override);
    }

    /**
     * Load polygon border data from bundled JSON resource.
     */
    private void loadPolygonData() {
        try (InputStream is = getClass().getResourceAsStream(POLYGON_RESOURCE)) {
            if (is == null) {
                OttoTalkClient.LOGGER.warn("[OttonienMap] Polygon resource not found: {}", POLYGON_RESOURCE);
                return;
            }
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject polygons = root.getAsJsonObject("polygons");
            if (polygons == null) return;

            Map<String, int[][]> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : polygons.entrySet()) {
                JsonArray vertArray = entry.getValue().getAsJsonArray();
                int[][] vertices = new int[vertArray.size()][2];
                for (int i = 0; i < vertArray.size(); i++) {
                    JsonArray point = vertArray.get(i).getAsJsonArray();
                    vertices[i][0] = point.get(0).getAsInt();
                    vertices[i][1] = point.get(1).getAsInt();
                }
                loaded.put(entry.getKey(), vertices);
            }
            this.polygonData = loaded;
        } catch (Exception e) {
            OttoTalkClient.LOGGER.error("[OttonienMap] Error loading polygon data", e);
        }
    }

    /**
     * rausfinden in welcher Region der Spieler grad ist über den nächsten Center Point.
     * einfaches Nearest-Neighbor mit den Region Center Koordinaten.
     */
    public RegionData getRegionAt(double playerX, double playerZ) {
        if (regions.isEmpty()) return null;

        RegionData nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (RegionData region : regions) {
            double dx = playerX - region.getX();
            double dz = playerZ - region.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = region;
            }
        }

        return nearest;
    }

    /**
     * Alle Regionen innerhalb einer Bounding Box zum Rendern der sichtbaren Regions.
     */
    public List<RegionData> getRegionsInBounds(double minX, double minZ, double maxX, double maxZ) {
        List<RegionData> result = new ArrayList<>();
        // mit Margin damit Labels für Regionen knapp ausserhalb auch erscheinen
        double margin = 500;
        for (RegionData region : regions) {
            if (region.getX() >= minX - margin && region.getX() <= maxX + margin
                    && region.getZ() >= minZ - margin && region.getZ() <= maxZ + margin) {
                result.add(region);
            }
        }
        return result;
    }

    public List<RegionData> getRegions() { return regions; }
    public List<FactionData> getFactions() { return factions; }
    public boolean isDataLoaded() { return dataLoaded; }
    public boolean isLoading() { return loading.get(); }

    public RegionData getRegionById(String id) {
        return regionById.get(id);
    }

    public FactionData getFactionForRegion(String regionId) {
        return factionByRegion.get(regionId);
    }
}
