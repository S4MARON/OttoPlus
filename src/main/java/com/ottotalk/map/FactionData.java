package com.ottotalk.map;

import java.util.List;

/**
 * Ein Gefolge/Fraktion aus der Ottonien Map API (Daten vom /api/public/factions endpoint).
 */
public class FactionData {
    private String uuid;
    private String name;              // e.g. "Reichenau", "Arctander"
    private String region;            // e.g. "lehen_28"
    private String region_name;       // e.g. "Eugensbrück"
    private String rank_id;           // e.g. "Grafschaft", "Dorf", "Abbey"
    private String rank_name;         // e.g. "Grafschaft", "Dorf", "Abtei"
    private int rank_priority;        // 0=Unlanded, 10=Abbey, 50=Dorf, 60=Rittergut, 70=Vogtei, 80=Freiherrschaft, 90=Grafschaft
    private String leader_name;       // Minecraft username of leader
    private String leader_uuid;
    private int member_count;
    private String banner;            // e.g. "Reichenau_Blau_Golden"
    private String banner_name;       // e.g. "Reichenau Blau Golden"
    private String description;
    private boolean has_lord;
    private String lord_name;         // Direct lord name
    private String lord_name_recursive; // Top-level lord name
    private String lord_region;
    private String lord_region_recursive;
    private List<String> vassal_names;
    private List<String> vassal_uuids;
    private List<String> vassal_uuids_recursive;
    private String order_name;        // e.g. "Benediktinerorden", "Zisterzienserorden"
    private int order_id;
    private String creation_date_string;

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getRegion() { return region; }
    public String getRegionName() { return region_name; }
    public String getRankId() { return rank_id; }
    public String getRankName() { return rank_name; }
    public int getRankPriority() { return rank_priority; }
    public String getLeaderName() { return leader_name; }
    public String getLeaderUuid() { return leader_uuid; }
    public int getMemberCount() { return member_count; }
    public String getBanner() { return banner; }
    public String getBannerName() { return banner_name; }
    public String getDescription() { return description; }
    public boolean hasLord() { return has_lord; }
    public String getLordName() { return lord_name; }
    public String getLordNameRecursive() { return lord_name_recursive; }
    public String getLordRegion() { return lord_region; }
    public String getLordRegionRecursive() { return lord_region_recursive; }
    public List<String> getVassalNames() { return vassal_names; }
    public List<String> getVassalUuids() { return vassal_uuids; }
    public List<String> getVassalUuidsRecursive() { return vassal_uuids_recursive; }
    public String getOrderName() { return order_name; }
    public int getOrderId() { return order_id; }
    public String getCreationDateString() { return creation_date_string; }

    /**
     * baut ne Local-Override Fraktion für wenn die API noch nichts von dem Lehen weiß
     */
    public static FactionData localOverride(String name, String region, String rankName, int rankPriority, String banner) {
        FactionData f = new FactionData();
        f.name = name;
        f.region = region;
        f.rank_name = rankName;
        f.rank_priority = rankPriority;
        f.banner = banner;
        return f;
    }

    /**
     * Returns a color for the faction based on its rank priority.
     * Higher rank = more prominent color.
     */
    public int getRankColor() {
        switch (rank_priority) {
            case 95: return 0xFFAA44CC; // Herzogtum - Purple
            case 90: return 0xFFFFD700; // Grafschaft - Gold
            case 80: return 0xFFC0C0C0; // Freiherrschaft - Silver
            case 70: return 0xFF4A90D9; // Vogtei - Blue
            case 60: return 0xFF8B4513; // Rittergut - Brown
            case 50: return 0xFF228B22; // Dorf - Green
            case 10: return 0xFFDAA520; // Abtei - Dark Gold
            default: return 0xFF808080; // Unlanded/Unknown - Gray
        }
    }

    /**
     * gibt ne halbtransparente Fill-Color für Map Overlays zurück
     */
    public int getRankFillColor() {
        // gleicher Farbton nur mit 40% Alpha
        int color = getRankColor();
        return (color & 0x00FFFFFF) | 0x66000000;
    }
}
