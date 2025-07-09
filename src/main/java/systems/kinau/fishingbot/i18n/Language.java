package systems.kinau.fishingbot.i18n;

import lombok.AllArgsConstructor;
import lombok.Getter;
import systems.kinau.fishingbot.gui.config.DisplayNameProvider;

import java.util.Arrays;
import java.util.Locale;

/**
 * Created: 13.10.2020
 *
 * @author Summerfeeling
 */
@AllArgsConstructor
@Getter
public enum Language implements DisplayNameProvider {

    CHINESE_SIMPLIFIED(Locale.of("zh", "CN"), "Chinese Simplified"),
    CHINESE_TRADITIONAL(Locale.of("zh", "TW"), "Chinese Traditional"),
    ENGLISH(Locale.of("en", "EN"), "English");

    private final Locale locale;
    private final String displayName;

    public String getLanguageCode() {
        return locale.toLanguageTag().replace("-", "_");
    }

    public static Language getByLocale(Locale locale) {
        return Arrays.stream(values())
                .filter(language -> language.locale.equals(locale))
                .findAny()
                .orElse(Language.ENGLISH);
    }

}
