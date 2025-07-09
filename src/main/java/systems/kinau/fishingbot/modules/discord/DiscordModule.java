package systems.kinau.fishingbot.modules.discord;

import lombok.Getter;
import lombok.Setter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.EventHandler;
import systems.kinau.fishingbot.event.Listener;
import systems.kinau.fishingbot.event.custom.RespawnEvent;
import systems.kinau.fishingbot.event.play.UpdateExperienceEvent;
import systems.kinau.fishingbot.event.play.UpdateHealthEvent;
import systems.kinau.fishingbot.modules.Module;

import java.text.NumberFormat;
import java.util.Locale;

public class DiscordModule extends Module implements Listener {

    private final DiscordDetails DISCORD_DETAILS = new DiscordDetails("FishingBot", "https://vignette.wikia.nocookie.net/mcmmo/images/2/2f/FishingRod.png");

    @Getter private DiscordMessageDispatcher discord;

    @Getter @Setter private float health = -1;
    @Getter @Setter private int level = -1;

    @Override
    public void onEnable() {
        FishingBot.getInstance().getCurrentBot().getEventManager().registerListener(this);
        // Activate Discord web hook
        if (FishingBot.getInstance().getCurrentBot().getConfig().isWebHookEnabled() && !FishingBot.getInstance().getCurrentBot().getConfig().getWebHook().equalsIgnoreCase("false")
                && !FishingBot.getInstance().getCurrentBot().getConfig().getWebHook().equals("YOURWEBHOOK"))
            this.discord = new DiscordMessageDispatcher(FishingBot.getInstance().getCurrentBot().getConfig().getWebHook());
    }

    @Override
    public void onDisable() {
        FishingBot.getInstance().getCurrentBot().getEventManager().unregisterListener(this);
        if (this.discord != null)
            this.discord.shutdown();
        this.discord = null;
    }

    private String getFooter() {
        if (FishingBot.getInstance().getCurrentBot() == null)
            return "";
        return FishingBot.getInstance().getCurrentBot().getAuthData().getUsername();
    }

    @EventHandler
    public void onUpdateHealth(UpdateHealthEvent event) {
        if (getDiscord() == null) return;
        if (event.getEid() != FishingBot.getInstance().getCurrentBot().getPlayer().getEntityID())
            return;
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.ROOT);
        numberFormat.setMaximumFractionDigits(2);
        String mention = FishingBot.getInstance().getCurrentBot().getConfig().getPingOnEnchantmentMention() + " ";
        if (mention.equals("<@USER_ID> "))
            mention = "";
        if (FishingBot.getInstance().getCurrentBot().getConfig().isAlertOnAttack() && getHealth() > event.getHealth()) {
            if (getDiscord() == null) return;
            if (!mention.isEmpty())
                getDiscord().dispatchMessage(mention, DISCORD_DETAILS);
            getDiscord().dispatchEmbed(FishingBot.getI18n().t("config-announces-discord-alert-on-attack"), 0xff0000,
                    "https://raw.githubusercontent.com/MrKinau/FishingBot/master/src/main/resources/img/general/heart.png",
                    FishingBot.getI18n().t("discord-webhook-damage", numberFormat.format(event.getHealth())),
                    getFooter(), DISCORD_DETAILS);
        }
        this.health = event.getHealth();
    }

    @EventHandler
    public void onXP(UpdateExperienceEvent event) {
        if (getDiscord() == null) return;
        if (getLevel() != event.getLevel() && FishingBot.getInstance().getCurrentBot().getConfig().isAlertOnLevelUpdate()) {
            getDiscord().dispatchEmbed(FishingBot.getI18n().t("config-announces-discord-alert-on-level-update"), 0xb5ea3a,
                    "https://raw.githubusercontent.com/MrKinau/FishingBot/master/src/main/resources/img/general/xp.png",
                    FishingBot.getI18n().t("announce-level-up", String.valueOf(event.getLevel())),
                    getFooter(), DISCORD_DETAILS);
        }
        this.level = event.getLevel();
    }

    @EventHandler
    public void onRespawn(RespawnEvent event) {
        if (getDiscord() == null) return;
        if (FishingBot.getInstance().getCurrentBot().getConfig().isAlertOnRespawn()) {
            String mention = FishingBot.getInstance().getCurrentBot().getConfig().getPingOnEnchantmentMention() + " ";
            if (mention.equals("<@USER_ID> "))
                mention = "";
            if (!mention.isEmpty())
                getDiscord().dispatchMessage(mention, DISCORD_DETAILS);
            getDiscord().dispatchEmbed(FishingBot.getI18n().t("config-announces-discord-alert-on-respawn"), 0x666666,
                    "https://raw.githubusercontent.com/MrKinau/FishingBot/master/src/main/resources/img/general/heart.png",
                    FishingBot.getI18n().t("discord-webhook-respawn"),
                    getFooter(), DISCORD_DETAILS);
        }
    }

}
