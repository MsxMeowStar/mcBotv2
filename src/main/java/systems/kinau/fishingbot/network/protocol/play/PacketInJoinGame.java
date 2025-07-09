/*
 * Created by David Luedtke (MrKinau)
 * 2019/5/5
 */

package systems.kinau.fishingbot.network.protocol.play;

import com.google.common.io.ByteArrayDataOutput;
import lombok.Getter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.play.JoinGameEvent;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.protocol.ProtocolConstants;
import systems.kinau.fishingbot.network.utils.ByteArrayDataInputWrapper;

@Getter
public class PacketInJoinGame extends Packet {

    private int eid;
    private int gamemode;
    private String[] worldIdentifier;
    private String dimension;
    private String spawnWorld;
    private long hashedSeed;
    private int difficulty;
    private int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private String levelType;
    private boolean reducedDebugInfo;
    private boolean enableRespawnScreen;
    private boolean debug;
    private boolean flat;
    private boolean hardcore;
    private int portalCooldown;
    private int seaLevel;

    @Override
    public void write(ByteArrayDataOutput out, int protocolId) {
        // only incoming packet
    }

    @Override
    public void read(ByteArrayDataInputWrapper in, NetworkHandler networkHandler, int length, int protocolId) {
        switch (protocolId) {
            case ProtocolConstants.MC_1_9:
            case ProtocolConstants.MC_1_8: {
                eid = in.readInt();                         // entity ID
                gamemode = in.readUnsignedByte();           // gamemode
                dimension = String.valueOf(in.readByte());  // dimension
                difficulty = in.readUnsignedByte();         // difficulty
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                levelType = readString(in);                 // level type
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                break;
            }
            case ProtocolConstants.MC_1_13_2:
            case ProtocolConstants.MC_1_13_1:
            case ProtocolConstants.MC_1_13:
            case ProtocolConstants.MC_1_12_2:
            case ProtocolConstants.MC_1_12_1:
            case ProtocolConstants.MC_1_12:
            case ProtocolConstants.MC_1_11_1:
            case ProtocolConstants.MC_1_11:
            case ProtocolConstants.MC_1_10:
            case ProtocolConstants.MC_1_9_4:
            case ProtocolConstants.MC_1_9_2:
            case ProtocolConstants.MC_1_9_1: {
                eid = in.readInt();                         // entity ID
                gamemode = in.readUnsignedByte();           // gamemode
                dimension = String.valueOf(in.readInt());   // dimension
                difficulty = in.readUnsignedByte();         // difficulty
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                levelType = readString(in);                 // level type
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                break;
            }
            case ProtocolConstants.MC_1_14:
            case ProtocolConstants.MC_1_14_1:
            case ProtocolConstants.MC_1_14_2:
            case ProtocolConstants.MC_1_14_3:
            case ProtocolConstants.MC_1_14_4: {
                eid = in.readInt();                         // entity ID
                gamemode = in.readUnsignedByte();           // gamemode
                dimension = String.valueOf(in.readInt());   // dimension
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                levelType = readString(in);                 // level type
                viewDistance = readVarInt(in);              // view distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                break;
            }
            case ProtocolConstants.MC_1_15:
            case ProtocolConstants.MC_1_15_1:
            case ProtocolConstants.MC_1_15_2: {
                eid = in.readInt();                         // entity ID
                gamemode = in.readUnsignedByte();           // gamemode
                dimension = String.valueOf(in.readInt());   // dimension
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                levelType = readString(in);                 // level type
                viewDistance = readVarInt(in);              // view distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                break;
            }
            case ProtocolConstants.MC_1_16:
            case ProtocolConstants.MC_1_16_1: {
                eid = in.readInt();                         // entity ID
                gamemode = in.readUnsignedByte();           // gamemode
                in.readUnsignedByte();                      // previous gamemode
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                readNBT(in, protocolId);                    // dimension codec (dont use, just skip it)
                dimension = readString(in);                 // dimension
                spawnWorld = readString(in);                // spawn world name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                break;
            }
            case ProtocolConstants.MC_1_16_2:
            case ProtocolConstants.MC_1_16_3:
            case ProtocolConstants.MC_1_16_4:
            case ProtocolConstants.MC_1_17:
            case ProtocolConstants.MC_1_17_1: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                readNBT(in, protocolId);                    // dimension codec (dont use, just skip it)
                readNBT(in, protocolId);                    // spawn dimension
                spawnWorld = readString(in);                // spawn world name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                break;
            }
            case ProtocolConstants.MC_1_18:
            case ProtocolConstants.MC_1_18_2: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                readNBT(in, protocolId);                    // dimension codec (dont use, just skip it)
                readNBT(in, protocolId);                    // spawn dimension
                spawnWorld = readString(in);                // spawn world name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = in.readUnsignedByte();         // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                break;
            }
            case ProtocolConstants.MC_1_19:
            case ProtocolConstants.MC_1_19_1:
            case ProtocolConstants.MC_1_19_3:
            case ProtocolConstants.MC_1_19_4: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                readNBT(in, protocolId);                    // registry codec
                readString(in);                             // dimension type
                spawnWorld = readString(in);                // dimension name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = readVarInt(in);                // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                if (in.readBoolean()) {                     // has last death location
                    readString(in);                         // last death dimension
                    in.readLong();                          // last death position
                }
                break;
            }
            case ProtocolConstants.MC_1_20: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                readNBT(in, protocolId);                    // registry codec
                readString(in);                             // dimension type
                spawnWorld = readString(in);                // dimension name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                maxPlayers = readVarInt(in);                // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                if (in.readBoolean()) {                     // has last death location
                    readString(in);                         // last death dimension
                    in.readLong();                          // last death position
                }
                portalCooldown = readVarInt(in);
                break;
            }
            case ProtocolConstants.MC_1_20_2:
            case ProtocolConstants.MC_1_20_3: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                maxPlayers = readVarInt(in);                // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                in.readBoolean();                           // doLimitedCrafting
                readString(in);                             // dimension type
                spawnWorld = readString(in);                // dimension name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                if (in.readBoolean()) {                     // has last death location
                    readString(in);                         // last death dimension
                    in.readLong();                          // last death position
                }
                portalCooldown = readVarInt(in);
                break;
            }
            case ProtocolConstants.MC_1_20_5:
            case ProtocolConstants.MC_1_21: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                maxPlayers = readVarInt(in);                // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                in.readBoolean();                           // doLimitedCrafting
                readVarInt(in);                             // dimension type
                spawnWorld = readString(in);                // dimension name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                if (in.readBoolean()) {                     // has last death location
                    readString(in);                         // last death dimension
                    in.readLong();                          // last death position
                }
                portalCooldown = readVarInt(in);
                break;
            }
            case ProtocolConstants.MC_1_21_2:
            default: {
                eid = in.readInt();                         // entity ID
                hardcore = in.readBoolean();                // is hardcore
                int worldCount = readVarInt(in);            // count of worlds
                worldIdentifier = new String[worldCount];   // identifier for all worlds
                for (int i = 0; i < worldCount; i++)
                    worldIdentifier[i] = readString(in);
                maxPlayers = readVarInt(in);                // maxPlayer
                viewDistance = readVarInt(in);              // view distance
                simulationDistance = readVarInt(in);        // simulation distance
                reducedDebugInfo = in.readBoolean();        // reduced Debug info
                enableRespawnScreen = in.readBoolean();     // set to false when the doImmediateRespawn gamerule is true
                in.readBoolean();                           // doLimitedCrafting
                readVarInt(in);                             // dimension type
                spawnWorld = readString(in);                // dimension name
                hashedSeed = in.readLong();                 // first 8 bytes of the SHA-256 hash of the world's seed
                gamemode = in.readUnsignedByte();           // current gamemode
                in.readUnsignedByte();                      // previous gamemode
                debug = in.readBoolean();                   // debug world
                flat = in.readBoolean();                    // flat world
                if (in.readBoolean()) {                     // has last death location
                    readString(in);                         // last death dimension
                    in.readLong();                          // last death position
                }
                portalCooldown = readVarInt(in);
                seaLevel = readVarInt(in);
                break;
            }
        }

        FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(
                new JoinGameEvent(eid, gamemode, worldIdentifier, dimension, spawnWorld,
                        hashedSeed, difficulty, maxPlayers, viewDistance, simulationDistance, levelType,
                        reducedDebugInfo, enableRespawnScreen, debug, flat));
    }
}
