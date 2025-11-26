package ch.admin.bj.swiyu.verifier.common.config;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

@RequiredArgsConstructor
public class StatusListCachingCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        var statusListTTL = context.getEnvironment().getProperty("cache.status-list-ttl", Long.class, 0L);

        return statusListTTL > 0L;
    }
}