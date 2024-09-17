package ch.admin.bit.eid.oid4vp;

import ch.admin.bit.eid.oid4vp.config.BbsKeyProperties;
import ch.admin.bit.eid.oid4vp.controller.VerificationController;
import ch.admin.eid.bbscryptosuite.BbsCryptoSuite;
import ch.admin.eid.bbscryptosuite.CryptoSuiteOptions;
import ch.admin.eid.bbscryptosuite.CryptoSuiteType;
import ch.admin.eid.bbscryptosuite.InternalException;
import org.assertj.core.util.Strings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Oid4VpApplicationTests {

    @Autowired
    private VerificationController verificationController;

    @Autowired
    private BbsKeyProperties bbsKeyProperties;


    /*Sanity Test to check if the application even loads*/
    @Test
    void contextLoads() {
        assertThat(verificationController).isNotNull();
        assertThat(bbsKeyProperties).isNotNull();
    }

    @Test
    void bbsLibraryTest() {
        BbsCryptoSuite bbsCryptoSuite = new BbsCryptoSuite(bbsKeyProperties.getBBSKey());

        CryptoSuiteOptions options = new CryptoSuiteOptions(
                Stream.of("/id", "/type", "/issuer", "/validFrom").toList(),
                CryptoSuiteType.BBS2023,
                // todo check
                "https://thepublickey"
        );

        // 1. Generate base proof
        String originalVc = """
                {"@context": ["https://www.w3.org/ns/credentials/v2","https://www.w3.org/ns/credentials/examples/v2"],"id": "did:example:acfeb1f712ebc6f1c276e12e44","type": ["VerifiableCredential", "ExampleAlumniCredential"],"issuer": "https://university.example/issuers/565049","validFrom": "32010-01-01T19:23:24Z","credentialSubject": {"name": "Test","first_name": "Max","last_name": "Mustermann","origin_canton": "Bern","origin_city": "Biel/Bienne","address":{"street": "Bahnhofsplatz","number": "1","city": "BielBienne","postal_code": "2502"},"metadata": {"eye_color": "blue", "height": "180"}}}""";

        assertFalse(Strings.isNullOrEmpty(bbsCryptoSuite.addProof(originalVc, options)));
    }

    @Test
    void bbsLibraryTestInvalidContext_thenException() {
        var bbsCryptoSuite = new BbsCryptoSuite(bbsKeyProperties.getBBSKey());

        CryptoSuiteOptions options = new CryptoSuiteOptions(
                Stream.of("/id", "/type", "/issuer", "/validFrom").toList(),
                CryptoSuiteType.BBS2023,
                "https://thepublickey"
        );

        // 1. Generate base proof
        String originalVc = """
                {"@context": "https://www.w3.org/ns/credentials/v2","https://www.w3.org/ns/credentials/examples/v2"],"id": "did:example:acfeb1f712ebc6f1c276e12e44","type": ["VerifiableCredential", "ExampleAlumniCredential"],"issuer": "https://university.example/issuers/565049","validFrom": "32010-01-01T19:23:24Z","credentialSubject": {"name": "Test","first_name": "Max","last_name": "Mustermann","origin_canton": "Bern","origin_city": "Biel/Bienne","address":{"street": "Bahnhofsplatz","number": "1","city": "BielBienne","postal_code": "2502"},"metadata": {"eye_color": "blue", "height": "180"}}}""";

        assertThrows(InternalException.class, () -> bbsCryptoSuite.addProof(originalVc, options));
    }

    @Test
    void bbsLibraryNoIDTest() {
        var bbsCryptoSuite = new BbsCryptoSuite(bbsKeyProperties.getBBSKey());
        CryptoSuiteOptions options = new CryptoSuiteOptions(
                Stream.of("/type", "/issuer", "/validFrom").toList(),
                CryptoSuiteType.BBS2023,
                "https://thepublickey"
        );

        // 1. Generate base proof
        String originalVc = """
                {"@context": ["https://www.w3.org/ns/credentials/v2","https://www.w3.org/ns/credentials/examples/v2"],"type": ["VerifiableCredential", "ExampleAlumniCredential"],"issuer": "https://university.example/issuers/565049","validFrom": "32010-01-01T19:23:24Z","credentialSubject": {"name": "Test","first_name": "Max","last_name": "Mustermann","origin_canton": "Bern","origin_city": "Biel/Bienne","address":{"street": "Bahnhofsplatz","number": "1","city": "BielBienne","postal_code": "2502"},"metadata": {"eye_color": "blue", "height": "180"}}}""";

        assertFalse(Strings.isNullOrEmpty(bbsCryptoSuite.addProof(originalVc, options)));
    }
}
