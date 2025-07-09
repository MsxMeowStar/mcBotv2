/*
 * Created by David Luedtke (MrKinau)
 * 2019/10/18
 */

package systems.kinau.fishingbot.modules;

import lombok.Getter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.EventHandler;
import systems.kinau.fishingbot.event.Listener;
import systems.kinau.fishingbot.event.configuration.ConfigurationFinishEvent;
import systems.kinau.fishingbot.event.configuration.ConfigurationStartEvent;
import systems.kinau.fishingbot.event.configuration.KnownPacksRequestedEvent;
import systems.kinau.fishingbot.event.login.*;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.protocol.ProtocolState;
import systems.kinau.fishingbot.network.protocol.configuration.PacketOutFinishConfiguration;
import systems.kinau.fishingbot.network.protocol.configuration.PacketOutKnownPacks;
import systems.kinau.fishingbot.network.protocol.configuration.PacketOutPluginMessage;
import systems.kinau.fishingbot.network.protocol.login.PacketOutEncryptionResponse;
import systems.kinau.fishingbot.network.protocol.login.PacketOutLoginAcknowledge;
import systems.kinau.fishingbot.network.protocol.login.PacketOutLoginPluginResponse;
import systems.kinau.fishingbot.network.protocol.login.PacketOutLoginStart;
import systems.kinau.fishingbot.network.protocol.play.PacketOutAcknowledgeConfiguration;
import systems.kinau.fishingbot.network.utils.CryptManager;
import systems.kinau.fishingbot.utils.UUIDUtils;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLEncoder;

public class LoginModule extends Module implements Listener {

    @Getter private String userName;

    public LoginModule(String userName) {
        this.userName = userName;
        FishingBot.getInstance().getCurrentBot().getEventManager().registerListener(this);
    }

    @Override
    public void onEnable() {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutLoginStart(getUserName()));
    }

    @Override
    public void onDisable() {
        FishingBot.getInstance().getCurrentBot().getEventManager().unregisterListener(this);
    }

    @EventHandler
    public void onEncryptionRequest(EncryptionRequestEvent event) {
        NetworkHandler networkHandler = FishingBot.getInstance().getCurrentBot().getNet();

        // Set public key
        networkHandler.setPublicKey(event.getPublicKey());

        // Generate & Set secret key
        SecretKey secretKey = CryptManager.createNewSharedKey();
        networkHandler.setSecretKey(secretKey);

        byte[] serverIdHash = CryptManager.getServerIdHash(event.getServerId().trim(), event.getPublicKey(), secretKey);
        if(serverIdHash == null) {
            FishingBot.getI18n().severe("module-login-hash-error");
            FishingBot.getInstance().getCurrentBot().setRunning(false);
            return;
        }

        String var5 = new BigInteger(serverIdHash).toString(16);
        sendSessionRequest(FishingBot.getInstance().getCurrentBot().getAuthData().getUsername(),
                "token:" + FishingBot.getInstance().getCurrentBot().getAuthData().getAccessToken() + ":" + UUIDUtils.withoutDashes(FishingBot.getInstance().getCurrentBot().getAuthData().getUuid()), var5);

        networkHandler.sendPacket(new PacketOutEncryptionResponse(event.getServerId(), event.getPublicKey(), event.getVerifyToken(), secretKey));
        networkHandler.activateEncryption();
        networkHandler.decryptInputStream();
    }

    @EventHandler
    public void onLoginDisconnect(LoginDisconnectEvent event) {
        FishingBot.getI18n().severe("module-login-failed", event.getErrorMessage());
        FishingBot.getInstance().getCurrentBot().setRunning(false);
        FishingBot.getInstance().getCurrentBot().setAuthData(null);
    }

    @EventHandler
    public void onSetCompression(SetCompressionEvent event) {
        FishingBot.getInstance().getCurrentBot().getNet().setThreshold(event.getThreshold());
    }

    @EventHandler
    public void onLoginPluginRequest(LoginPluginRequestEvent event) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutLoginPluginResponse(event.getMsgId(), false, null));
    }

    @EventHandler
    public void onLoginSuccess(LoginSuccessEvent event) {
        FishingBot.getI18n().info("module-login-successful", event.getUserName(), event.getUuid().toString());
        if (FishingBot.getInstance().getCurrentBot().getServerProtocol() < ProtocolConstants.MC_1_20_2) {
            FishingBot.getInstance().getCurrentBot().getNet().setState(ProtocolState.PLAY);
        } else {
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutLoginAcknowledge());
            FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new ConfigurationStartEvent());
        }
        FishingBot.getInstance().getCurrentBot().getPlayer().setUuid(event.getUuid());
    }

    @EventHandler
    public void onConfigurationStart(ConfigurationStartEvent event) {
        if (FishingBot.getInstance().getCurrentBot() != null && FishingBot.getInstance().getCurrentBot().getNet() != null
                && FishingBot.getInstance().getCurrentBot().getNet().getState() == ProtocolState.PLAY)
            FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutAcknowledgeConfiguration());
        FishingBot.getInstance().getCurrentBot().getNet().setState(ProtocolState.CONFIGURATION);
    }

    @EventHandler
    public void onConfigurationFinish(ConfigurationFinishEvent event) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutPluginMessage("minecraft:brand", (out, protocol) -> Packet.writeString("fishingbot", out)));
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutFinishConfiguration());
        FishingBot.getInstance().getCurrentBot().getNet().setState(ProtocolState.PLAY);
    }

    @EventHandler
    public void onKnownPacksRequested(KnownPacksRequestedEvent event) {
        FishingBot.getInstance().getCurrentBot().getNet().sendPacket(new PacketOutKnownPacks(event.getKnownPacks()));
    }

    private String sendSessionRequest(String user, String session, String serverid) {
        try {
            return sendGetRequest("http://session.minecraft.net/game/joinserver.jsp"
                    + "?user=" + urlEncode(user)
                    + "&sessionId=" + urlEncode(session)
                    + "&serverId=" + urlEncode(serverid));
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private String sendGetRequest(String url) {
        try {
            URL var4 = new URL(url);
            BufferedReader var5 = new BufferedReader(new InputStreamReader(var4.openStream()));
            String var6 = var5.readLine();
            var5.close();
            return var6;
        } catch (IOException var7) {
            return var7.toString();
        }
    }

    private String urlEncode(String par0Str) throws IOException {
        return URLEncoder.encode(par0Str, "UTF-8");
    }
}
