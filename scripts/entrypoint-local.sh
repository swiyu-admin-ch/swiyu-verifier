#!/bin/bash
# Reduced entrypoint script for local execution - does not mount vault

# Add microservice CAs
echo "Adding certificates to Java truststore:"
CERT_FILES=$(ls /certs-app/*.c*rt)
if [ "$CERT_FILES" ]; then
    for CERT in $CERT_FILES; do
        CERT_ALIAS=$(basename $CERT .crt)
        echo " => adding $CERT as $CERT_ALIAS to truststore"
        $JAVA_HOME/bin/keytool -importcert -file $CERT -alias $CERT_ALIAS -cacerts -storepass changeit -noprompt -trustcacerts
    done
else
    echo " => No certificates found, skipping"
fi

java -Duser.timezone=Europe/Zurich \
-Dfile.encoding=UTF-8 \
-Dspring.profiles.active=${STAGE} \
-Dhttp.proxyHost=${HTTP_PROXY} \
-Dhttp.proxyPort=8080 \
-Dhttps.proxyHost=${HTTPS_PROXY} \
-Dhttps.proxyPort=8080 \
-Dhttp.nonProxyHosts="${NO_PROXY}" \
-jar $1