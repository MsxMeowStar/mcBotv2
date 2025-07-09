package systems.kinau.fishingbot.modules.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.modules.command.BrigardierCommand;
import systems.kinau.fishingbot.modules.command.executor.CommandExecutor;

public class CommandStuck extends BrigardierCommand {

    public CommandStuck() {
        super("stuck", FishingBot.getI18n().t("command-stuck-desc"), "recast", "reeject", "refish", "recatch");
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandExecutor> builder) {
        builder.executes(context -> {
            context.getSource().sendMessage("This command is disabled because the fishing module has been removed.");
            return 0;
        });
    }
}
