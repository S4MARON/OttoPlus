package com.ottotalk.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Brief-Schreibmaske. Geht auf wenn der spieler ne phantom-membran in der hand hat.
 * 14 zeilen pro seite, 19 zeichen pro zeile. "Schreiben" schickt /letter commands
 * mit ner ladeanimation, danach geht der PostRecipientScreen auf.
 */
public class LetterScreen extends Screen {

    private static final Identifier TEX_LETTER       = new Identifier("ottotalk", "textures/gui/letter.png");
    private static final Identifier TEX_PAGE_BACK    = new Identifier("ottotalk", "textures/gui/page_backward.png");
    private static final Identifier TEX_PAGE_BACK_HL = new Identifier("ottotalk", "textures/gui/page_backward_highlighted.png");
    private static final Identifier TEX_PAGE_FWD     = new Identifier("ottotalk", "textures/gui/page_forward.png");
    private static final Identifier TEX_PAGE_FWD_HL  = new Identifier("ottotalk", "textures/gui/page_forward_highlighted.png");
    private static final Identifier TEX_SAVE         = new Identifier("ottotalk", "textures/gui/save.png");
    private static final Identifier TEX_EMPTY        = new Identifier("ottotalk", "textures/gui/empty.png");
    private static final Identifier TEX_EXIT         = new Identifier("ottotalk", "textures/gui/exit.png");

    private static final int TEX_LETTER_W  = 187;
    private static final int TEX_LETTER_H  = 206;
    private static final int PAGE_NAV_W    = 23;
    private static final int PAGE_NAV_H    = 13;

    private static final int FRAME_W       = 188;
    // Layout (top to bottom, no overlaps):
    //  title+sep:  0..25  (25px)
    //  text lines: 25..145 (12x10=120px)
    //  fmt bar:    2px gap + 10px bar = 12px, fmtBarY=frameY+147
    //  gap:        8px  -> navY at frameY+167
    //  nav row:    13px, ends at frameY+180
    //  gap:        5px  -> iconRowY at frameY+185
    //  icon row:   18px, ends at frameY+203
    //  gap:        5px  -> sendY at frameY+208
    //  send btn:   18px, ends at frameY+226
    //  pad:        18px
    private static final int FRAME_H       = 244;

