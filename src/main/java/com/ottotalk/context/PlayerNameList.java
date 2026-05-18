package com.ottotalk.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ottotalk.OttoTalkClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liste aller Spieler mit ihren RP-Charakternamen.
 * Wird beim Join befüllt, Updates kommen aus den Chat-Tooltips, gespeichert in ottotalk_players.json.
 */
public class PlayerNameList {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PLAYERS_PATH = FabricLoader.getInstance().getConfigDir().resolve("ottotalk_players.json");
    private static final String UNKNOWN = "Unbekannt";

    // accountName lowercase zu PlayerEntry Lookup Map
    private static final Map<String, PlayerEntry> players = new ConcurrentHashMap<>();

    public static class PlayerEntry {
        public String accountName;
        public String characterName; // "Unbekannt" wenn noch unbekannt
        public String characterTitle;
        public int characterTitleColor = -1; // -1 ist unset
        public boolean locked; // wenn true wird der Charaktername nicht aus dem Chat auto-updated

        public PlayerEntry(String accountName, String characterName) {
            this.accountName = accountName;
            this.characterName = characterName;
            this.characterTitle = "";
            this.characterTitleColor = -1;
            this.locked = false;
        }
    }

    /** Spielerliste von der Platte laden */
    public static void load() {
        if (Files.exists(PLAYERS_PATH)) {
            try {
                String json = Files.readString(PLAYERS_PATH);
                Type type = new TypeToken<List<PlayerEntry>>(){}.getType();
                List<PlayerEntry> list = GSON.fromJson(json, type);
                if (list != null) {
                    players.clear();
                    for (PlayerEntry entry : list) {
                        if (entry.accountName != null) {
                            players.put(entry.accountName.toLowerCase(), entry);
                        }
                    }
                }
            } catch (Exception e) {
                OttoTalkClient.LOGGER.error("[PlayerNames] Failed to load player list", e);
            }
        }
    }

    /** Spielerliste auf die Platte speichern */
    public static void save() {
        try {
            Files.createDirectories(PLAYERS_PATH.getParent());
            List<PlayerEntry> list = new ArrayList<>(players.values());
            list.sort(Comparator.comparing(e -> e.accountName.toLowerCase()));
            String json = GSON.toJson(list);
            Files.writeString(PLAYERS_PATH, json);
        } catch (IOException e) {
            OttoTalkClient.LOGGER.error("[PlayerNames] Failed to save player list", e);
        }
    }

    /**
     * Geht alle online spieler durch und packt neue mit "Unbekannt" als character name rein.
     * wird beim server-join aufgerufen.
     */
    public static void syncOnlinePlayers() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) return;

        Collection<PlayerListEntry> onlinePlayers = client.getNetworkHandler().getPlayerList();
        int added = 0;
        for (PlayerListEntry entry : onlinePlayers) {
            String name = entry.getProfile().getName();
            if (name == null || name.isEmpty()) continue;
            String key = name.toLowerCase();
            if (!players.containsKey(key)) {
                players.put(key, new PlayerEntry(name, UNKNOWN));
                added++;
            }
        }
        if (added > 0) {
            save();
        }
    }

    /**
     * Charaktername für einen spieler updaten (kommt vom chat tooltip).
     * updatet nur wenn der name vorher "Unbekannt" war oder sich unterscheidet.
     */
    public static void updateCharacterName(String accountName, String characterName) {
        if (accountName == null || characterName == null || characterName.isEmpty()) return;
        String key = accountName.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) {
            entry = new PlayerEntry(accountName, characterName);
            players.put(key, entry);
            save();
        } else if (!entry.locked && !characterName.equals(entry.characterName)) {
            entry.characterName = characterName;
            save();
        }
    }

    /**
     * charakternamen manuell setzen (aus dem settings UI).
     */
    public static void setCharacterName(String accountName, String characterName) {
        if (accountName == null || accountName.isEmpty()) return;
        String key = accountName.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) {
            entry = new PlayerEntry(accountName, characterName != null ? characterName : UNKNOWN);
            players.put(key, entry);
        } else {
            entry.characterName = characterName != null ? characterName : UNKNOWN;
        }
        save();
    }

    /**
     * Ensure a player exists in the list. If not, add them with "Unbekannt".
     * Called when encountering a player (e.g. nametag render) who may have
     * joined after the initial syncOnlinePlayers() call.
     */
    public static void ensurePlayer(String accountName) {
        if (accountName == null || accountName.isEmpty()) return;
        String key = accountName.toLowerCase();
        if (!players.containsKey(key)) {
            players.put(key, new PlayerEntry(accountName, UNKNOWN));
            save();
        }
    }

    /** Charakternamen für nen spieler holen, oder "Unbekannt" wenn unbekannt. */
    public static String getCharacterName(String accountName) {
        if (accountName == null) return UNKNOWN;
        PlayerEntry entry = players.get(accountName.toLowerCase());
        return entry != null ? entry.characterName : UNKNOWN;
    }

    /** title color manuell setzen (kommt aus dem chat tooltip parsing). */
    public static void setCharacterTitleColor(String accountName, int color) {
        if (accountName == null || accountName.isEmpty()) return;
        String key = accountName.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) {
            entry = new PlayerEntry(accountName, UNKNOWN);
            players.put(key, entry);
        }
        entry.characterTitleColor = color;
        save();
    }

    /** die gespeicherte title color für nen spieler holen, oder -1 wenn unset. */
    public static int getCharacterTitleColor(String accountName) {
        if (accountName == null) return -1;
        PlayerEntry entry = players.get(accountName.toLowerCase());
        return (entry != null) ? entry.characterTitleColor : -1;
    }

    /** Manually set a character title (from settings UI or local player sync). */
    public static void setCharacterTitle(String accountName, String title) {
        if (accountName == null || accountName.isEmpty()) return;
        String key = accountName.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) {
            entry = new PlayerEntry(accountName, UNKNOWN);
            players.put(key, entry);
        }
        entry.characterTitle = title != null ? title : "";
        save();
    }

    /** Get the character title for a player, or "" if unknown/unset. */
    public static String getCharacterTitle(String accountName) {
        if (accountName == null) return "";
        PlayerEntry entry = players.get(accountName.toLowerCase());
        if (entry == null || entry.characterTitle == null) return "";
        return entry.characterTitle;
    }

    /** Check if a player's character name is known (not "Unbekannt"). */
    public static boolean isKnown(String accountName) {
        return !UNKNOWN.equals(getCharacterName(accountName));
    }

    /** alle player entries holen (sortiert nach account name). */
    public static List<PlayerEntry> getAllEntries() {
        List<PlayerEntry> list = new ArrayList<>(players.values());
        list.sort(Comparator.comparing(e -> e.accountName.toLowerCase()));
        return list;
    }

    /** Get the total number of tracked players. */
    public static int size() {
        return players.size();
    }

    /** Check if a player's name is locked (no auto-update). */
    public static boolean isLocked(String accountName) {
        if (accountName == null) return false;
        PlayerEntry entry = players.get(accountName.toLowerCase());
        return entry != null && entry.locked;
    }

    /** Toggle the locked state of a player. */
    public static void toggleLocked(String accountName) {
        if (accountName == null) return;
        PlayerEntry entry = players.get(accountName.toLowerCase());
        if (entry != null) {
            entry.locked = !entry.locked;
            save();
        }
    }

    /** alles leeren. */
    public static void clear() {
        players.clear();
    }
}
