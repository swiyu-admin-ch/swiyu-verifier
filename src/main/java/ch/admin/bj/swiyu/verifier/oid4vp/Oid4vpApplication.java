/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
public class Oid4vpApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(Oid4vpApplication.class, args).getEnvironment();
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
