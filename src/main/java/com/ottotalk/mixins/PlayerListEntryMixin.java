package com.ottotalk.mixins;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.context.CharacterNameResolver;
import com.ottotalk.context.PlayerNameList;
import com.ottotalk.gui.ServerChatState;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tauscht den Account-Namen in der Tab-Liste gegen den RP-Charakternamen,
 * wenn showCharacterNames und showNamesInTablist beide an sind.
 * Color-remap läuft eh immer wenn showCharacterNames an ist.
 */
@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void ottotalk_replaceTablistName(CallbackInfoReturnable<Text> cir) {
        com.ottotalk.config.OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (!cfg.showCharacterNames) return;
        if (!ServerChatState.isOnOttonien()) return;

        // Color-remap vom vanilla Display Name auch wenn Tablist Replacement aus ist
        if (!cfg.showNamesInTablist) {
            Text vanilla = cir.getReturnValue();
            if (vanilla != null) {
                Text remapped = CharacterNameResolver.remapTextColors(vanilla, cfg);
                if (!remapped.getString().equals(vanilla.getString()) ||
                        !remapped.getStyle().equals(vanilla.getStyle())) {
                    cir.setReturnValue(remapped);
                }
            }
            return;
        }

        PlayerListEntry self = (PlayerListEntry) (Object) this;
        String accountName = self.getProfile().getName();
        if (accountName == null || accountName.isEmpty()) return;

        String characterName = PlayerNameList.getCharacterName(accountName);
        if (characterName == null || characterName.equals("Unbekannt")) return;

        final int nameRgb = cfg.colorName;
        String characterTitle = PlayerNameList.getCharacterTitle(accountName);
        if (characterTitle != null && !characterTitle.isEmpty()) {
            int rawColor = PlayerNameList.getCharacterTitleColor(accountName);
            final int titleColorRgb = cfg.mapTitleColor(rawColor);
            MutableText result = Text.literal(characterTitle)
                    .styled(s -> s.withColor(TextColor.fromRgb(titleColorRgb)));
            result.append(Text.literal(" " + characterName)
                    .styled(s -> s.withColor(TextColor.fromRgb(nameRgb))));
            cir.setReturnValue(result);
        } else {
            cir.setReturnValue(Text.literal(characterName)
                    .styled(s -> s.withColor(TextColor.fromRgb(nameRgb))));
        }
    }
}
