#!/bin/bash

#
# SPDX-FileCopyrightText: 2025 Swiss Confederation
#
# SPDX-License-Identifier: MIT
#

# Add microservice CAs
echo "Adding certificates to Java truststore:"
if ls /certs-app/*.crt &> /dev/null; then
    for f in $(ls /certs-app/*.crt); do
        CERT=$f
        CERT_ALIAS=$(basename $CERT .crt)
        echo " => adding $CERT as $CERT_ALIAS to truststore"
        $JAVA_HOME/bin/keytool -importcert -file $CERT -alias $CERT_ALIAS -cacerts -storepass changeit -noprompt -trustcacerts
    done
else
    echo " => No certificates found, skipping"
fi


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
test -d "${JAVA_BOOTCLASSPATH}" && bootclasspath_java_opt=-Xbootclasspath/a:$(find "${JAVA_BOOTCLASSPATH}" -type f -name "*.jar" | xargs -I {} echo {} | tr '\n' ':')

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
