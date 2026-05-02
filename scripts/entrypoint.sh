#!/bin/bash

# If the directory provided through environment variable $JAVA_BOOTCLASSPATH exists, we gather up all .jar files in it and append them to the classpath.
# This is done by concatenating all filenames with a colon
# Resulting line looks something like
# -Xbootclasspath/a:my-jce-provider1.jar:my-classpath-lib.jar
# On java bootclasspath:
# https://docs.oracle.com/en/java/javase/24/docs/specs/man/java.html#extra-options-for-java:
#
# -Xbootclasspath/a:directories|zip|JAR-files
#    Specifies a list of directories, JAR files, and ZIP archives to append to the end of the default bootstrap class path.
#
#    On Windows, semicolons (;) separate entities in this list; on other platforms it is a colon (:).
#
test -d "${JAVA_BOOTCLASSPATH}" && bootclasspath_java_opt=-Xbootclasspath/a:$(ls $JAVA_BOOTCLASSPATH/*.jar | tr '\n' ':')

# Print truststore presence for baked-in certs so it is visible in container startup logs.
if ls /certs-app/*.crt >/dev/null 2>&1; then
    echo "Checking certificates in Java truststore:"
    for cert in /certs-app/*.crt; do
        alias="$(basename "$cert" .crt)"
        if keytool -list -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit -alias "$alias" >/dev/null 2>&1; then
            echo " => truststore contains alias: $alias"
        else
            echo " => truststore missing alias: $alias"
        fi
    done
fi

java -Duser.timezone=Europe/Zurich \
-Dspring.config.location=classpath:bootstrap.yml,classpath:application.yml,optional:file:/vault/secrets/database-credentials.yml \
-Dfile.encoding=UTF-8 \
-Dspring.profiles.active=${MY_SPRING_PROFILES} \
-Dhttp.proxyHost=${HTTP_PROXY} \
-Dhttp.proxyPort=8080 \
-Dhttps.proxyHost=${HTTPS_PROXY} \
-Dhttps.proxyPort=8080 \
-Dhttp.nonProxyHosts="${NO_PROXY}" \
${bootclasspath_java_opt} \
-jar $1