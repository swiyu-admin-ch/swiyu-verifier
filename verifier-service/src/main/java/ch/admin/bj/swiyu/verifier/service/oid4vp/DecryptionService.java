package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseMode;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DecryptionService {
    private final ManagementRepository managementEntityRepository;
    private final ObjectMapper objectMapper;

    @NotNull
    private static JWEDecrypter createJweDecryptor(Management managementEntity, String keyId) throws ParseException, JOSEException {
        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        if (!ResponseMode.DIRECT_POST_JWT.equals(responseSpecification.getResponseMode())) {
            // We did not ask for an encrypted response!
            throw new IllegalArgumentException("No encrypted response expected.");
        }

        JWKSet privateKeys = JWKSet.parse(Optional.ofNullable(responseSpecification.getJwksPrivate())
                // Throw illegal state, as this would be a server error
                .orElseThrow(() -> new IllegalStateException("Missing JWK private. Unable to decrypt response.")));
        ECKey privateKey = Optional.ofNullable(privateKeys.getKeyByKeyId(keyId).toECKey())
                .orElseThrow(() -> new IllegalArgumentException("No matching JWK for keyId %s found. Unable to decrypt response.".formatted(keyId)));
        return new ECDHDecrypter(privateKey);
    }

    /**
     * Decrypts the verification response, if required by the verification request.
     * Else returns the verificationResponse unedited
     *
     * @param managementEntityId   the id of the verification request / management id
     * @param verificationResponse response sent by the wallet
     * @return a decrypted VerificationPresentationUnionDto
     * @throws IllegalArgumentException if <ul>
     *     <li>the verificationResponse should have been encrypted but is unencrypted or contains unencrypted data</li>
     *     <li>Some issue is detected with the encryption</li>
     *     <li>response_mode is unrecognized</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public VerificationPresentationUnionDto decrypt(UUID managementEntityId, VerificationPresentationUnionDto verificationResponse) {

        Management managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        ResponseMode responseMode = managementEntity.getResponseSpecification().getResponseMode();
        if (ResponseMode.DIRECT_POST.equals(responseMode)) {
            return verificationResponse;
        } else if (ResponseMode.DIRECT_POST_JWT.equals(responseMode) && verificationResponse.isEncryptedPresentation()) {
            return jweDecrypt(verificationResponse, managementEntity);
        } else {
            throw  new IllegalArgumentException("Lacking encryption. All elements of the response should be encrypted.");
        }
    }

    private VerificationPresentationUnionDto jweDecrypt(VerificationPresentationUnionDto verificationResponse, Management managementEntity) {
        try {
            JWEObject jwe = JWEObject.parse(verificationResponse.getResponse());
            String keyId = Optional.ofNullable(jwe.getHeader().getKeyID()).orElseThrow(() -> new IllegalArgumentException("Missing keyId. Unable to decrypt response."));
            JWEDecrypter decrypter = createJweDecryptor(managementEntity, keyId);
            jwe.decrypt(decrypter);
            return objectMapper.readValue(jwe.getPayload().toString(), VerificationPresentationUnionDto.class);
        } catch (ParseException | JOSEException | JsonProcessingException e) {
            throw new IllegalArgumentException("Response cannot be decrypted", e);
        }
    }
}