    private static final char[] FMT_COLOR_CODES = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
    };
    private static final int[] FMT_COLOR_RGBS = {
        0x000000,0x0000AA,0x00AA00,0x00AAAA,
        0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
        0x555555,0x5555FF,0x55FF55,0x55FFFF,
        0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF
    };
    // style codes: fett, kursiv, unterstrichen, durchgestrichen, reset
    private static final char[]   FMT_STYLE_CODES  = { 'l','o','n','m','r' };
    private static final String[] FMT_STYLE_LABELS = { "B","I","U","S","R" };
    private static final int MAX_PAGES     = 5;
    public  static final int MAX_LINES     = 12;  // limit vom server-script
    private static final int LINE_H        = 10;
    private static final int MAX_LINE_CHARS  = 200; // max field length; visual wrap nutzt LINE_PIXEL_W
    public  static final int LINE_PIXEL_W    = 114; // vanilla book text-area breite (px)

    private static final int TITLE_COLOR     = 0xFF3A2010; // deep brown, high contrast on parchment
    private static final int READ_ONLY_COLOR = 0xFF7A6450; // medium warm brown, readable but clearly muted
    private static final int LINE_COLOR      = 0x445C3A1A;
    private static final int LOADING_DOT_MS  = 350;

    private static final int ICON_BTN_W  = 20;
    private static final int ICON_BTN_H  = 18;
    private static final int SEND_BTN_W  = 100;
    private static final int SEND_BTN_H  = 18;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ottotalk-letter");
                t.setDaemon(true);
                return t;
            });

    private final List<List<String>> pages      = new ArrayList<>();
    private final List<boolean[]>     pageNewlines = new ArrayList<>(); // true = explicit Enter vor dieser zeile
    private int   readOnlyPageCount;        // seiten aus dem original-buch (nicht editierbar)
    private int[] originalLastContentLine;  // stabile lock-grenze pro buchseite
    private int currentPage  = 0;
    private int focusedLine  = 0;

    private int frameX, frameY;
    private int textX, textY, textW;
    private final TextFieldWidget[] lineWidgets = new TextFieldWidget[MAX_LINES];

    private int fmtBarY;  // Y der formatting-toolbar
    private int fmtColorsX; // X wo die 16 color-swatches anfangen
    private int fmtStylesX; // X wo die style-buttons anfangen
    private int navY;
    private int prevBtnX, prevBtnY;
    private int nextBtnX, nextBtnY;
    private int saveBtnX,    saveBtnY;
    private int addPageBtnX, addPageBtnY;
    private int cancelBtnX,  cancelBtnY;
    private int sendBtnX,    sendBtnY;

    private boolean inWrap           = false; // verhindert re-entrant word-wrap calls
    // loading state
    private boolean isLoading        = false;
    private long    loadingStart     = 0;
    private long    loadingDuration  = 0;
    // newContentSaved: true sobald /letter für alle neuen seiten raus ist (gated Brief versenden)
    private boolean newContentSaved;
    // viewOnly: true wenn der brief jemand anderem gehört (nur lesen + blättern)
    private boolean viewOnly = false;

    public LetterScreen() {
        super(Text.literal("Brief schreiben"));
        this.readOnlyPageCount       = 0;
        this.originalLastContentLine = new int[0];
        this.newContentSaved         = false;
        pages.add(newBlankPage());
        pageNewlines.add(new boolean[MAX_LINES]);
    }

    /** pre-filled konstruktor (kein author check, für kompatibilität). */
    public LetterScreen(List<List<String>> prefilledPages) {
        this(prefilledPages, null);
    }

    /** Pre-filled constructor: pass the book's author NBT string to enforce ownership. */
    public LetterScreen(List<List<String>> prefilledPages, String author) {
        super(Text.literal("Brief"));
        MinecraftClient mc0 = MinecraftClient.getInstance();
        String playerName = (mc0 != null && mc0.player != null) ? mc0.player.getGameProfile().getName() : null;
        this.viewOnly = author != null && !author.isEmpty()
                && playerName != null && !author.equals(playerName);
        this.readOnlyPageCount       = prefilledPages.size();
        this.newContentSaved         = true;
        this.originalLastContentLine = new int[prefilledPages.size()];
        int lastPrefill = prefilledPages.size() - 1;
        for (int i = 0; i < prefilledPages.size(); i++) {
            // alle seiten außer der letzten sind komplett gelockt (server appendet immer, mitten reinschreiben geht nicht)
            originalLastContentLine[i] = (i < lastPrefill) ? MAX_LINES - 1
                    : getLastContentLine(prefilledPages.get(i));
        }
        for (List<String> p : prefilledPages) {
            List<String> copy = new ArrayList<>(p);
            while (copy.size() > MAX_LINES) copy.remove(copy.size() - 1); // bei 12 cappen
            while (copy.size() < MAX_LINES) copy.add("");
            pages.add(copy);
            pageNewlines.add(new boolean[MAX_LINES]);
        }
        if (pages.isEmpty()) { pages.add(newBlankPage()); pageNewlines.add(new boolean[MAX_LINES]); }
        if (!viewOnly) {
            // ne neue leere editierbare seite dranhängen, damit der nutzer direkt losschreiben kann
            pages.add(newBlankPage());
            pageNewlines.add(new boolean[MAX_LINES]);
        }
    }

    static List<String> newBlankPage() {
        List<String> p = new ArrayList<>(MAX_LINES);
        for (int i = 0; i < MAX_LINES; i++) p.add("");
        return p;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void init() {
        frameX = (width  - FRAME_W) / 2;
        frameY = (height - FRAME_H) / 2;

        textW = LINE_PIXEL_W;
        textX = frameX + (FRAME_W - textW) / 2;
        textY = frameY + 25;

        List<String> page = pages.get(currentPage);
        int lastLocked = getOriginalLastLocked(currentPage);
        for (int i = 0; i < MAX_LINES; i++) {
            final int lineIdx = i;
            ShadowFreeField field = new ShadowFreeField(
                    textRenderer, textX, textY + i * LINE_H, textW, LINE_H - 1);
            field.setDrawsBackground(false);
            field.setMaxLength(MAX_LINE_CHARS);
            field.setChangedListener(text -> saveLine(lineIdx, text));
            field.setText(ampToSection(lineIdx < page.size() ? page.get(lineIdx) : ""));
            boolean isLineReadOnly = (lineIdx <= lastLocked);
            field.setEditable(!isLineReadOnly);
            field.setEditableColor(isLineReadOnly ? READ_ONLY_COLOR : TITLE_COLOR);
            field.setUneditableColor(READ_ONLY_COLOR);
            lineWidgets[i] = field;
            addDrawableChild(field);
        }
        focusLine(lastLocked + 1 < MAX_LINES ? lastLocked + 1 : 0);

        // formatting toolbar: 2px abstand unter dem text-bereich, 10px hoch
        fmtBarY = frameY + 25 + MAX_LINES * LINE_H + 2; // frameY+147
        // 16 color swatches: 6x6 each, 1px gap = 7px stride, total 111px
        // 4px gap
        // 6 style buttons: 8x8 each, 2px gap = 10px stride, total 58px
        // Grand total: 111+4+58 = 173px, centred in 188: margin=7
        fmtColorsX = frameX + 7;
        fmtStylesX = fmtColorsX + 16 * 7 - 1 + 4; // 111+4=115 from fmtColorsX

        navY = frameY + 25 + MAX_LINES * LINE_H + 8 + 14; // frameY+167
        int iconRowY = navY + PAGE_NAV_H + 5;  // frameY+185

        prevBtnX = frameX + 10;
        prevBtnY = navY;
        nextBtnX = frameX + FRAME_W - 10 - PAGE_NAV_W;
        nextBtnY = navY;

        // icon button reihe: save | addPage | cancel  (gap=6, total=72, in 188 zentriert)
        int iconGap     = 6;
        int iconsStartX = frameX + (FRAME_W - ICON_BTN_W * 3 - iconGap * 2) / 2;
        saveBtnX    = iconsStartX;
        saveBtnY    = iconRowY;
        addPageBtnX = iconsStartX + ICON_BTN_W + iconGap;
        addPageBtnY = iconRowY;
        cancelBtnX  = iconsStartX + (ICON_BTN_W + iconGap) * 2;
        cancelBtnY  = iconRowY;
        if (viewOnly) cancelBtnX = frameX + (FRAME_W - ICON_BTN_W) / 2; // zentriert wenn keine anderen buttons da sind

        // 3-tile Senden button unter der icon reihe
        sendBtnX = frameX + (FRAME_W - SEND_BTN_W) / 2;
        sendBtnY = iconRowY + ICON_BTN_H + 5;  // frameY+208
    }

    private void saveLine(int lineIdx, String text) {
        List<String> p = pages.get(currentPage);
        while (p.size() <= lineIdx) p.add("");
        p.set(lineIdx, text);
        // als dirty markieren wenn die geänderte zeile editierbar ist (nicht original gelockt)
        if (lineIdx > getOriginalLastLocked(currentPage)) newContentSaved = false;
        if (inWrap) return;
        // word-wrap: wenn der getippte text die pixel-breite sprengt, in die nächste zeile rippeln
        if (lineWidgets[lineIdx] != null && lineWidgets[lineIdx].isFocused()
                && textRenderer != null && textRenderer.getWidth(text) > LINE_PIXEL_W) {
            wrapOverflow(lineIdx);
        }
    }

    /** Ripples overflow words from lineIdx downward until every line fits within LINE_PIXEL_W. */
    private void wrapOverflow(int startLine) {
        inWrap = true;
        try {
            List<String> p = pages.get(currentPage);
            int locked = getOriginalLastLocked(currentPage);
            for (int i = startLine; i < MAX_LINES - 1; i++) {
                if (i <= locked) break;
                String text = i < p.size() ? p.get(i) : "";
                if (textRenderer.getWidth(text) <= LINE_PIXEL_W) break;

                // letzten word-boundary split finden der noch reinpasst
                String fits = "", overflow = "";
                boolean found = false;
                for (int j = text.length() - 1; j > 0; j--) {
                    if (text.charAt(j) == ' ') {
                        String cand = text.substring(0, j);
                        if (textRenderer.getWidth(cand) <= LINE_PIXEL_W) {
                            fits    = cand;
                            overflow = text.substring(j + 1);
                            found   = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    // hard-wrap: das längste prefix das noch reinpasst
                    for (int j = text.length() - 1; j > 0; j--) {
                        if (textRenderer.getWidth(text.substring(0, j)) <= LINE_PIXEL_W) {
                            fits    = text.substring(0, j);
                            overflow = text.substring(j);
                            found   = true;
                            break;
                        }
                    }
                }
                if (!found || overflow.isEmpty()) break;

                int next = i + 1;
                if (next <= locked) break;

                // diese zeile updaten
                while (p.size() <= i) p.add("");
                p.set(i, fits);
                if (lineWidgets[i] != null) lineWidgets[i].setText(fits);

                // overflow vor die nächste zeile schieben
                String nextExisting = next < p.size() ? p.get(next) : "";
                String nextNew = nextExisting.isEmpty() ? overflow : overflow + " " + nextExisting;
                while (p.size() <= next) p.add("");
                p.set(next, nextNew);
                if (lineWidgets[next] != null) lineWidgets[next].setText(nextNew);

                // tastatur-focus mit verschieben falls hier grad getippt wurde
                if (lineWidgets[i] != null && lineWidgets[i].isFocused()) {
                    focusLine(next);
                }
            }

            // page-overflow: wenn die letzte zeile immer noch nicht reinpasst, das
            // overflow-wort in die erste editierbare zeile der nächsten seite mitnehmen.
            final int LAST = MAX_LINES - 1;
            if (LAST > locked) {
                String lastText = LAST < p.size() ? p.get(LAST) : "";
                if (textRenderer.getWidth(lastText) > LINE_PIXEL_W) {
                    String fits = "", overflow = "";
                    boolean found = false;
                    for (int j = lastText.length() - 1; j > 0; j--) {
                        if (lastText.charAt(j) == ' ') {
                            String cand = lastText.substring(0, j);
                            if (textRenderer.getWidth(cand) <= LINE_PIXEL_W) {
                                fits = cand; overflow = lastText.substring(j + 1); found = true; break;
                            }
                        }
                    }
                    if (!found) {
                        for (int j = lastText.length() - 1; j > 0; j--) {
                            if (textRenderer.getWidth(lastText.substring(0, j)) <= LINE_PIXEL_W) {
                                fits = lastText.substring(0, j); overflow = lastText.substring(j); found = true; break;
                            }
                        }
                    }
                    if (found && !overflow.isEmpty()) {
                        int nextPi = currentPage + 1;
                        if (nextPi >= pages.size() && (pages.size() - readOnlyPageCount) < MAX_PAGES) {
                            pages.add(newBlankPage());
                            pageNewlines.add(new boolean[MAX_LINES]);
                        }
                        if (nextPi < pages.size()) {
                            int nextLocked = getOriginalLastLocked(nextPi);
                            int firstEditable = nextLocked + 1;
                            if (firstEditable < MAX_LINES) {
                                List<String> nextPage = pages.get(nextPi);
                                while (nextPage.size() <= firstEditable) nextPage.add("");
                                String existing = nextPage.get(firstEditable);
                                nextPage.set(firstEditable, existing.isEmpty() ? overflow : overflow + " " + existing);
                                // letzte zeile der aktuellen seite kürzen
                                while (p.size() <= LAST) p.add("");
                                p.set(LAST, fits);
                                if (lineWidgets[LAST] != null) lineWidgets[LAST].setText(fits);
                                // zur nächsten seite springen falls die letzte zeile focus hatte
                                if (lineWidgets[LAST] != null && lineWidgets[LAST].isFocused()) {
                                    switchPage(nextPi);
                                    focusLine(firstEditable);
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            inWrap = false;
        }
    }

    /** Returns the original last locked line index for a page, or -1 if no locked lines. */
    private int getOriginalLastLocked(int pageIdx) {
        if (pageIdx < originalLastContentLine.length) return originalLastContentLine[pageIdx];
        return -1;
    }

    private void focusLine(int idx) {
        if (idx >= 0 && idx < MAX_LINES && lineWidgets[idx] != null) {
            setFocused(lineWidgets[idx]);
            lineWidgets[idx].setFocused(true);
            focusedLine = idx;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawTexture(TEX_LETTER, frameX, frameY, FRAME_W, FRAME_H,
                0, 0, TEX_LETTER_W, TEX_LETTER_H, TEX_LETTER_W, TEX_LETTER_H);

        String title = viewOnly ? "Brief lesen" : (readOnlyPageCount > 0 ? "Brief versenden" : "Brief schreiben");
        int titleW = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title, frameX + (FRAME_W - titleW) / 2, frameY + 8, TITLE_COLOR, false);
        ctx.fill(frameX + 12, frameY + 19, frameX + FRAME_W - 12, frameY + 20, LINE_COLOR);

        if (isLoading) {
            renderLoading(ctx);
            if (System.currentTimeMillis() - loadingStart >= loadingDuration) {
                isLoading = false;
                promoteSentContent();
            }
            return;
        }

        // linke rand-bar markiert gelockte (schon gesendete) zeilen anstatt nem vollen overlay,
        // damit der pergament-text gut lesbar bleibt.
        int lastLocked = getOriginalLastLocked(currentPage);
        for (int i = 0; i <= lastLocked && i < MAX_LINES; i++) {
            int ly = textY + i * LINE_H;
            ctx.fill(textX - 5, ly, textX - 2, ly + LINE_H - 2, 0xFF9B7020);
        }

        super.render(ctx, mouseX, mouseY, delta);
        // read-only zeilen manuell rendern, sonst werden § format codes nicht interpretiert
        // und der vanilla TextFieldWidget drop shadow soll auch weg bleiben.
        int lastLockedRO = getOriginalLastLocked(currentPage);
        List<String> roPage = pages.get(currentPage);
        for (int i = 0; i <= lastLockedRO && i < MAX_LINES; i++) {
            String line = i < roPage.size() ? roPage.get(i) : "";
            if (!line.isEmpty()) {
                // die &-codes (vom server gespeichert) für die anzeige wieder in § umwandeln
                ctx.drawText(textRenderer, ampToSection(line), textX, textY + i * LINE_H, TITLE_COLOR, false);
            }
            ctx.fill(textX - 4, textY + i * LINE_H, textX - 2, textY + i * LINE_H + LINE_H - 2, READ_ONLY_COLOR);
        }
        for (int i = Math.max(0, lastLockedRO + 1); i < MAX_LINES; i++) {
            if (lineWidgets[i] == null) continue;
            String lt = lineWidgets[i].getText();
            if (!lt.isEmpty())
                ctx.drawText(textRenderer, lt, textX, textY + i * LINE_H, TITLE_COLOR, false);
            if (lineWidgets[i].isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cp = Math.min(lineWidgets[i].getCursor(), lt.length());
                int cx = textX + textRenderer.getWidth(lt.substring(0, cp));
                ctx.fill(cx, textY + i * LINE_H - 1, cx + 1, textY + i * LINE_H + 9, TITLE_COLOR);
            }
        }
        for (int i = 0; i < MAX_LINES; i++) {
            int gy = textY + i * LINE_H + LINE_H - 1;
            ctx.fill(textX, gy, textX + textW, gy + 1, LINE_COLOR);
        }

        if (currentPage > 0) {
            boolean hl = hit(mouseX, mouseY, prevBtnX, prevBtnY, PAGE_NAV_W, PAGE_NAV_H);
            ctx.drawTexture(hl ? TEX_PAGE_BACK_HL : TEX_PAGE_BACK,
                    prevBtnX, prevBtnY, PAGE_NAV_W, PAGE_NAV_H,
                    0, 0, PAGE_NAV_W, PAGE_NAV_H, PAGE_NAV_W, PAGE_NAV_H);
        }
        String ps = "Seite " + (currentPage + 1) + " / " + pages.size();
        int psW = textRenderer.getWidth(ps);
        ctx.drawText(textRenderer, ps, frameX + (FRAME_W - psW) / 2, navY + 2, TITLE_COLOR, false);
        if (currentPage < pages.size() - 1) {
            boolean hl = hit(mouseX, mouseY, nextBtnX, nextBtnY, PAGE_NAV_W, PAGE_NAV_H);
            ctx.drawTexture(hl ? TEX_PAGE_FWD_HL : TEX_PAGE_FWD,
                    nextBtnX, nextBtnY, PAGE_NAV_W, PAGE_NAV_H,
                    0, 0, PAGE_NAV_W, PAGE_NAV_H, PAGE_NAV_W, PAGE_NAV_H);
        }

        drawIconBtn(ctx, cancelBtnX, cancelBtnY, TEX_EXIT, mouseX, mouseY, true);

        if (!viewOnly) {
            boolean saveEnabled    = !newContentSaved;
            boolean addPageEnabled = (pages.size() - readOnlyPageCount) < MAX_PAGES;
            drawIconBtn(ctx, saveBtnX,    saveBtnY,    TEX_SAVE,  mouseX, mouseY, saveEnabled);
            drawIconBtn(ctx, addPageBtnX, addPageBtnY, TEX_EMPTY, mouseX, mouseY, addPageEnabled);
            int plusColor = addPageEnabled ? TITLE_COLOR : 0xFF888877;
            int plusX = addPageBtnX + (ICON_BTN_W - textRenderer.getWidth("+")) / 2;
            int plusY = addPageBtnY + (ICON_BTN_H - 7) / 2;
            ctx.drawText(textRenderer, "+", plusX, plusY, plusColor, false);

            drawSendBtn(ctx, sendBtnX, sendBtnY, SEND_BTN_W, SEND_BTN_H, "Senden", mouseX, mouseY, newContentSaved);

            if (!isLoading) renderFormattingBar(ctx, mouseX, mouseY);

            // tooltips zuletzt zeichnen damit sie oben drüber liegen
            if (hit(mouseX, mouseY, saveBtnX, saveBtnY, ICON_BTN_W, ICON_BTN_H)) {
                String tip = saveEnabled ? "Niederschreiben" : "Keine Änderungen";
                ctx.drawTooltip(textRenderer, Text.literal(tip), mouseX, mouseY);
            } else if (addPageEnabled && hit(mouseX, mouseY, addPageBtnX, addPageBtnY, ICON_BTN_W, ICON_BTN_H)) {
                ctx.drawTooltip(textRenderer, Text.literal("+ Neue Seite"), mouseX, mouseY);
            }
        }
    }

    private void renderLoading(DrawContext ctx) {
        ctx.fill(frameX + 1, frameY + 21, frameX + FRAME_W - 1, frameY + FRAME_H - 1, 0xBB2A1A08);

        int cy = frameY + FRAME_H / 2 - 8;
        int dots = (int) ((System.currentTimeMillis() - loadingStart) / LOADING_DOT_MS % 4);
        String msg = "Wird gesendet" + ".".repeat(dots);
        int mw = textRenderer.getWidth(msg);
        ctx.drawText(textRenderer, msg, frameX + (FRAME_W - mw) / 2, cy, 0xFFFFE0AA, false);

        String[] spinner = {"|", "/", "-", "\\"};
        int si = (int) ((System.currentTimeMillis() - loadingStart) / 200 % 4);
        String sp = spinner[si];
        int sw = textRenderer.getWidth(sp);
        ctx.drawText(textRenderer, sp, frameX + (FRAME_W - sw) / 2, cy + 12, 0xFFCCAA60, false);
    }

    private void renderFormattingBar(DrawContext ctx, int mouseX, int mouseY) {
        int lastLockedForBar = getOriginalLastLocked(currentPage);
        boolean pageEditable = (lastLockedForBar < MAX_LINES - 1);
        int barAlpha = pageEditable ? 0xCC : 0x55; // dim wenn alle zeilen gelockt sind

        ctx.fill(frameX + 6, fmtBarY - 1, frameX + FRAME_W - 6, fmtBarY, 0x33402010);

        for (int i = 0; i < 16; i++) {
            int x = fmtColorsX + i * 7;
            int y = fmtBarY + 2;
            int rgb = FMT_COLOR_RGBS[i];
            int col = (barAlpha << 24) | (rgb & 0xFFFFFF);
            // der schwarze swatch braucht nen kontrast-rand, sonst sieht man ihn aufm pergament nicht
            if (i == 0) ctx.fill(x - 1, y - 1, x + 7, y + 7, (barAlpha << 24) | 0x7A6450);
            ctx.fill(x, y, x + 6, y + 6, col);
            if (pageEditable && hit(mouseX, mouseY, x, y, 6, 6))
                ctx.fill(x, y, x + 6, y + 6, 0x55FFFFFF);
        }

        int lastStyleIdx = FMT_STYLE_CODES.length - 1; // reset button ist immer der letzte eintrag
        for (int i = 0; i < FMT_STYLE_CODES.length; i++) {
            int x = fmtStylesX + i * 10;
            int y = fmtBarY + 1;
            boolean hovered = pageEditable && hit(mouseX, mouseY, x, y, 8, 8);
            ctx.fill(x, y, x + 8, y + 8, hovered ? 0x44402010 : 0x22402010);
            String lbl = FMT_STYLE_LABELS[i];
            int lw = textRenderer.getWidth(lbl);
            int lblColor = pageEditable ? (i == lastStyleIdx ? 0xFF7A6450 : 0xFF3A2010) : 0xFF888877;
            ctx.drawText(textRenderer, lbl, x + (8 - lw) / 2, y + 1, lblColor, false);
        }
    }

    private void insertFormattingCode(String code) {
        int lastLocked = getOriginalLastLocked(currentPage);
        if (lastLocked >= MAX_LINES - 1) return; // alle zeilen auf der seite sind gelockt
        int target = focusedLine;
        if (target <= lastLocked) target = lastLocked + 1;
        if (target >= MAX_LINES || lineWidgets[target] == null) return;
        // direkt setText/setCursor nutzen um SharedConstants.stripInvalidChars zu umgehen,
        // das würde sonst das § aus TextFieldWidget.write() rausfiltern
        TextFieldWidget field = lineWidgets[target];
        String cur = field.getText();
        int pos = Math.min(field.getCursor(), cur.length());
        String newText = cur.substring(0, pos) + code + cur.substring(pos);
        if (newText.length() <= MAX_LINE_CHARS) {
            field.setText(newText);
            field.setCursor(pos + code.length(), false);
        }
        field.setFocused(true);
        this.setFocused(field);
    }

    private void drawIconBtn(DrawContext ctx, int x, int y, Identifier tex, int mx, int my, boolean enabled) {
        ctx.drawTexture(tex, x, y, ICON_BTN_W, ICON_BTN_H, 0, 0, ICON_BTN_W, ICON_BTN_H, ICON_BTN_W, ICON_BTN_H);
        if (!enabled) {
            ctx.fill(x, y, x + ICON_BTN_W, y + ICON_BTN_H, 0xAABBBBBB);
        } else if (hit(mx, my, x, y, ICON_BTN_W, ICON_BTN_H)) {
            ctx.fill(x, y, x + ICON_BTN_W, y + ICON_BTN_H, 0x33FFFFFF);
        }
    }

    private void drawSendBtn(DrawContext ctx, int x, int y, int w, int h,
                             String label, int mx, int my, boolean enabled) {
        NineSliceRenderer.drawThreeTile(ctx, x, y, w, h);
        if (!enabled) {
            ctx.fill(x, y, x + w, y + h, 0x88888888);
        } else if (hit(mx, my, x, y, w, h)) {
            ctx.fill(x, y, x + w, y + h, 0x22FFFFFF);
        }
        int fg = !enabled ? 0xFF888877 : (hit(mx, my, x, y, w, h) ? 0xFFFFE0AA : 0xFFEECCAA);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, x + (w - lw) / 2, y + (h - 7) / 2, fg, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isLoading) return true; // alle inputs blocken während gesendet wird
        if (keyCode == 257 || keyCode == 335) {
            if (focusedLine < MAX_LINES - 1) {
                int nextLine = focusedLine + 1;
                // als expliziten absatzumbruch markieren (kein word-wrap)
                if (currentPage < pageNewlines.size()) pageNewlines.get(currentPage)[nextLine] = true;
                focusLine(nextLine);
            }
            return true;
        }
        if (keyCode == 258) {
            focusLine((focusedLine + 1) % MAX_LINES);
            return true;
        }
        if (keyCode == 259 && focusedLine > 0
                && lineWidgets[focusedLine] != null
                && lineWidgets[focusedLine].getCursor() == 0
                && lineWidgets[focusedLine].getText().isEmpty()) {
            focusLine(focusedLine - 1);
            lineWidgets[focusedLine].setCursorToEnd(false);
            return true;
        }
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isLoading) return true; // klicks während dem senden blockieren
        int mx = (int) mouseX, my = (int) mouseY;

        if (currentPage > 0 && hit(mx, my, prevBtnX, prevBtnY, PAGE_NAV_W, PAGE_NAV_H)) {
            switchPage(currentPage - 1); return true;
        }
        if (currentPage < pages.size() - 1 && hit(mx, my, nextBtnX, nextBtnY, PAGE_NAV_W, PAGE_NAV_H)) {
            switchPage(currentPage + 1); return true;
        }
        if (hit(mx, my, cancelBtnX, cancelBtnY, ICON_BTN_W, ICON_BTN_H)) {
            close(); return true;
        }
        // ab hier wird der brief verändert, also view-only aufrufer blocken
        if (viewOnly) return true;
        if (!newContentSaved && hit(mx, my, saveBtnX, saveBtnY, ICON_BTN_W, ICON_BTN_H)) {
            saveCurrentPage(); startSending(); return true;
        }
        if ((pages.size() - readOnlyPageCount) < MAX_PAGES && hit(mx, my, addPageBtnX, addPageBtnY, ICON_BTN_W, ICON_BTN_H)) {
            pages.add(newBlankPage()); pageNewlines.add(new boolean[MAX_LINES]); switchPage(pages.size() - 1); return true;
        }
        if (newContentSaved && hit(mx, my, sendBtnX, sendBtnY, SEND_BTN_W, SEND_BTN_H)) {
            MinecraftClient.getInstance().setScreen(new PostRecipientScreen());
            return true;
        }
        // toolbar-klicks vor super laufen lassen, sonst geht der focus aufm aktiven TextField
        // verloren wenn man auf nen swatch klickt
        if (!isLoading && getOriginalLastLocked(currentPage) < MAX_LINES - 1) {
            for (int i = 0; i < 16; i++) {
                int bx = fmtColorsX + i * 7;
                int by = fmtBarY + 2;
                if (hit(mx, my, bx, by, 6, 6)) {
                    insertFormattingCode("\u00a7" + FMT_COLOR_CODES[i]);
                    return true;
                }
            }
            for (int i = 0; i < FMT_STYLE_CODES.length; i++) {
                int bx = fmtStylesX + i * 10;
                int by = fmtBarY + 1;
                if (hit(mx, my, bx, by, 8, 8)) {
                    insertFormattingCode("\u00a7" + FMT_STYLE_CODES[i]);
                    return true;
                }
            }
        }
        for (int i = 0; i < MAX_LINES; i++) {
            if (lineWidgets[i] != null && lineWidgets[i].isMouseOver(mouseX, mouseY)) {
                focusedLine = i; break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void switchPage(int newPage) {
        saveCurrentPage();
        currentPage = newPage;
        List<String> page = pages.get(currentPage);
        int lastLocked = getOriginalLastLocked(currentPage);
        for (int i = 0; i < MAX_LINES; i++) {
            if (lineWidgets[i] != null) {
                lineWidgets[i].setText(ampToSection(i < page.size() ? page.get(i) : ""));
                boolean isLineReadOnly = (i <= lastLocked);
                lineWidgets[i].setEditable(!isLineReadOnly);
                lineWidgets[i].setEditableColor(isLineReadOnly ? READ_ONLY_COLOR : TITLE_COLOR);
            }
        }
        focusLine(lastLocked + 1 < MAX_LINES ? lastLocked + 1 : 0);
    }

    /** index der letzten nicht-leeren zeile auf ner seite, oder -1 wenn alle leer sind.
     *  zeilen mit nur \u200C anchor-zeichen (von startSending hinzugef\u00FCgt) gelten als leer. */
    private static int getLastContentLine(List<String> page) {
        for (int j = Math.min(MAX_LINES, page.size()) - 1; j >= 0; j--) {
            if (!isAnchorOnly(page.get(j))) return j;
        }
        return -1;
    }

    /** true wenn der string leer ist oder nur aus \u200C anchor-zeichen besteht. */
    private static boolean isAnchorOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '\u200C') return false;
        }
        return true;
    }

    void saveCurrentPage() {
        List<String> page = pages.get(currentPage);
        for (int i = 0; i < MAX_LINES; i++) {
            while (page.size() <= i) page.add("");
            page.set(i, lineWidgets[i] != null ? lineWidgets[i].getText() : "");
        }
    }

    private void startSending() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sendIndex = 0;

        // letzte seite finden die wirklich neuen editierbaren content hat
        int lastContentPage = -1;
        for (int pi = pages.size() - 1; pi >= 0; pi--) {
            int sl = getOriginalLastLocked(pi) + 1;
            List<String> pg = pages.get(pi);
            for (int j = sl; j < Math.min(MAX_LINES, pg.size()); j++) {
                if (!pg.get(j).isEmpty()) { lastContentPage = pi; break; }
            }
            if (lastContentPage >= 0) break;
        }
        if (lastContentPage < 0) return; // nichts zu senden

        for (int pi = 0; pi <= lastContentPage; pi++) {
            List<String> page = pages.get(pi);
            int startLine = getOriginalLastLocked(pi) + 1;
            if (startLine >= MAX_LINES) continue; // alle zeilen dieser seite sind gelockt

            int sendUpTo;
            if (pi < lastContentPage) {
                // nicht die letzte seite: leere zeilen am ende pad'n, damit folgeseiten verankert bleiben
                sendUpTo = MAX_LINES - 1;
            } else {
                // letzte content-seite: leere zeilen am ende abschneiden
                sendUpTo = -1;
                for (int j = MAX_LINES - 1; j >= startLine; j--) {
                    String ln = j < page.size() ? page.get(j) : "";
                    if (!ln.isEmpty()) { sendUpTo = j; break; }
                }
                if (sendUpTo < 0) continue;
            }

            // word-wrap'te zeilen werden zu einem absatz zusammengefasst; nur echte
            // Enter-presses (in pageNewlines getrackt) produzieren ein literales \n
            boolean[] newlines = (pi < pageNewlines.size()) ? pageNewlines.get(pi) : new boolean[MAX_LINES];
            java.util.List<String> paragraphs = new java.util.ArrayList<>();
            StringBuilder para = new StringBuilder();
            for (int j = startLine; j <= sendUpTo; j++) {
                String line = j < page.size() ? page.get(j) : "";
                boolean isExplicitBreak = (j > startLine) && (j < MAX_LINES) && newlines[j];
                if (isExplicitBreak) {
                    paragraphs.add(para.toString());
                    para = new StringBuilder();
                } else if (j > startLine && !line.isEmpty()) {
                    para.append(" "); // word-wrap fortsetzung mit space verbinden
                }
                para.append(line);
            }
            paragraphs.add(para.toString());
            StringBuilder sb = new StringBuilder();
            for (int p = 0; p < paragraphs.size(); p++) {
                if (p > 0) sb.append("\\n");
                String paraText = paragraphs.get(p);
                // der \u200C anchor auf nicht-letzten abs\u00E4tzen verhindert dass der server
                // den trailing line break beim speichern wegschluckt.
                if (p < paragraphs.size() - 1) {
                    sb.append(paraText.isEmpty() ? "\u200C" : paraText + "\u200C");
                } else {
                    sb.append(paraText.isEmpty() ? "\u200C" : paraText);
                }
            }

            if (sb.length() == 0) continue;

            // Minecraft command string limit ist 256 zeichen; "letter " prefix = 7 zeichen, also payload max 248
            if (sb.length() > 248) sb.setLength(248);

            // § is rejected by the MC server protocol (\u00A7 = disallowed in commands).
            // §x zu &x umwandeln, das server-side plugin macht via ChatColor wieder § draus.
            for (int r = 0; r < sb.length() - 1; r++) {
                if (sb.charAt(r) == '\u00a7') {
                    char nx = sb.charAt(r + 1);
                    if ((nx >= '0' && nx <= '9') || (nx >= 'a' && nx <= 'f')
                            || nx == 'k' || nx == 'l' || nx == 'm'
                            || nx == 'n' || nx == 'o' || nx == 'r') {
                        sb.setCharAt(r, '&');
                    }
                }
            }

            final String cmd   = "letter " + sb;
            final long   delay = (long) sendIndex * 1200L;
            sendIndex++;
            SCHEDULER.schedule(() -> mc.send(() -> {
                if (mc.player != null) mc.player.networkHandler.sendChatCommand(cmd);
            }), delay, TimeUnit.MILLISECONDS);
        }

        loadingDuration = Math.max(1200L, (long) sendIndex * 1200L);
        loadingStart    = System.currentTimeMillis();
        isLoading       = true;
    }

    /** nachdem die /letter commands raus sind: alle seiten mit content lock'n, widgets neu init'n. */
    private void promoteSentContent() {
        newContentSaved = true;
        int newLockedCount = 0;
        for (int pi = 0; pi < pages.size(); pi++) {
            List<String> page = pages.get(pi);
            for (int j = 0; j < Math.min(MAX_LINES, page.size()); j++) {
                if (!page.get(j).isEmpty()) { newLockedCount = pi + 1; break; }
            }
        }
        int[] newLocked = new int[newLockedCount];
        for (int pi = 0; pi < newLockedCount; pi++) {
            if (pi < newLockedCount - 1) {
                newLocked[pi] = MAX_LINES - 1; // alles ausser der letzten seite: komplett gelockt
            } else {
                int last = -1;
                List<String> page = pages.get(pi);
                for (int j = Math.min(MAX_LINES, page.size()) - 1; j >= 0; j--) {
                    if (!isAnchorOnly(page.get(j))) { last = j; break; }
                }
                newLocked[pi] = last;
            }
        }
        readOnlyPageCount       = newLockedCount;
        originalLastContentLine = newLocked;
        if (pages.size() <= readOnlyPageCount) pages.add(newBlankPage());
        // re-init räumt children weg + ruft init wieder auf, damit der neue lock-state in den widgets greift
        init(MinecraftClient.getInstance(), this.width, this.height);
    }

    @Override
    public boolean shouldPause() { return false; }

    /** wandelt &X colour/style codes (wie der server sie speichert) in \u00a7 codes f\u00fcrs rendering um. */
    private static String ampToSection(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                if ((n >= '0' && n <= '9') || (n >= 'a' && n <= 'f')
                        || n == 'k' || n == 'l' || n == 'm' || n == 'n' || n == 'o' || n == 'r') {
                    sb.append('\u00a7');
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * TextFieldWidget das sein eigenes rendering unterdrückt, damit LetterScreen
     * den text manuell ohne den built-in drop shadow zeichnen kann.
     * volle keyboard/mouse interaktion bleibt erhalten (visible = true).
     */
    private static final class ShadowFreeField extends TextFieldWidget {
        ShadowFreeField(net.minecraft.client.font.TextRenderer tr, int x, int y, int w, int h) {
            super(tr, x, y, w, h, Text.empty());
        }
        @Override
        public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            // wird unterdrückt, LetterScreen.render() zeichnet den text ohne shadow
        }
    }
}
