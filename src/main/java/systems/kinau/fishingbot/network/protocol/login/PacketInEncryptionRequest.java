/*
 * Created by David Luedtke (MrKinau)
 * 2019/5/5
 */

package systems.kinau.fishingbot.network.protocol.login;

import com.google.common.io.ByteArrayDataOutput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.login.EncryptionRequestEvent;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.utils.ByteArrayDataInputWrapper;
import systems.kinau.fishingbot.network.utils.CryptManager;

import java.io.IOException;
import java.security.PublicKey;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PacketInEncryptionRequest extends Packet {

    private String serverId;
    private PublicKey publicKey;
    private byte[] verifyToken;
    private boolean shouldAuthenticate;

    @Override
    public void write(ByteArrayDataOutput out, int protocolId) throws IOException { }

    @Override
    public void read(ByteArrayDataInputWrapper in, NetworkHandler networkHandler, int length, int protocolId) throws IOException {
        this.serverId = readString(in);
        this.publicKey = CryptManager.decodePublicKey(readBytesFromStream(in));
        this.verifyToken = readBytesFromStream(in);

        if (protocolId >= ProtocolConstants.MC_1_20_5)
            this.shouldAuthenticate = in.readBoolean();

        FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new EncryptionRequestEvent(serverId, publicKey, verifyToken, shouldAuthenticate));
    }
}
