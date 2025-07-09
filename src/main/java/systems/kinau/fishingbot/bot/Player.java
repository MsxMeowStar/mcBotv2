/*
 * Created by David Luedtke (MrKinau)
 * 2019/10/18
 */

package systems.kinau.fishingbot.bot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.EventHandler;
import systems.kinau.fishingbot.event.Listener;
import systems.kinau.fishingbot.event.configuration.ConfigurationStartEvent;
import systems.kinau.fishingbot.event.custom.RespawnEvent;
import systems.kinau.fishingbot.event.play.CommandsRegisteredEvent;
import systems.kinau.fishingbot.event.play.InventoryCloseEvent;
import systems.kinau.fishingbot.event.play.JoinGameEvent;
import systems.kinau.fishingbot.event.play.LookChangeEvent;
import systems.kinau.fishingbot.event.play.PingChangeEvent;
import systems.kinau.fishingbot.event.play.PosLookChangeEvent;
import systems.kinau.fishingbot.event.play.SetHeldItemEvent;
import systems.kinau.fishingbot.event.play.UpdateExperienceEvent;
import systems.kinau.fishingbot.event.play.UpdateHealthEvent;
import systems.kinau.fishingbot.event.play.UpdateSlotEvent;
import systems.kinau.fishingbot.event.play.UpdateWindowItemsEvent;
import systems.kinau.fishingbot.modules.command.brigardier.argument.MessageArgumentType;
import systems.kinau.fishingbot.modules.command.executor.CommandExecutor;
import systems.kinau.fishingbot.modules.command.executor.ConsoleCommandExecutor;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.protocol.ProtocolState;
import systems.kinau.fishingbot.network.protocol.play.PacketOutBlockPlace;
import systems.kinau.fishingbot.network.protocol.play.PacketOutChatCommand;
import systems.kinau.fishingbot.network.protocol.play.PacketOutChatMessage;
import systems.kinau.fishingbot.network.protocol.play.PacketOutClickWindow;
import systems.kinau.fishingbot.network.protocol.play.PacketOutClientStatus;
import systems.kinau.fishingbot.network.protocol.play.PacketOutCloseInventory;
import systems.kinau.fishingbot.network.protocol.play.PacketOutEntityAction;
import systems.kinau.fishingbot.network.protocol.play.PacketOutEntityAction.EntityAction;
import systems.kinau.fishingbot.network.protocol.play.PacketOutHeldItemChange;
import systems.kinau.fishingbot.network.protocol.play.PacketOutPlayerInput;
import systems.kinau.fishingbot.network.protocol.play.PacketOutPlayerLoaded;
import systems.kinau.fishingbot.network.protocol.play.PacketOutPosLook;
import systems.kinau.fishingbot.network.protocol.play.PacketOutTeleportConfirm;
import systems.kinau.fishingbot.network.protocol.play.PacketOutUnsignedChatCommand;
import systems.kinau.fishingbot.network.protocol.play.PacketOutUseItem;
import systems.kinau.fishingbot.network.utils.CryptManager;
import systems.kinau.fishingbot.utils.CommandUtils;
import systems.kinau.fishingbot.utils.ItemUtils;
import systems.kinau.fishingbot.utils.LocationUtils;
import systems.kinau.fishingbot.utils.Pair;
import systems.kinau.fishingbot.utils.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter
@Setter
public class Player implements Listener {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float originYaw = -255;
    private float originPitch = -255;

    private int experience;
    private int levels;
    private float health = -1;
    private boolean sentLowHealth;
    private boolean respawning;

    private int heldSlot;
    private Slot heldItem;
    private Inventory inventory;
    @Setter(AccessLevel.NONE)
    private final Map<Integer, Inventory> openedInventories = new HashMap<>();
    private Optional<CryptManager.MessageSignature> lastUsedSignature = Optional.empty();
    private int chatSessionIndex = 0;
    private CommandDispatcher<CommandExecutor> mcCommandDispatcher;

    private UUID uuid;

    private int entityID = -1;
    private int lastPing = 500;

