/*
 * Created by David Luedtke (MrKinau)
 * 2019/5/3
 */

package systems.kinau.fishingbot;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.cli.CommandLine;
import systems.kinau.fishingbot.auth.AuthData;
import systems.kinau.fishingbot.auth.Authenticator;
import systems.kinau.fishingbot.bot.Player;
import systems.kinau.fishingbot.event.EventManager;
import systems.kinau.fishingbot.gui.Dialogs;
import systems.kinau.fishingbot.i18n.I18n;
import systems.kinau.fishingbot.io.config.SettingsConfig;
import systems.kinau.fishingbot.io.logging.LogFormatter;
import systems.kinau.fishingbot.modules.ChatProxyModule;
import systems.kinau.fishingbot.modules.ClientDefaultsModule;
import systems.kinau.fishingbot.modules.HandshakeModule;
import systems.kinau.fishingbot.modules.LoginModule;
import systems.kinau.fishingbot.modules.ModuleManager;
import systems.kinau.fishingbot.modules.command.ChatCommandModule;
import systems.kinau.fishingbot.modules.command.CommandRegistry;
import systems.kinau.fishingbot.modules.command.executor.CommandExecutor;
import systems.kinau.fishingbot.modules.discord.DiscordModule;
import systems.kinau.fishingbot.modules.ejection.EjectionModule;
import systems.kinau.fishingbot.modules.timer.TimerModule;
import systems.kinau.fishingbot.network.mojangapi.MojangAPI;
import systems.kinau.fishingbot.network.mojangapi.Realm;
import systems.kinau.fishingbot.network.ping.ServerPinger;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.utils.MinecraftTranslations;
import systems.kinau.fishingbot.utils.UUIDUtils;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;

public class Bot {

    @Getter @Setter private boolean running;
    @Getter @Setter private boolean preventStartup;
    @Getter @Setter private boolean preventReconnect;
    @Getter         private SettingsConfig config;
    @Getter @Setter private int serverProtocol = ProtocolConstants.MC_1_8; //default 1.8
    @Getter @Setter private String serverHost;
    @Getter @Setter private int serverPort;
    @Getter @Setter private AuthData authData;
    @Getter @Setter private boolean wontConnect = false;
    @Getter         private ExecutorService commandsThread;
    @Getter         private boolean noGui;

    @Getter         private EventManager eventManager;
    @Getter         private CommandRegistry commandRegistry;
    @Getter         private ModuleManager moduleManager;

    @Getter         private Player player;

    @Getter         private Socket socket;
    @Getter         private NetworkHandler net;

    @Getter         private MinecraftTranslations minecraftTranslations;

    @Getter         private File logsFolder = new File(FishingBot.getExecutionDirectory(), "logs");

