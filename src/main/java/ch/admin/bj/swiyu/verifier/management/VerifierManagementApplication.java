/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@Slf4j
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class VerifierManagementApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(VerifierManagementApplication.class, args).getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String serverPort = env.getProperty("server.port");
        log.info(
                """
                                                
                        ----------------------------------------------------------------------------
                        \t'{}' is running!\s
                        \tProfile(s): \t\t\t\t{}
                        \tSwaggerUI:   \t\t\t\thttp://localhost:{}/swagger-ui.html
                        ----------------------------------------------------------------------------""",
                appName,
                env.getActiveProfiles(),
                serverPort
        );
    }

}
