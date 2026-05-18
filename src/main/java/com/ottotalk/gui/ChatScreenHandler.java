package com.ottotalk.gui;

import com.ottotalk.OttoTalkClient;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.ottotalk.gui.ChatCheckboxRenderer;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Kümmert sich um den OTTO button und die roleplay UI per Fabric Screen Events API
 * (refmap-frei, läuft auch in production environments).
 */
public class ChatScreenHandler {

    private static final int OTTO_BUTTON_W = 17;
    private static final int OTTO_BUTTON_H = 10;
    private static final Identifier TEX_INPUT_SLICE = new Identifier("ottotalk", "textures/gui/input_3_slice.png");
    private static final Identifier TEX_AI_ACTIVE = new Identifier("ottotalk", "textures/gui/ai_active.png");
    private static final Identifier TEX_AI_ACTIVE_2 = new Identifier("ottotalk", "textures/gui/ai_active_2.png");
    private static final Identifier TEX_INDICATOR_SPRECHEN = new Identifier("ottotalk", "textures/gui/indicator_sprechen.png");
    private static final Identifier TEX_POST = new Identifier("ottotalk", "textures/gui/post.png");
    private static final int LETTER_BUTTON_W = 17;
    private static final int LETTER_BUTTON_H = 10;
    private static final int LETTER_BUTTON_PAD = 3;
    private static int letterButtonX = 0;
    private static int letterButtonY = 0;
    private static final java.util.Map<String, Identifier> indicatorCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int AI_ACTIVE_W = 39;
    private static final int AI_ACTIVE_H = 10;
    private static final int AI_INDICATOR_PAD = 3; // padding nach dem indicator
    private static final int INPUT_TEX_W = 58;
    private static final int INPUT_TEX_H = 16;
    private static final int INPUT_CAP_LEFT = 5;
    private static final int INPUT_CAP_RIGHT = 6;
    private static final long ANIM_DURATION_MS = 200;
    private static int ottoButtonX = 0;
    private static int ottoButtonY = 0;
    private static int inputFieldX = 0;
    private static int inputFieldY = 0;
    private static int inputFieldW = 0;
    private static int inputFieldH = 0;
    private static int inputMinW = 0;
    private static int inputMaxW = 0;
    private static int storedButtonSpacing = 0;
    private static long chatOpenedTime = 0;
    private static TextFieldWidget currentChatField = null;
    private static ServerChatOverlay serverChatOverlay = new ServerChatOverlay();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen) {
                onChatScreenInit(client, (ChatScreen) screen, scaledWidth, scaledHeight);
            }
        });

    }

    /**
     * Wenn setupFromMixin() schon gelaufen ist (inputFieldW > 0), die geometry überspringen und nur events registrieren.
     * Sonst hier die volle geometry als fallback aufsetzen.
     */
    private static void onChatScreenInit(MinecraftClient client, ChatScreen chatScreen, int scaledWidth, int scaledHeight) {
        try {
            TextFieldWidget chatField = findChatField(chatScreen);
            if (chatField == null) chatField = currentChatField; // mixin fallback nehmen
            if (chatField == null) return;
            currentChatField = chatField;

            if (inputFieldW == 0) {
                // setupFromMixin lief nicht, also volle geometry hier machen
                int originalX = chatField.getX();
                int originalY = chatField.getY();
                int originalWidth = chatField.getWidth();
                int originalHeight = chatField.getHeight();

                int aiIndicatorExtra = RoleplayStateManager.isRoleplayModeActive() ? (AI_ACTIVE_W + AI_INDICATOR_PAD) : 0;
                int buttonSpacing = OTTO_BUTTON_W + 6 + aiIndicatorExtra;
                storedButtonSpacing = buttonSpacing;

                int chatHudWidth = MinecraftClient.getInstance().inGameHud.getChatHud().getWidth();
                int newChatWidth = Math.min(originalWidth - buttonSpacing, chatHudWidth + 4 - buttonSpacing);

                chatField.setX(originalX + buttonSpacing);
                chatField.setWidth(newChatWidth);
                chatField.setMaxLength(10000);
                chatField.setDrawsBackground(false);
                chatOpenedTime = System.currentTimeMillis();

                inputFieldX = originalX;
                inputFieldY = originalY;
                inputFieldW = newChatWidth + buttonSpacing;
                inputFieldH = originalHeight;
                inputMinW = inputFieldW;
                inputMaxW = scaledWidth - originalX;

                ottoButtonX = originalX + 2;
                ottoButtonY = originalY + (originalHeight - OTTO_BUTTON_H) / 2 - 2;
            }

            registerScreenEvents(chatScreen, chatField);
        } catch (Exception e) {
            OttoTalkClient.LOGGER.error("Failed to add OTTO button: " + e.getMessage());
        }
    }

    private static void registerScreenEvents(ChatScreen chatScreen, TextFieldWidget chatField) {
        // nach dem render: OTTO button zeichnen, roleplay overlay
        ScreenEvents.afterRender(chatScreen).register((screen, context, mouseX, mouseY, tickDelta) -> {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            // chat field soll immer an der richtigen geshifteten position sein
            if (chatField != null && inputFieldX > 0) {
                chatField.setX(inputFieldX + storedButtonSpacing);
                chatField.setWidth(inputFieldW - storedButtonSpacing);
            }

            // MCs solid-schwarzen input background unterdrücken und unsere textur drunter malen,
            // dann den text wieder drüberrendern
            if (chatField != null) {
                chatField.setDrawsBackground(false);
                drawInputBackground(context);
                chatField.render(context, mouseX, mouseY, tickDelta);
            }

            // dynamische breite: input field wird breiter wenn der text den rand erreicht
            if (chatField != null) {
                String text = chatField.getText();
                // vanilla 256-zeichen-limit für commands (startet mit /), sonst unlimited
                boolean isCommand = text.startsWith("/");
                chatField.setMaxLength(isCommand ? 256 : 10000);
                int textPixelW = textRenderer.getWidth(text) + 12; // 12px padding für den cursor
                int neededFieldW = textPixelW + storedButtonSpacing;
                int targetW = Math.max(inputMinW, Math.min(neededFieldW, inputMaxW));
                if (targetW != inputFieldW) {
                    inputFieldW = targetW;
                    int newChatWidth = targetW - storedButtonSpacing;
                    chatField.setWidth(newChatWidth);
                }
            }

            boolean onOttonien = ServerChatState.isOnOttonien();
            Identifier indicatorTex;
            if (onOttonien) {
                String path = ServerChatState.getIndicatorTexturePath();
                indicatorTex = indicatorCache.computeIfAbsent(path, p -> new Identifier("ottotalk", p));
            } else {
                indicatorTex = TEX_INDICATOR_SPRECHEN;
            }
            boolean ottoHovered = mouseX >= ottoButtonX && mouseX <= ottoButtonX + OTTO_BUTTON_W
                    && mouseY >= ottoButtonY && mouseY <= ottoButtonY + OTTO_BUTTON_H;
            context.drawTexture(indicatorTex, ottoButtonX, ottoButtonY, OTTO_BUTTON_W, OTTO_BUTTON_H,
                    0, 0, 17, 10, 17, 10);
            if (ottoHovered) {
                context.fill(ottoButtonX + 1, ottoButtonY + 1,
                        ottoButtonX + OTTO_BUTTON_W - 1, ottoButtonY + OTTO_BUTTON_H - 1, 0x33FFFFFF);
            }

            if (RoleplayStateManager.isRoleplayModeActive()) {
                int aiIndX = ottoButtonX + OTTO_BUTTON_W + 4;
                int aiIndY = ottoButtonY + (OTTO_BUTTON_H - AI_ACTIVE_H) / 2;
                boolean isRede = RoleplayStateManager.getSpeechMode() == RoleplayStateManager.SpeechMode.REDE;
                Identifier aiTex = isRede ? TEX_AI_ACTIVE : TEX_AI_ACTIVE_2;
                context.drawTexture(aiTex, aiIndX, aiIndY, AI_ACTIVE_W, AI_ACTIVE_H,
                        0, 0, AI_ACTIVE_W, AI_ACTIVE_H, AI_ACTIVE_W, AI_ACTIVE_H);

                // chat field position dynamisch anpassen wenn AI modus oder letter item sich ändert
                int letterExtra1 = isHoldingLetterItem() ? (LETTER_BUTTON_W + LETTER_BUTTON_PAD) : 0;
                int expectedSpacing = OTTO_BUTTON_W + 6 + AI_ACTIVE_W + AI_INDICATOR_PAD + letterExtra1;
                if (storedButtonSpacing != expectedSpacing) {
                    storedButtonSpacing = expectedSpacing;
                    int newX = inputFieldX + storedButtonSpacing;
                    chatField.setX(newX);
                    chatField.setWidth(inputFieldW - storedButtonSpacing);
                }
            } else {
                int letterExtra2 = isHoldingLetterItem() ? (LETTER_BUTTON_W + LETTER_BUTTON_PAD) : 0;
                int expectedSpacing = OTTO_BUTTON_W + 6 + letterExtra2;
                if (storedButtonSpacing != expectedSpacing) {
                    storedButtonSpacing = expectedSpacing;
                    int newX = inputFieldX + storedButtonSpacing;
                    chatField.setX(newX);
                    chatField.setWidth(inputFieldW - storedButtonSpacing);
                }
            }

            if (isHoldingLetterItem()) {
                int aiOff = RoleplayStateManager.isRoleplayModeActive() ? (AI_ACTIVE_W + AI_INDICATOR_PAD + 4) : 0;
                letterButtonX = ottoButtonX + OTTO_BUTTON_W + 3 + aiOff;
                letterButtonY = ottoButtonY;
                boolean letterHovered = mouseX >= letterButtonX && mouseX <= letterButtonX + LETTER_BUTTON_W
                        && mouseY >= letterButtonY && mouseY <= letterButtonY + LETTER_BUTTON_H;
                context.drawTexture(TEX_POST, letterButtonX, letterButtonY, LETTER_BUTTON_W, LETTER_BUTTON_H,
                        0, 0, 17, 10, 17, 10);
                if (letterHovered) {
                    context.fill(letterButtonX, letterButtonY,
                            letterButtonX + LETTER_BUTTON_W, letterButtonY + LETTER_BUTTON_H, 0x33FFFFFF);
                    context.drawTooltip(textRenderer, Text.literal("Brief schreiben"), mouseX, mouseY);
                }
            }

            // check ob pending edit text vorhanden (vom edit button auf den options)
            String pendingEdit = RoleplayStateManager.consumePendingEditText();
            if (pendingEdit != null && chatField != null) {
                chatField.setText(pendingEdit);
                chatField.setFocused(true);
                chatField.setCursorToEnd(false);
            }

            // overlay sitzt zwischen chat history und input field. nur sichtbar während LLM responses
            // generiert oder angezeigt werden (LOADING/OPTIONS), nicht im IDLE.
            int serverOverlayBottomY = chatField.getY(); // default: über dem chat input
            if (RoleplayStateManager.shouldRenderOverlay()) {
                RoleplayOverlayScreen overlay = RoleplayStateManager.getCurrentOverlay();
                if (overlay != null && overlay.getState() != RoleplayOverlayScreen.State.IDLE) {
                    // ChatHud breite nehmen (aus den chat settings) damits zu den chat messages passt
                    int chatHudWidth = MinecraftClient.getInstance().inGameHud.getChatHud().getWidth();
                    overlay.init(chatHudWidth, chatField.getY());
                    overlay.render(context, mouseX, mouseY, tickDelta);
                    serverOverlayBottomY = overlay.getOverlayY(); // server overlay sitzt über dem otto overlay
                }
                // chat field jedes frame focused halten solang das overlay offen ist
                if (!chatField.isFocused()) {
                    chatField.setFocused(true);
                }
            }

            // server chat overlay ist nur an wenn der spieler wirklich auf Ottonien.com connected ist
            if (ServerChatState.shouldRender()) {
                int chatHudWidth = MinecraftClient.getInstance().inGameHud.getChatHud().getWidth();
                serverChatOverlay.init(chatHudWidth, serverOverlayBottomY);
                serverChatOverlay.render(context, mouseX, mouseY, tickDelta);
                ServerChatState.setLastOverlayHeight(serverChatOverlay.getOverlayHeight());
            } else {
                ServerChatState.setLastOverlayHeight(0);
                serverChatOverlay.resetAnimation();
            }
        });

        // vor key press: ESC abfangen zum roleplay-mode schließen, Enter für lange messages abfangen
        ScreenKeyboardEvents.beforeKeyPress(chatScreen).register((screen, key, scancode, modifiers) -> {
            if (key == 256 && RoleplayStateManager.isRoleplayModeActive()) {
                RoleplayStateManager.disableRoleplayMode();
            }
            // Enter abfangen für lange chat messages (> 250 zeichen), aber NICHT für commands
            if ((key == 257 || key == 335) && chatField != null) {
                String text = chatField.getText().trim();
                if (!text.startsWith("/") && text.length() > 250) {
                    // die komplette message einmal in die history packen; der split selbst produziert mehrere packets
                    // die wir nicht einzeln in der message history haben wollen
                    MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(text);
                    sendSplitChatMessage(text);
                    chatField.setText("");
                    // close auf den nächsten tick verschieben, sonst verarbeitet MCs keyPressed handler auch noch Enter
                    MinecraftClient.getInstance().send(() -> {
                        MinecraftClient.getInstance().setScreen(null);
                    });
                }
            }
        });

        // nach key press: chat field bleibt focused solang overlay offen ist
        // außerdem command-eingabe (/) blocken solang AI Helper aktiv ist
        ScreenKeyboardEvents.afterKeyPress(chatScreen).register((screen, key, scancode, modifiers) -> {
            if (RoleplayStateManager.isRoleplayModeActive()) {
                if (!chatField.isFocused()) {
                    chatField.setFocused(true);
                }
                // führendes / abschneiden um commands zu blocken solang der AI Helper läuft
                if (chatField.getText().startsWith("/")) {
                    chatField.setText(chatField.getText().substring(1));
                }
            }
        });

        // mouse-klicks behandeln: checkboxen, OTTO button, overlay schließen, chat re-focus
        ScreenMouseEvents.afterMouseClick(chatScreen).register((screen, mouseX, mouseY, button) -> {
            // zuerst checkbox-klicks prüfen
            if (RoleplayStateManager.isRoleplayModeActive()) {
                if (ChatCheckboxRenderer.handleClick(mouseX, mouseY)) {
                    chatField.setFocused(true);
                    return;
                }
            }
            // letter button klick checken
            if (isHoldingLetterItem()
                    && mouseX >= letterButtonX && mouseX <= letterButtonX + LETTER_BUTTON_W
                    && mouseY >= letterButtonY && mouseY <= letterButtonY + LETTER_BUTTON_H) {
                MinecraftClient mc2 = MinecraftClient.getInstance();
                if (mc2.player != null && mc2.player.getMainHandStack().getItem() == Items.WRITTEN_BOOK) {
                    List<List<String>> bookPages = readWrittenBookPages();
                    if (bookPages != null) {
                        mc2.setScreen(new LetterScreen(bookPages, readWrittenBookAuthor()));
                    } else {
                        mc2.setScreen(new LetterScreen());
                    }
                } else {
                    mc2.setScreen(new LetterScreen());
                }
                return;
            }
            // klick auf den server-toggle button prüfen
            if (mouseX >= ottoButtonX && mouseX <= ottoButtonX + OTTO_BUTTON_W
                    && mouseY >= ottoButtonY && mouseY <= ottoButtonY + OTTO_BUTTON_H) {
                if (ServerChatState.isOnOttonien()) {
                    ServerChatState.toggleServerOverlay();
                } else {
                    // fallback: roleplay toggeln wenn man nicht auf Ottonien ist
                    RoleplayStateManager.toggleRoleplayMode();
                }
            }
            // server chat overlay klicks checken
            if (ServerChatState.shouldRender()) {
                if (serverChatOverlay.mouseClicked(mouseX, mouseY, button)) {
                    chatField.setFocused(true);
                    return;
                }
            }
            // overlay close button klick prüfen
            if (RoleplayStateManager.shouldRenderOverlay()) {
                RoleplayOverlayScreen overlay = RoleplayStateManager.getCurrentOverlay();
                if (overlay != null) {
                    overlay.mouseClicked(mouseX, mouseY, button);
                }
            }
            // chat field immer focused halten
            chatField.setFocused(true);
        });

        // Screen remove: roleplay nur deaktivieren wenn wir nicht auf ne API response warten
        ScreenEvents.remove(chatScreen).register(screen -> {
            currentChatField = null;
            inputFieldW = 0;
            RoleplayOverlayScreen overlay = RoleplayStateManager.getCurrentOverlay();
            if (overlay != null && (overlay.getState() == RoleplayOverlayScreen.State.LOADING
                    || overlay.getState() == RoleplayOverlayScreen.State.OPTIONS)) {
                // chat zu während wir auf results warten/sie zeigen - chat im nächsten tick wieder auf
                MinecraftClient.getInstance().send(() -> {
                    MinecraftClient.getInstance().setScreen(new ChatScreen(""));
                });
            } else if (RoleplayStateManager.isRoleplayModeActive()) {
                RoleplayStateManager.disableRoleplayMode();
            }
        });
    }

    /**
     * Called from ChatScreenMixin.onInitStart(), resets geometry so onChatScreenInit
     * can detect whether setupFromMixin ran (inputFieldW > 0) or not (inputFieldW == 0).
     */
    public static void resetGeometry() {
        inputFieldW = 0;
    }

    /**
     * Wird aus ChatScreenMixin.onInitDone() mit ner direkten chatField referenz aufgerufen.
     * setzt die input-field geometry auf und positioniert das field (umgeht findChatField()).
     * registriert KEINE screen events (das macht ScreenEvents.AFTER_INIT).
     */
    public static void setupFromMixin(TextFieldWidget chatField, int scaledWidth, int scaledHeight) {
        currentChatField = chatField;

        int originalX = chatField.getX();
        int originalY = chatField.getY();
        int originalWidth = chatField.getWidth();
        int originalHeight = chatField.getHeight();

        int aiIndicatorExtra = RoleplayStateManager.isRoleplayModeActive() ? (AI_ACTIVE_W + AI_INDICATOR_PAD) : 0;
        int letterExtra = isHoldingLetterItem() ? (LETTER_BUTTON_W + LETTER_BUTTON_PAD) : 0;
        int buttonSpacing = OTTO_BUTTON_W + 6 + aiIndicatorExtra + letterExtra;
        storedButtonSpacing = buttonSpacing;

        int chatHudWidth = MinecraftClient.getInstance().inGameHud.getChatHud().getWidth();
        int newChatWidth = Math.min(originalWidth - buttonSpacing, chatHudWidth + 4 - buttonSpacing);

        chatField.setX(originalX + buttonSpacing);
        chatField.setWidth(newChatWidth);
        chatField.setMaxLength(10000);
        chatField.setDrawsBackground(false);
        chatOpenedTime = System.currentTimeMillis();

        inputFieldX = originalX;
        inputFieldY = originalY;
        inputFieldW = newChatWidth + buttonSpacing;
        inputFieldH = originalHeight;
        inputMinW = inputFieldW;
        inputMaxW = scaledWidth - originalX;

        ottoButtonX = originalX + 2;
        ottoButtonY = originalY + (originalHeight - OTTO_BUTTON_H) / 2 - 2;
    }

    public static int getInputFieldX() { return inputFieldX; }

    public static int getChatFieldX() { return inputFieldX + storedButtonSpacing; }

    /**
     * Wird aus ChatScreenMixin direkt vor chatField.render() aufgerufen, um MCs default-schwarzen fill zu verdecken.
     */
    public static void drawInputBackground(net.minecraft.client.gui.DrawContext context) {
        if (inputFieldW > 0) {
            int animW;
            if (!com.ottotalk.OttoTalkClient.getConfig().enableAnimations) {
                animW = inputFieldW;
            } else {
                // breite von links animieren
                long elapsed = System.currentTimeMillis() - chatOpenedTime;
                float progress = Math.min(1.0f, (float) elapsed / ANIM_DURATION_MS);
                // ease-out: 1 - (1-t)^2
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                animW = (int) (inputFieldW * eased);
                if (animW < INPUT_CAP_LEFT + INPUT_CAP_RIGHT) animW = INPUT_CAP_LEFT + INPUT_CAP_RIGHT;
            }
            renderInputSlice(context, inputFieldX, inputFieldY - 5, animW, INPUT_TEX_H);
        }
    }

    private static void renderInputSlice(net.minecraft.client.gui.DrawContext context, int x, int y, int width, int height) {
        int capL = INPUT_CAP_LEFT;
        int capR = INPUT_CAP_RIGHT;
        int texW = INPUT_TEX_W;
        int texH = INPUT_TEX_H;
        int tileW = texW - capL - capR;
        int middleW = width - capL - capR;

        // linke cap (native size, kein stretch)
        context.drawTexture(TEX_INPUT_SLICE, x, y, capL, texH,
                0, 0, capL, texH, texW, texH);
        // mitte (horizontal getilet auf native höhe)
        if (middleW > 0 && tileW > 0) {
            int drawnX = 0;
            while (drawnX < middleW) {
                int drawW = Math.min(tileW, middleW - drawnX);
                context.drawTexture(TEX_INPUT_SLICE, x + capL + drawnX, y, drawW, texH,
                        capL, 0, drawW, texH, texW, texH);
                drawnX += tileW;
            }
        }
        // rechte cap (native size, kein stretch)
        context.drawTexture(TEX_INPUT_SLICE, x + width - capR, y, capR, texH,
                texW - capR, 0, capR, texH, texW, texH);
    }

    /**
     * Schickt ne message und splittet in chunks von 250 wenn nötig.
     * jeder chunk ausser dem letzten endet mit '>'.
     */
    public static void sendSplitChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (message.startsWith("/")) {
            // commands: nicht splitten, einfach schicken (wird von MC auf 256 gekappt)
            client.player.networkHandler.sendCommand(message.substring(1));
            return;
        }

        if (message.length() <= 250) {
            client.player.networkHandler.sendChatMessage(message);
            return;
        }

        // an word-grenzen splitten, max 249 zeichen pro chunk (1 zeichen für den > marker reserviert)
        java.util.List<String> parts = new java.util.ArrayList<>();
        String remaining = message;
        while (remaining.length() > 249) {
            // letztes leerzeichen bei oder vor index 249 finden
            int split = remaining.lastIndexOf(' ', 249);
            if (split <= 0) split = 249; // kein space gefunden: hard split
            parts.add(remaining.substring(0, split).stripTrailing() + ">");
            remaining = remaining.substring(split).stripLeading();
        }
        if (!remaining.isEmpty()) parts.add(remaining);

        // mit 1200ms zwischen den teilen schicken, damit der server jedes verarbeiten kann
        for (int i = 0; i < parts.size(); i++) {
            final String part = parts.get(i);
            final long delay = i * 1200L;
            if (delay == 0) {
                client.player.networkHandler.sendChatMessage(part);
            } else {
                scheduler.schedule(() -> {
                    client.send(() -> {
                        if (client.player != null) {
                            client.player.networkHandler.sendChatMessage(part);
                        }
                    });
                }, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Planung: chat message nach delay schicken, mit bypass interception aktiv.
     * wird für multi-part AI Helper messages gebraucht damit die reihenfolge stimmt.
     */
    public static void scheduleBypassMessage(String message, long delayMs) {
        MinecraftClient client = MinecraftClient.getInstance();
        scheduler.schedule(() -> {
            client.send(() -> {
                if (client.player != null) {
                    RoleplayStateManager.setBypassInterception(true);
                    client.player.networkHandler.sendChatMessage(message);
                    RoleplayStateManager.setBypassInterception(false);
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public static boolean isHoldingLetterItem() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        net.minecraft.item.ItemStack stack = mc.player.getMainHandStack();
        if (stack.getItem() == Items.PHANTOM_MEMBRANE) return true;
        if (stack.getItem() == Items.WRITTEN_BOOK) {
            String author = readWrittenBookAuthor();
            if (author == null || author.isEmpty()) return true;
            return author.equals(mc.player.getGameProfile().getName());
        }
        return false;
    }

    /** gibt das author-feld vom written book in der main hand zurück, oder null. */
    private static String readWrittenBookAuthor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        net.minecraft.item.ItemStack stack = mc.player.getMainHandStack();
        if (stack.getItem() != Items.WRITTEN_BOOK) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return null;
        return nbt.contains("author") ? nbt.getString("author") : null;
    }

    /**
     * Liest die seiten aus dem written-book NBT und gibt sie als liste von zeilen-listen zurück.
     * gibt null zurück wenn das item kein written book ist oder keine seiten hat.
     */
    private static List<List<String>> readWrittenBookPages() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        net.minecraft.item.ItemStack stack = mc.player.getMainHandStack();
        if (stack.getItem() != Items.WRITTEN_BOOK) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("pages")) return null;
        NbtList pagesList = nbt.getList("pages", NbtElement.STRING_TYPE);
        if (pagesList.isEmpty()) return null;
        List<List<String>> result = new java.util.ArrayList<>();
        for (int i = 0; i < pagesList.size(); i++) {
            String raw = pagesList.getString(i);
            String pageText;
            try {
                com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(raw);
                pageText = extractBookPageText(json);
            } catch (Exception e) {
                pageText = raw;
            }
            String[] rawLines = pageText.split("\n", -1);
            List<String> page = new java.util.ArrayList<>();
            for (String rawLine : rawLines) {
                if (page.size() >= LetterScreen.MAX_LINES) break;
                // jede absatz-zeile in MAX_LINE_CHARS-breite slots word-wrappen
                for (String wrapped : wordWrapLine(rawLine, LetterScreen.LINE_PIXEL_W)) {
                    if (page.size() >= LetterScreen.MAX_LINES) break;
                    page.add(wrapped);
                }
            }
            while (page.size() < LetterScreen.MAX_LINES) page.add("");
            result.add(page);
        }
        return result.isEmpty() ? null : result;
    }

    /** rekursiv plain text aus nem JSON text component rausziehen. */
    private static String extractBookPageText(com.google.gson.JsonElement json) {
        if (json == null) return "";
        if (json.isJsonPrimitive()) return json.getAsString();
        if (json.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (com.google.gson.JsonElement el : json.getAsJsonArray())
                sb.append(extractBookPageText(el));
            return sb.toString();
        }
        if (json.isJsonObject()) {
            com.google.gson.JsonObject obj = json.getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            if (obj.has("text")) sb.append(obj.get("text").getAsString());
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                for (com.google.gson.JsonElement extra : obj.getAsJsonArray("extra"))
                    sb.append(extractBookPageText(extra));
            }
            return sb.toString();
        }
        return "";
    }

    private static TextFieldWidget findChatField(ChatScreen chatScreen) {
        List<? extends net.minecraft.client.gui.Element> children = chatScreen.children();
        for (net.minecraft.client.gui.Element child : children) {
            if (child instanceof TextFieldWidget) {
                return (TextFieldWidget) child;
            }
        }
        return null;
    }

    /**
     * Word-wrapt ne einzelne absatz-zeile so dass sie in maxPixelWidth passt (per MC text renderer).
     * bricht wenn möglich an word-grenzen; hart, wenn ein einzelnes wort zu breit ist.
     */
    private static java.util.List<String> wordWrapLine(String text, int maxPixelWidth) {
        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text.isEmpty()) { lines.add(""); return lines; }
        while (tr != null && tr.getWidth(text) > maxPixelWidth) {
            // letztes leerzeichen finden wo der prefix noch reinpasst
            int cut = -1;
            for (int i = text.length() - 1; i > 0; i--) {
                if (text.charAt(i) == ' ' && tr.getWidth(text.substring(0, i)) <= maxPixelWidth) {
                    cut = i; break;
                }
            }
            if (cut < 0) {
                // hard-wrap: längster prefix der reinpasst
                for (int i = text.length() - 1; i > 0; i--) {
                    if (tr.getWidth(text.substring(0, i)) <= maxPixelWidth) { cut = i; break; }
                }
            }
            if (cut <= 0) { lines.add(text); return lines; } // safety
            lines.add(text.substring(0, cut).stripTrailing());
            text = text.substring(cut).stripLeading();
        }
        if (!text.isEmpty()) lines.add(text);
        return lines;
    }
}
