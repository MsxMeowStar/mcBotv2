/*
 * Created by David Luedtke (MrKinau)
 * 2019/5/3
 */

package systems.kinau.fishingbot.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.UUID;

@RequiredArgsConstructor
@ToString
public class AuthData {

    @Getter private final String accessToken;
    @Getter private final String uuid;
    @Getter private final String username;
    @Getter @Setter private ProfileKeys profileKeys;

    @RequiredArgsConstructor
    @Getter
    public static class ProfileKeys {
        private final PublicKey publicKey;
        private final String publicKeySignature;
        private final PrivateKey privateKey;
        private final long expiresAt;
        private final UUID chatSessionId = UUID.randomUUID();
    }
}
