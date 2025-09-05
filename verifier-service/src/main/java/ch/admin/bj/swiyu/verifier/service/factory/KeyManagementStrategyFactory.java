package ch.admin.bj.swiyu.verifier.service.factory;

import ch.admin.bj.swiyu.verifier.service.factory.strategy.IKeyManagementStrategy;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory to create the appropriate key management strategy based on the key management method.
 * <p>
 * This factory uses a map to associate key management methods with their corresponding strategies.
 * It provides a method to retrieve the appropriate strategy based on the provided key management method.
 */
@Component
@AllArgsConstructor
public class KeyManagementStrategyFactory {

    private final Map<String, IKeyManagementStrategy> strategyMap;

    /**
     * Retrieves the appropriate key management strategy based on the provided key management method.
     *
     * @param keyManagementMethod The key management method (e.g., "key", "pkcs11", "securosys").
     * @return The corresponding KeyManagementStrategy instance.
     * @throws IllegalArgumentException if the provided key management method is not supported.
     */
    public IKeyManagementStrategy getStrategy(String keyManagementMethod) {
        IKeyManagementStrategy strategy = strategyMap.get(keyManagementMethod);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported key management method: " + keyManagementMethod);
        }
        return strategy;
    }
}
