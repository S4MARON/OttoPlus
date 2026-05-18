package com.ottotalk.map;

import java.util.List;

/**
 * Ein Lehen aus der Ottonien Map API. Daten kommen vom /api/public/regions endpoint.
 */
public class RegionData {
    private String id;           // e.g. "lehen_1"
    private String name;         // e.g. "Klepburg"
    private int x;               // Minecraft X coordinate (center)
    private int y;               // Minecraft Y coordinate
    private int z;               // Minecraft Z coordinate (center)
    private String agriculture;  // e.g. "Feld", "Wild", "Erz", "Fisch"
    private String mines;        // e.g. "Kohle", "Eisen", "Salz"
    private String bonus;        // e.g. "Honig", "Torf"
    private int fertility;       // 5, 10, or 30
    private List<String> connected_regions; // e.g. ["lehen_2", "lehen_3"]
    private String player_gathering;
    private boolean enabled;     // Whether the region is settleable

    // Transient zugewiesene Fraktion wird nach dem Data-Merge verknüpft
    private transient FactionData faction;
    // Transient Polygon Border Vertices als [x, z] Paare in Minecraft-Koordinaten
    private transient int[][] polygon;

    public String getId() { return id; }
    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getAgriculture() { return agriculture; }
    public String getMines() { return mines; }
    public String getBonus() { return bonus; }
    public int getFertility() { return fertility; }
    public List<String> getConnectedRegions() { return connected_regions; }
    public String getPlayerGathering() { return player_gathering; }
    public int getPlayerGatheringCount() {
        try { return player_gathering != null ? Integer.parseInt(player_gathering.trim()) : 0; }
        catch (NumberFormatException e) { return 0; }
    }
    public boolean isEnabled() { return enabled; }

    public FactionData getFaction() { return faction; }
    public void setFaction(FactionData faction) { this.faction = faction; }

    public boolean hasFaction() { return faction != null; }

    public int[][] getPolygon() { return polygon; }
    public void setPolygon(int[][] polygon) { this.polygon = polygon; }
    public boolean hasPolygon() { return polygon != null && polygon.length >= 3; }
}