    public Bot(CommandLine cmdLine) {
        FishingBot.getInstance().setCurrentBot(this);
        this.eventManager = new EventManager();
        this.moduleManager = new ModuleManager();
        this.noGui = cmdLine.hasOption("nogui");

        if (!cmdLine.hasOption("nogui"))
            getEventManager().registerListener(FishingBot.getInstance().getMainGUIController());

        // read config

        if (cmdLine.hasOption("config"))
            this.config = new SettingsConfig(cmdLine.getOptionValue("config"));
        else
            this.config = new SettingsConfig(new File(FishingBot.getExecutionDirectory(), "config.json").getAbsolutePath());

        // update i18n

        FishingBot.setI18n(new I18n(config.getLanguage(), FishingBot.PREFIX, true));

        // use command line arguments
        if (cmdLine.hasOption("logsdir")) {
            this.logsFolder = new File(cmdLine.getOptionValue("logsdir"));
            if (!logsFolder.exists()) {
                boolean success = logsFolder.mkdirs();
                if (!success) {
                    FishingBot.getI18n().severe("log-failed-creating-folder");
                    FishingBot.getInstance().getCurrentBot().setRunning(false);
                    FishingBot.getInstance().getCurrentBot().setWontConnect(true);
                    FishingBot.getInstance().getCurrentBot().setPreventStartup(true);
                    return;
                }
            }
        }

        // set logger file handler
        try {
            FileHandler fh;
            if(!logsFolder.exists() && !logsFolder.mkdir() && logsFolder.isDirectory())
                throw new IOException(FishingBot.getI18n().t("log-failed-creating-folder"));
            FishingBot.getLog().removeHandler(Arrays.stream(FishingBot.getLog().getHandlers()).filter(handler -> handler instanceof FileHandler).findAny().orElse(null));
            FishingBot.getLog().addHandler(fh = new FileHandler(logsFolder.getPath() + "/log%g.log", 0 /* 0 = infinity */, Math.max(1, getConfig().getLogCount())));
            fh.setFormatter(new LogFormatter());
            fh.setEncoding("UTF-8");
        } catch (IOException e) {
            FishingBot.getI18n().severe("log-failed-creating-log");
            FishingBot.getInstance().getCurrentBot().setRunning(false);
            FishingBot.getInstance().getCurrentBot().setWontConnect(true);
            return;
        }

        // start message
        FishingBot.getLog().info("Using " + FishingBot.TITLE);

        // log config location
        FishingBot.getI18n().info("config-loaded-from", new File(getConfig().getPath()).getAbsolutePath());

        // init MinecraftTranslations
        this.minecraftTranslations = new MinecraftTranslations();

        // authenticate player if online-mode is set
        if (getConfig().isOnlineMode()) {
            boolean authSuccessful = authenticate();

            if (!authSuccessful) {
                if (!isPreventStartup()) {
                    FishingBot.getI18n().severe("credentials-invalid");
                    if (!cmdLine.hasOption("nogui")) {
                        Dialogs.showCredentialsInvalid();
                    }
                }
                setPreventStartup(true);
                return;
            }
        } else {
            FishingBot.getI18n().info("credentials-using-offline-mode", getConfig().getUserName());
            this.authData = new AuthData(null, UUIDUtils.createOfflineUUIDString(getConfig().getUserName()), getConfig().getUserName());
        }

        if (!cmdLine.hasOption("nogui")) {
            FishingBot.getInstance().getMainGUIController().setImage(authData.getUuid());
            FishingBot.getInstance().getMainGUIController().setAccountName(authData.getUsername());
        }

        FishingBot.getI18n().info("auth-username", authData.getUsername());

        String ip = getConfig().getServerIP();
        int port = getConfig().getServerPort();

        int assumedProtocolIdForMJAPI = ProtocolConstants.getProtocolId(getConfig().getDefaultProtocol());
        if (assumedProtocolIdForMJAPI == ProtocolConstants.AUTOMATIC)
            assumedProtocolIdForMJAPI = ProtocolConstants.getLatest();
        MojangAPI mojangAPI = null;
        if (getConfig().isOnlineMode())
            mojangAPI = new MojangAPI(getAuthData(), assumedProtocolIdForMJAPI);

        // Check rather to connect to realm
        if (getConfig().getRealmId() != -1 && mojangAPI != null) {
            if (getConfig().getRealmId() == 0) {
                List<Realm> possibleRealms = mojangAPI.getPossibleWorlds();
                mojangAPI.printRealms(possibleRealms);
                FishingBot.getI18n().info("realms-id-not-set");
                if (!cmdLine.hasOption("nogui")) {
                    AtomicBoolean dialogClicked = new AtomicBoolean(false);
                    Dialogs.showRealmsWorlds(possibleRealms, realm -> {
                        if (realm != null) {
                            FishingBot.getInstance().getConfig().setRealmId(realm.getId());
                            FishingBot.getInstance().getConfig().save();
                            getConfig().setRealmId(realm.getId());
                            getConfig().save();
                        }
                        dialogClicked.set(true);
                    });

                    // Wait in this thread until the dialog is answered
                    while (!dialogClicked.get()) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (getConfig().getRealmId() == 0) {
                    setPreventStartup(true);
                    return;
                }
            }
            if (getConfig().isRealmAcceptTos())
                mojangAPI.agreeTos();
            else {
                if (!cmdLine.hasOption("nogui")) {
                    AtomicBoolean dialogClicked = new AtomicBoolean(false);
                    Dialogs.showRealmsAcceptToS(clickedYes -> {
                        if (clickedYes) {
                            FishingBot.getInstance().getConfig().setRealmAcceptTos(true);
                            FishingBot.getInstance().getConfig().save();
                            getConfig().setRealmAcceptTos(true);
                            getConfig().save();
                        }
                        dialogClicked.set(true);
                    });

                    // Wait in this thread until the dialog is answered
                    while (!dialogClicked.get()) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (!getConfig().isRealmAcceptTos()) {
                    FishingBot.getI18n().severe("realms-tos-agreement");
                    setPreventStartup(true);
                    return;
                }
            }

            String ipAndPort = null;
            for (int i = 0; i < 5; i++) {
                ipAndPort = mojangAPI.getServerIP(getConfig().getRealmId());
                if (ipAndPort == null) {
                    FishingBot.getI18n().info("realms-determining-address", String.valueOf(i + 1));
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignore) { }
                } else
                    break;
            }
            if (ipAndPort == null) {
                setWontConnect(true);
                setRunning(false);
                setPreventReconnect(true);
                return;
            }
            ip = ipAndPort.split(":")[0];
            port = Integer.parseInt(ipAndPort.split(":")[1]);
        }

        // Ping server
        FishingBot.getI18n().info("server-pinging", ip, String.valueOf(port), getConfig().getDefaultProtocol());
        ServerPinger sp = new ServerPinger(ip, port);
        sp.ping();

        // Obtain keys for chat signing
        if (mojangAPI != null) {
            mojangAPI.obtainCertificates();
        }
    }

    public EjectionModule getEjectModule() {
        return (EjectionModule) getModuleManager().getLoadedModule(EjectionModule.class).orElse(null);
    }

    public DiscordModule getDiscordModule() {
        return (DiscordModule) getModuleManager().getLoadedModule(DiscordModule.class).orElse(null);
    }

    public void start(CommandLine cmdLine) {
        if (isRunning() || isPreventStartup()) {
            FishingBot.getInstance().setCurrentBot(null);
            if (cmdLine.hasOption("nogui"))
                return;
            FishingBot.getInstance().getMainGUIController().updateStartStop();
            FishingBot.getInstance().getMainGUIController().enableStartStop();
            return;
        }
        connect();
    }

    public void runCommand(String command, boolean executeBotCommand, CommandExecutor commandExecutor) {
        commandsThread.execute(() -> {
            if (getNet() == null)
                return;
            if (executeBotCommand && command.startsWith("/")) {
                boolean executed = FishingBot.getInstance().getCurrentBot().getCommandRegistry().dispatchCommand(command, commandExecutor);
                if (executed)
                    return;
            }

            getPlayer().sendMessage(command, commandExecutor);
        });
    }

    private boolean authenticate() {
        Authenticator authenticator = new Authenticator();
        Optional<AuthData> authData = authenticator.authenticate();

        if (!authData.isPresent()) {
            setAuthData(new AuthData(null, UUIDUtils.createOfflineUUIDString(getConfig().getUserName()), getConfig().getUserName()));
            return false;
        }

        setAuthData(authData.get());

        return true;
    }

    private void registerCommands() {
        this.commandRegistry = new CommandRegistry();
        commandRegistry.registerBotCommands();
    }

    private void connect() {
        String serverName = getServerHost();
        int port = getServerPort();

        do {
            try {
                setRunning(true);
                if (isWontConnect()) {
                    setWontConnect(false);
                    ServerPinger sp = new ServerPinger(getServerHost(), getServerPort());
                    sp.ping();
                    if (isWontConnect()) {
                        if (!getConfig().isAutoReconnect())
                            return;
                        try {
                            Thread.sleep(getConfig().getAutoReconnectTime() * 1000);
                        } catch (InterruptedException ignore) { }
                        continue;
                    }
                }
                this.socket = new Socket(serverName, port);

                this.net = new NetworkHandler();
                this.commandsThread = Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder().setNameFormat("command-executor-thread-%d").build());

                registerCommands();
                if (FishingBot.getInstance().getMainGUIController() != null)
                    getEventManager().registerListener(FishingBot.getInstance().getMainGUIController());

                if (FishingBot.getInstance().getMainGUIController() != null && !getEventManager().isRegistered(FishingBot.getInstance().getMainGUIController()))
                    getEventManager().registerListener(FishingBot.getInstance().getMainGUIController());

                // enable required modules

                getModuleManager().enableModule(new HandshakeModule(serverName, port));
                getModuleManager().enableModule(new LoginModule(getAuthData().getUsername()));
                getModuleManager().enableModule(new ClientDefaultsModule());
                getModuleManager().enableModule(new ChatProxyModule());

                if (getConfig().isStartTextEnabled())
                    getModuleManager().enableModule(new ChatCommandModule());

                if (getConfig().isWebHookEnabled())
                    getModuleManager().enableModule(new DiscordModule());

                if (getConfig().isAutoLootEjectionEnabled())
                    getModuleManager().enableModule(new EjectionModule());

                if (getConfig().isTimerEnabled())
                    getModuleManager().enableModule(new TimerModule());

                // init player

                this.player = new Player();

                // add shutdown hook

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (socket != null && !socket.isClosed())
                            socket.close();
                        if (commandsThread != null && !commandsThread.isTerminated())
                            commandsThread.shutdownNow();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));

                // game loop (for receiving packets)

                while (running) {
                    try {
                        net.readData();
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        FishingBot.getI18n().severe("packet-could-not-be-received");
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                FishingBot.getI18n().severe("bot-could-not-be-started", e.getMessage());
            } finally {
                try {
                    if (socket != null)
                        this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (getPlayer() != null)
                    getEventManager().unregisterListener(getPlayer());
                if (commandsThread != null && !commandsThread.isShutdown())
                    commandsThread.shutdownNow();
                getEventManager().getRegisteredListener().clear();
                getEventManager().getClassToInstanceMapping().clear();
                getModuleManager().disableAll();
                this.socket = null;
                this.net = null;
                this.player = null;
            }
            if (getConfig().isAutoReconnect() && !isPreventReconnect()) {
                FishingBot.getI18n().info("bot-automatic-reconnect", String.valueOf(getConfig().getAutoReconnectTime()));

                try {
                    Thread.sleep(getConfig().getAutoReconnectTime() * 1000);
                } catch (InterruptedException ignore) { }

                if (getAuthData() == null) {
                    if (getConfig().isOnlineMode())
                        authenticate();
                    else {
                        FishingBot.getI18n().info("credentials-using-offline-mode", getConfig().getUserName());
                        authData = new AuthData(null, UUIDUtils.createOfflineUUIDString(getConfig().getUserName()), getConfig().getUserName());
                    }
                }
            }
        } while (getConfig().isAutoReconnect() && !isPreventReconnect());
        FishingBot.getInstance().setCurrentBot(null);
        if (FishingBot.getInstance().getMainGUIController() != null) {
            FishingBot.getInstance().getMainGUIController().updateStartStop();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) { }
            FishingBot.getInstance().getMainGUIController().enableStartStop();
        }
    }
}
