package systems.kinau.fishingbot.modules.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.modules.command.BrigardierCommand;
import systems.kinau.fishingbot.modules.command.executor.CommandExecutor;

public class CommandSummary extends BrigardierCommand {

    public CommandSummary() {
        super("summary", FishingBot.getI18n().t("command-summary-desc"), "summarize", "stats", "statistics", "caught", "loot");
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandExecutor> builder) {
        builder.then(literal("clear")
                        .executes(getExecutor(true)))
                .executes(getExecutor(false));
    }

    private Command<CommandExecutor> getExecutor(boolean clearAfterwards) {
        return context -> {
            context.getSource().sendMessage("This command is disabled because the fishing module has been removed.");
            return 0;
        };
    }

    @Override
    public String getSyntax(String label) {
        return FishingBot.getI18n().t("command-summary-syntax", label);
    }
}
