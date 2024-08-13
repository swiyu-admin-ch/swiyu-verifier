package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresentationFormatFactory {

    private final BBSKeyConfiguration bbsKeyConfiguration;

    public CredentialBuilder getFormatBuilder(PresentationSubmission presentationSubmission) {

        // TODO assume only 1 submission at the moment
        var format = presentationSubmission.getDescriptorMap().getFirst().getFormat();

        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("No format " + format);
        }

        return switch (format) {
            case "ldp_vp" -> new LdpCredential(bbsKeyConfiguration);
            case "jwt_vp_json", "jwt_vc" -> new SDJWTCredential();
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        };
    }
}
