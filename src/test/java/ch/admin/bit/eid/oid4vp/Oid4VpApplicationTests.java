package ch.admin.bit.eid.oid4vp;

import ch.admin.bit.eid.oid4vp.controller.VerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uniffi.api.KeyPair;
import uniffi.cryptosuite.BbsCryptoSuite;
import uniffi.cryptosuite.CryptoSuiteOptions;
import uniffi.cryptosuite.CryptoSuiteType;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class Oid4VpApplicationTests {

	@Autowired
	private VerificationController verificationController;

	/*Sanity Test to check if the application even loads*/
	@Test
	void contextLoads() {
		assertThat(verificationController).isNotNull();
	}

	@Test
	void bbsLibraryTest() {
		CryptoSuiteOptions options = new CryptoSuiteOptions(
				Stream.of("/id", "/type", "/issuer","/validFrom").toList(),
				CryptoSuiteType.BBS2023,
				"https://thepublickey"
		);

		KeyPair keyPair = new KeyPair();
		BbsCryptoSuite bbsCryptoSuite = new BbsCryptoSuite(keyPair);

		// 1. Generate base proof
		String originalVc = """
                {"@context": ["https://www.w3.org/ns/credentials/v2","https://www.w3.org/ns/credentials/examples/v2"],"id": "did:example:acfeb1f712ebc6f1c276e12e44","type": ["VerifiableCredential", "ExampleAlumniCredential"],"issuer": "https://university.example/issuers/565049","validFrom": "32010-01-01T19:23:24Z","credentialSubject": {"name": "Test","first_name": "Max","last_name": "Mustermann","origin_canton": "Bern","origin_city": "Biel/Bienne","address":{"street": "Bahnhofsplatz","number": "1","city": "BielBienne","postal_code": "2502"},"metadata": {"eye_color": "blue", "height": "180"}}}""";
		String baseProof = bbsCryptoSuite.addProof(originalVc, options);

		assert baseProof != null;
		assert !baseProof.isEmpty();
	}

	@Test
	void bbsLibraryNoIDTest() {
		CryptoSuiteOptions options = new CryptoSuiteOptions(
				Stream.of("/type", "/issuer", "/validFrom").toList(),
				CryptoSuiteType.BBS2023,
				"https://thepublickey"
		);

		KeyPair keyPair = new KeyPair();
		BbsCryptoSuite bbsCryptoSuite = new BbsCryptoSuite(keyPair);

		// 1. Generate base proof
		String originalVc = """
                {"@context": ["https://www.w3.org/ns/credentials/v2","https://www.w3.org/ns/credentials/examples/v2"],"type": ["VerifiableCredential", "ExampleAlumniCredential"],"issuer": "https://university.example/issuers/565049","validFrom": "32010-01-01T19:23:24Z","credentialSubject": {"name": "Test","first_name": "Max","last_name": "Mustermann","origin_canton": "Bern","origin_city": "Biel/Bienne","address":{"street": "Bahnhofsplatz","number": "1","city": "BielBienne","postal_code": "2502"},"metadata": {"eye_color": "blue", "height": "180"}}}""";
		String baseProof = bbsCryptoSuite.addProof(originalVc, options);

		assert baseProof != null;
		assert !baseProof.isEmpty();
	}

}
