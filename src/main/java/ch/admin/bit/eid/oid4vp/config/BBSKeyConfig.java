package ch.admin.bit.eid.oid4vp.config;

import ch.admin.eid.bbs.KeyPair;
import ch.admin.eid.bbs.PublicKey;
import ch.admin.eid.bbs.SecretKey;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@ConfigurationProperties(prefix = "application.key.bbs")
@Data
public class BBSKeyConfig {

    @NotNull
    private String seed;

    public SecretKey getBBSKey() {
        return SecretKey.Companion.from(seed);
    }

    public PublicKey getPublicBBSKey() {
        return KeyPair.Companion.from(getBBSKey()).getPublicKey();
    }
}
