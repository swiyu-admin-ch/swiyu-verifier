package ch.admin.bit.eid.oid4vp.config;

import ch.qos.logback.access.tomcat.LogbackValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessLogConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> accessLogCustomizer() {
        return factory -> {
            final LogbackValve logbackValve = new LogbackValve();
            logbackValve.setFilename("logging/logback-access.xml");
            logbackValve.setAsyncSupported(true);
            factory.addContextValves(logbackValve);
        };
    }
}
