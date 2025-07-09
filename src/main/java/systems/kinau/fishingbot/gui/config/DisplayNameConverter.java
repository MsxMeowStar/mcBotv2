package systems.kinau.fishingbot.gui.config;

import javafx.util.StringConverter;

public class DisplayNameConverter extends StringConverter<Enum> {
    @Override
    public String toString(Enum object) {
        if (object instanceof DisplayNameProvider) {
            return ((DisplayNameProvider) object).getDisplayName();
        }
        return object.name();
    }

    @Override
    public Enum fromString(String string) {
        return null;
    }
}
