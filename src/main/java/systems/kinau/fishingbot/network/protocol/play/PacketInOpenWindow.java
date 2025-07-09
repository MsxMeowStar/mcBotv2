package systems.kinau.fishingbot.network.protocol.play;

import com.google.common.io.ByteArrayDataOutput;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.play.OpenWindowEvent;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.utils.ByteArrayDataInputWrapper;

import java.io.IOException;

@Getter
@NoArgsConstructor
@ToString
public class PacketInOpenWindow extends Packet {

    private int windowId;
    private int windowType;
    private String title;

    @Override
    public void write(ByteArrayDataOutput out, int protocolId) throws IOException {
        // Only incoming packet
    }

    @Override
    public void read(ByteArrayDataInputWrapper in, NetworkHandler networkHandler, int length, int protocolId) throws IOException {
        if (protocolId <= ProtocolConstants.MC_1_13_2) {
            this.windowId = in.readUnsignedByte();
            this.windowType = readString(in).hashCode();
            this.title = readChatComponent(in, protocolId);
            in.readUnsignedByte(); // slots
        } else {
            this.windowId = readContainerIdVarInt(in, protocolId);
            this.windowType = readVarInt(in);
            this.title = readChatComponent(in, protocolId);
        }

        FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new OpenWindowEvent(windowId, windowType, title));
    }
}
