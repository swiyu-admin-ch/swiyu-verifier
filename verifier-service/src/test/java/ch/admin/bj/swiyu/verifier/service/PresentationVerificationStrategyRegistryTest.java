package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresentationVerificationStrategyRegistryTest {

    private static class DummyStrategy implements PresentationVerificationStrategy {
        private final String format;

        DummyStrategy(String format) {
            this.format = format;
        }

        @Override
        public String getSupportedFormat() {
            return format;
        }

        @Override
        public String verify(String vpToken, Management management, PresentationSubmissionDto submission) {
            return "verified:" + format;
        }
    }

    @Test
    void getStrategy_returnsStrategyForKnownFormat() {
        // Arrange
        var sdJwtStrategy = new DummyStrategy("vc+sd-jwt");
        var dcSdJwtStrategy = new DummyStrategy("dc+sd-jwt");
        var registry = new PresentationVerificationStrategyRegistry(List.of(sdJwtStrategy, dcSdJwtStrategy));

        // Act
        var result = registry.getStrategy("dc+sd-jwt");

        // Assert
        assertThat(result).isSameAs(dcSdJwtStrategy);
    }

    @Test
    void getStrategy_whenUnknownFormat_thenThrowsIllegalArgumentException() {
        // Arrange
        var sdJwtStrategy = new DummyStrategy("vc+sd-jwt");
        var registry = new PresentationVerificationStrategyRegistry(List.of(sdJwtStrategy));

        // Act & Assert
        assertThatThrownBy(() -> registry.getStrategy("unknown-format"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown format: unknown-format");
    }
}

