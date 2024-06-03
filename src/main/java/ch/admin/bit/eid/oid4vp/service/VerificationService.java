package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.stereotype.Service;
import uniffi.api.KeyPair;
import uniffi.cryptosuite.BbsCryptoSuite;
import uniffi.cryptosuite.CryptoSuiteVerificationResult;

import java.util.HashMap;

@Service
@AllArgsConstructor
public class VerificationService {
    private final VerificationManagementRepository verificationManagementRepository;


    /**
     * Process the answer of a holder refusing to verify.
     * @param managementEntity
     * @param error
     * @param errorDescription
     */
    public void processHolderVerificationRejection(ManagementEntity managementEntity, String error, String errorDescription) {
        managementEntity.setWalletResponse(
                ResponseData.builder()
                        .errorCode(ResponseErrorCodeEnum.CLIENT_REJECTED)
                        .build()
        );
        managementEntity.setState(VerificationStatusEnum.FAILED);

        verificationManagementRepository.save(managementEntity);
    }

    public void processPresentation(ManagementEntity managementEntity, String vpToken, String presentationSubmission) {
        // TODO - Parse presentationSubmission and find out that we need to process it as BBS, ECDSA VC-DI or SD-JWT or JWT

        var walletResponseBuilder = ResponseData.builder();
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(vpToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            var errorCode = ResponseErrorCodeEnum.CREDENTIAL_INVALID;
            walletResponseBuilder.errorCode(errorCode);
            managementEntity.setWalletResponse(walletResponseBuilder.build());
            managementEntity.setState(VerificationStatusEnum.FAILED);
            verificationManagementRepository.save(managementEntity);
            throw VerificationException.credentialError(errorCode,
                    "The credential data integrity signature could not be verified");
        }
        // TODO See if the presentationSubmission matches with the request object
        // TODO  See if the requested data is actually here
        // TODO Validate VC validity
        // TODO Validate
        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        var parser = new GsonJsonParser();

        var credentialSubject = parser.parseMap(verifiedDocument).get("credentialSubject");
        walletResponseBuilder.credentialSubjectData(new Gson().toJson(credentialSubject));

    }


    private String verifyProofBBS(String bbsCredential, String nonce) throws Exception {
        // TODO Get the keys from VDR
        KeyPair keys = new KeyPair();
        BbsCryptoSuite suite = new BbsCryptoSuite(keys);
        CryptoSuiteVerificationResult verificationResult =  suite.verifyProof(bbsCredential, nonce, keys.getPublicKey());
        if ( ! verificationResult.component1()){
            throw new Exception();
        }
        return verificationResult.getVerifiedDocument();

    }


}
