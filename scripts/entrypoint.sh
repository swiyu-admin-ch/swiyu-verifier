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

java -Duser.timezone=Europe/Zurich \
-Dspring.config.location=classpath:bootstrap.yml,classpath:application.yml,optional:file:/vault/secrets/database-credentials.yml \
-Dfile.encoding=UTF-8 \
-Dspring.profiles.active=${STAGE} \
-Dhttp.proxyHost=${HTTP_PROXY} \
-Dhttp.proxyPort=8080 \
-Dhttps.proxyHost=${HTTPS_PROXY} \
-Dhttps.proxyPort=8080 \
-Dhttp.nonProxyHosts="${NO_PROXY}" \
-jar $1
