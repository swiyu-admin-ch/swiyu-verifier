package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry to select the appropriate presentation verification strategy
 * based on the presentation format.
 */
@Component
public class PresentationVerificationStrategyRegistry {

    private final Map<String, PresentationVerificationStrategy> strategiesByFormat;

    public PresentationVerificationStrategyRegistry(List<PresentationVerificationStrategy> strategies) {
        this.strategiesByFormat = Objects.requireNonNull(strategies, "strategies")
                .stream()
                .collect(Collectors.toMap(PresentationVerificationStrategy::getSupportedFormat, Function.identity()));
    }

    public PresentationVerificationStrategy getStrategy(String format) {
        var strategy = strategiesByFormat.get(format);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown format: " + format);
        }
        return strategy;
    }
}