    private Thread lookThread;

    public Player() {
        this.inventory = new Inventory();
        FishingBot.getInstance().getCurrentBot().getEventManager().registerListener(this);
    }

    @EventHandler
    public void onStartConfiguration(ConfigurationStartEvent event) {
        if (lookThread != null) {
            lookThread.interrupt();
            this.lookThread = null;
        }
    }

    @EventHandler
    public void onPosLookChange(PosLookChangeEvent event) {
        this.x = event.getX();
        this.y = event.getY();
        this.z = event.getZ();
        this.yaw = event.getYaw();
        this.pitch = event.getPitch();
        this.originYaw = yaw;
        this.originPitch = pitch;
        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() >= ProtocolConstants.MC_1_9)
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutTeleportConfirm(event.getTeleportId()));
    }

    @EventHandler
    public void onPosLookChange(LookChangeEvent event) {
        this.yaw = event.getYaw();
        this.pitch = event.getPitch();
        this.originYaw = yaw;
        this.originPitch = pitch;
    }

    @EventHandler
    public void onUpdateXP(UpdateExperienceEvent event) {
        if (getLevels() >= 0 && getLevels() < event.getLevel()) {
            FishingBot.getI18n().info("announce-level-up", String.valueOf(event.getLevel()));
            if (FishingBot.getInstance().getCurrentBot().getConfig().isAnnounceLvlUp() && !FishingBot.getInstance().getCurrentBot().getConfig().getAnnounceLvlUpText().equalsIgnoreCase("false"))
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatMessage(FishingBot.getInstance().getCurrentBot().getConfig().getAnnounceLvlUpText().replace("%lvl%", String.valueOf(event.getLevel()))));
        }

        this.levels = event.getLevel();
        this.experience = event.getExperience();
    }

    @EventHandler
    public void onSetHeldItem(SetHeldItemEvent event) {
        this.heldSlot = event.getSlot();
    }

    @EventHandler
    public void onUpdateSlot(UpdateSlotEvent event) {
        if (event.getWindowId() != 0)
            return;

        Slot slot = event.getSlot();

        if (getInventory() != null)
            getInventory().setItem(event.getSlotId(), slot);

        if (event.getSlotId() == getHeldSlot())
            this.heldItem = slot;
        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoLootEjectionEnabled()
                && !(event.getSlotId() == getHeldSlot() && ItemUtils.isFishingRod(slot)))
            FishingBot.getInstance().getCurrentBot().getEjectModule()
                    .executeEjectionRules(FishingBot.getInstance().getCurrentBot().getConfig().getAutoLootEjectionRules(), slot, event.getSlotId());
    }

    @EventHandler
    public void onUpdateWindow(UpdateWindowItemsEvent event) {
        if (event.getWindowId() == 0) {
            for (int i = 0; i < event.getSlots().size(); i++) {
                getInventory().setItem(i, event.getSlots().get(i));
                if (i == getHeldSlot())
                    this.heldItem = event.getSlots().get(i);
                if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoLootEjectionEnabled()
                        && !(i == getHeldSlot() && ItemUtils.isFishingRod(event.getSlots().get(i))))
                    FishingBot.getInstance().getCurrentBot().getEjectModule()
                            .executeEjectionRules(FishingBot.getInstance().getCurrentBot().getConfig().getAutoLootEjectionRules(), event.getSlots().get(i), (short) i);
            }
        } else if (event.getWindowId() > 0) {
            Inventory inventory;
            if (getOpenedInventories().containsKey(event.getWindowId()))
                inventory = getOpenedInventories().get(event.getWindowId());
            else {
                inventory = new Inventory();
                inventory.setWindowId(event.getWindowId());
                getOpenedInventories().put(event.getWindowId(), inventory);
            }
            for (int i = 0; i < event.getSlots().size(); i++)
                inventory.setItem(i, event.getSlots().get(i));
        }
    }

    @EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        getOpenedInventories().remove(event.getWindowId());
    }

    @EventHandler
    public void onJoinGame(JoinGameEvent event) {
        setEntityID(event.getEid());
        respawn();
    }

    @EventHandler
    public void onUpdateHealth(UpdateHealthEvent event) {
        if (event.getEid() != getEntityID())
            return;

        if (getHealth() != -1 && event.getHealth() <= 0 && getEntityID() != -1 && !isRespawning()) {
            setRespawning(true);
            FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new RespawnEvent());
            respawn();
        } else if (event.getHealth() > 0 && isRespawning())
            setRespawning(false);

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoCommandBeforeDeathEnabled()) {
            if (event.getHealth() < getHealth() && event.getHealth() <= FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeDeath() && !isSentLowHealth()) {
                for (String command : FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandBeforeDeath()) {
                    FishingBot.getInstance().getCurrentBot().runCommand(command, true, new ConsoleCommandExecutor());
                }
                setSentLowHealth(true);
            } else if (isSentLowHealth() && event.getHealth() > FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeDeath())
                setSentLowHealth(false);
        }

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoQuitBeforeDeathEnabled() && event.getHealth() < getHealth()
                && event.getHealth() <= FishingBot.getInstance().getCurrentBot().getConfig().getMinHealthBeforeQuit() && event.getHealth() != 0.0) {
            FishingBot.getI18n().warning("module-fishing-health-threshold-reached");
            FishingBot.getInstance().getCurrentBot().setPreventReconnect(true);
            FishingBot.getInstance().getCurrentBot().setRunning(false);
        }

        this.health = event.getHealth();
    }

    @EventHandler
    public void onRespawn(RespawnEvent event) {
        new Thread(() -> {
            try {
                Thread.sleep(FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandOnRespawnDelay());
            } catch (InterruptedException ignore) { }
            if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoCommandOnRespawnEnabled()) {
                for (String command : FishingBot.getInstance().getCurrentBot().getConfig().getAutoCommandOnRespawn()) {
                    FishingBot.getInstance().getCurrentBot().runCommand(command, true, new ConsoleCommandExecutor());
                }
            }
        }).start();
    }

    @EventHandler
    public void onPingUpdate(PingChangeEvent event) {
        setLastPing(event.getPing());
    }

    @EventHandler
    public void onCommandsRegistered(CommandsRegisteredEvent event) {
        setMcCommandDispatcher(event.getCommandDispatcher());
    }

    public void sneak(boolean sneaking) {
        int protocolId = FishingBot.getInstance().getCurrentBot().getServerProtocol();
        if (protocolId < ProtocolConstants.MC_1_21_6) {
            if (sneaking)
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutEntityAction(EntityAction.START_SNEAKING));
            else
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutEntityAction(EntityAction.STOP_SNEAKING));
        } else {
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutPlayerInput(
                    new PacketOutPlayerInput.Input(false, false, false, false, false, sneaking, false)
            ));
        }
    }

    public void respawn() {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutClientStatus(PacketOutClientStatus.Action.PERFORM_RESPAWN));
        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() >= ProtocolConstants.MC_1_21_4)
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutPlayerLoaded());

        if (FishingBot.getInstance().getCurrentBot().getConfig().isAutoSneak()) {
            FishingBot.getScheduler().schedule(() -> sneak(true), 250, TimeUnit.MILLISECONDS);
        }
    }

    public void sendMessage(String message, CommandExecutor commandExecutor) {
        message = message.replace("%prefix%", FishingBot.PREFIX);
        for (String line : message.split("\n")) {
            if (FishingBot.getInstance().getCurrentBot().getServerProtocol() == ProtocolConstants.MC_1_8) {
                for (String split : StringUtils.splitDescription(line)) {
                    FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatMessage(split));
                }
            } else if (FishingBot.getInstance().getCurrentBot().getServerProtocol() < ProtocolConstants.MC_1_19) {
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatMessage(line));
            } else {
                if (line.startsWith("/"))
                    executeChatCommand(line.substring(1), commandExecutor);
                else
                    FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatMessage(line));
            }
        }
    }

    private void executeChatCommand(String command, CommandExecutor commandExecutor) {
        if (mcCommandDispatcher == null) {
            if (FishingBot.getInstance().getCurrentBot().getServerProtocol() >= ProtocolConstants.MC_1_20_5)
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutUnsignedChatCommand(command));
            else
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatCommand(command));
            return;
        }

        CommandContextBuilder<CommandExecutor> context = mcCommandDispatcher.parse(command, commandExecutor).getContext();
        Map<String, Pair<ArgumentType<?>, ParsedArgument<CommandExecutor, ?>>> arguments = CommandUtils.getArguments(context);
        boolean containsSignableArguments = arguments.values().stream().anyMatch(argument -> argument.getKey() instanceof MessageArgumentType);
        if (!containsSignableArguments) {
            if (FishingBot.getInstance().getCurrentBot().getServerProtocol() >= ProtocolConstants.MC_1_20_5)
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutUnsignedChatCommand(command));
            else
                FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatCommand(command));
            return;
        }
        List<CryptManager.SignableArgument> signableArguments = arguments.entrySet().stream()
                .filter(entry -> entry.getValue().getKey() instanceof MessageArgumentType)
                .map(entry -> new CryptManager.SignableArgument(entry.getKey(), entry.getValue().getValue().getResult().toString()))
                .collect(Collectors.toList());
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutChatCommand(command, signableArguments));
    }

    public void dropStack(short slot, short actionNumber) {
        Map<Short, Slot> remainingSlots = new HashMap<>();
        remainingSlots.put(slot, Slot.EMPTY);
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ 0,
                        slot,
                        /* drop entire stack */ (byte) 1,
                        /* action count starting at 1 */ actionNumber,
                        /* drop entire stack */ 4,
                        /* empty slot */ Slot.EMPTY,
                        remainingSlots
                )
        );

        FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().setItem(slot, Slot.EMPTY);
    }

    public void swapToHotBar(int slotId, int hotBarButton) {
        // This is not notchian behaviour, but it works
        Map<Short, Slot> remainingSlots = new HashMap<>();
        remainingSlots.put((short) slotId, Slot.EMPTY);
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ 0,
                        /* the clicked slot */ (short) slotId,
                        /* use hotBar Button */ (byte) hotBarButton,
                        /* action count starting at 1 */ (short) 1,
                        /* hotBar button mode */ 2,
                        /* slot */ getInventory().getContent().get(slotId),
                        remainingSlots
                )
        );
        try { Thread.sleep(20); } catch (InterruptedException ignore) { }
        closeInventory();

        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() <= ProtocolConstants.MC_1_17) {
            Slot slot = FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().get(slotId);
            FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().put(slotId, FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().get(hotBarButton + 36));
            FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().put(hotBarButton + 36, slot);
        }
    }

    public void shiftToInventory(int slotId, Inventory inventory) {
        // This is not notchian behaviour, but it works
        Map<Short, Slot> remainingSlots = new HashMap<>();
        remainingSlots.put((short) slotId, Slot.EMPTY);
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(
                new PacketOutClickWindow(
                        /* player inventory */ inventory.getWindowId(),
                        /* the clicked slot */ (short) (slotId + (inventory.getContent().size() - 45)),
                        /* use right click */ (byte) 0,
                        /* action count starting at 1 */ inventory.getActionCounter(),
                        /* shift click mode */ 1,
                        /* slot */ getInventory().getContent().get(slotId),
                        remainingSlots
                )
        );
        try { Thread.sleep(20); } catch (InterruptedException ignore) { }

        FishingBot.getInstance().getCurrentBot().getPlayer().getInventory().getContent().put(slotId, Slot.EMPTY);
    }

    public boolean look(LocationUtils.Direction direction, Consumer<Boolean> onFinish) {
        float yaw = direction.getYaw() == Float.MIN_VALUE ? getYaw() : direction.getYaw();
        float pitch = direction.getPitch() == Float.MIN_VALUE ? getPitch() : direction.getPitch();
        return look(yaw, pitch, FishingBot.getInstance().getCurrentBot().getConfig().getLookSpeed(), onFinish);
    }

    public boolean look(float yaw, float pitch, int speed) {
        return look(yaw, pitch, speed, null);
    }

    public boolean look(float yaw, float pitch, int speed, Consumer<Boolean> onFinish) {
        if (lookThread != null && Thread.currentThread() != lookThread && lookThread.isAlive()) {
            return false;
        } else if (lookThread != null && Thread.currentThread() == lookThread && lookThread.isAlive()) {
            internalLook(yaw, pitch, speed, onFinish); // calling look inside onFinish
            return true;
        }

        this.lookThread = new Thread(() -> {
            internalLook(yaw, pitch, speed, onFinish);
        });
        getLookThread().start();
        return true;
    }

    private void internalLook(float yaw, float pitch, int speed, Consumer<Boolean> onFinish) {
        float yawDiff = LocationUtils.yawDiff(getYaw(), yaw);
        float pitchDiff = LocationUtils.yawDiff(getPitch(), pitch);

        int steps = (int) Math.ceil(Math.max(Math.abs(yawDiff), Math.abs(pitchDiff)) / Math.max(1, speed));
        float yawPerStep = yawDiff / steps;
        float pitchPerStep = pitchDiff / steps;

        for (int i = 0; i < steps; i++) {
            setYaw(getYaw() + yawPerStep);
            setPitch(getPitch() + pitchPerStep);
            if (getYaw() > 180)
                setYaw(-180 + (getYaw() - 180));
            if (getYaw() < -180)
                setYaw(180 + (getYaw() + 180));
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutPosLook(getX(), getY(), getZ(), getYaw(), getPitch(), true, true));
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignore) {
                return;
            }
        }
        if (onFinish != null && FishingBot.getInstance().getCurrentBot().getNet().getState() == ProtocolState.PLAY)
            onFinish.accept(true);

        try {
            Thread.sleep(50);
        } catch (InterruptedException ignore) { }
    }

    public boolean isCurrentlyLooking() {
        return !(lookThread == null || lookThread.isInterrupted() || !lookThread.isAlive());
    }

    public void openAdjacentChest(LocationUtils.Direction direction) {
        int x = (int)Math.floor(getX());
        int y = (int)Math.round(getY());
        int z = (int)Math.floor(getZ());
        PacketOutBlockPlace.BlockFace blockFace;
        switch (direction) {
            case EAST: x++; blockFace = PacketOutBlockPlace.BlockFace.WEST; break;
            case WEST: x--; blockFace = PacketOutBlockPlace.BlockFace.EAST; break;
            case NORTH: z--; blockFace = PacketOutBlockPlace.BlockFace.SOUTH; break;
            case DOWN: y--; blockFace = PacketOutBlockPlace.BlockFace.TOP; break;
            default: z++; blockFace = PacketOutBlockPlace.BlockFace.NORTH; break;
        }
        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() == ProtocolConstants.MC_1_8) {
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutUseItem(
                    x, y, z, (byte)0, (byte)0, (byte)0, 0F, 0F, blockFace
            ));
        } else {
            boolean autoSneak = FishingBot.getInstance().getCurrentBot().getConfig().isAutoSneak();
            if (autoSneak)
                sneak(false);
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutBlockPlace(
                    PacketOutBlockPlace.Hand.MAIN_HAND,
                    x, y, z, blockFace,
                    0.5F, 0.5F, 0.5F,
                    false,
                    false
            ));
            if (autoSneak)
                sneak(true);
        }
    }

    public void closeInventory() {
        closeInventory(0);
    }

    public void closeInventory(int windowId) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutCloseInventory(windowId));
    }

    public void setHeldSlot(int heldSlot) {
        setHeldSlot(heldSlot, true);
    }

    public void setHeldSlot(int heldSlot, boolean sendPacket) {
        if (sendPacket)
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutHeldItemChange(heldSlot));
        this.heldSlot = heldSlot + 36;
    }

    public void use() {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutUseItem(this));
    }

    public int incrementChatSessionIndex() {
        return this.chatSessionIndex++;
    }
}
