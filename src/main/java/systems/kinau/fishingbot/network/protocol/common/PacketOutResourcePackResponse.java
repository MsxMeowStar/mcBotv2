package systems.kinau.fishingbot.network.protocol.common;

import com.google.common.io.ByteArrayDataOutput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.utils.ByteArrayDataInputWrapper;

import java.io.IOException;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PacketOutResourcePackResponse extends Packet {

    private UUID uuid;
    private Result result;

    @Override
    public void write(ByteArrayDataOutput out, int protocolId) throws IOException {
        if (protocolId >= ProtocolConstants.MC_1_20_3)
            writeUUID(uuid, out);
        writeVarInt(result.ordinal(), out);
    }

    @Override
    public void read(ByteArrayDataInputWrapper in, NetworkHandler networkHandler, int length, int protocolId) throws IOException {
        // Only outgoing packet
    }

    public enum Result {
        SUCCESSFULLY_LOADED,
        DECLINED,
        FAILED_DOWNLOAD,
        ACCEPTED,
        DOWNLOADED,
        INVALID_URL,
        FAILED_RELOAD,
        DISCARDED
    }
}
