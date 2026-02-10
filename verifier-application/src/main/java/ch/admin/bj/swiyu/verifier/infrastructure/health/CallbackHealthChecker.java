package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class CallbackHealthChecker extends CachedHealthChecker {
    private static final String AMOUNT_OF_STALE_CALLBACKS = "amountOfStaleCallbacks";
    private static final Duration DEFAULT_DURATION_UNTIL_STALE = Duration.ofMinutes(1);

    private final CallbackEventRepository callbackEventRepository;
    private final Duration timeUntilStale;

    @Autowired
    public CallbackHealthChecker(CallbackEventRepository callbackEventRepository, @Value("${webhook.callback-interval:2000}") Duration callbackInterval) {
        this.callbackEventRepository = callbackEventRepository;
        if (callbackInterval.compareTo(DEFAULT_DURATION_UNTIL_STALE) < 0) {
            timeUntilStale = DEFAULT_DURATION_UNTIL_STALE;
        } else {
            timeUntilStale = callbackInterval.multipliedBy(3);
        }
    }

    @Override
    protected void performCheck(Health.Builder builder) throws Exception {
        var staleCallbacks = this.callbackEventRepository.findAllByTimestampBefore(Instant.now().minus(timeUntilStale));
        builder.withDetail(AMOUNT_OF_STALE_CALLBACKS, staleCallbacks.size());

        if (staleCallbacks.isEmpty()) {
            builder.up();
            return;
        }
        builder.down();
    }
}
