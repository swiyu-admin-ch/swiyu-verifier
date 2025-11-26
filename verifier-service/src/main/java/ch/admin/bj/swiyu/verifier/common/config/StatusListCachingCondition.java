package ch.admin.bj.swiyu.verifier.common.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class StatusListCachingCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var statusListTTL = context.getEnvironment().getProperty("cache.status-list-ttl", Integer.class, 0);

        return statusListTTL > 0;
    }
}