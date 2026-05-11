#!/bin/bash

# Shared password for simplicity (you can change this to be dynamic)
PW="banana"

# Define the domains
# Format: "Domain:FilenameSuffix"
DOMAINS=(
    "users.ourorg0:users-0"
    "messages0.ourorg0:messages0-0"
    "messages1.ourorg0:messages1-0"
    "messages2.ourorg0:messages2-0"
    "users.ourorg1:users-1"
    "messages0.ourorg1:messages0-1"
    "messages1.ourorg1:messages1-1"
    "messages2.ourorg1:messages2-1"
    "users.ourorg2:users-2"
    "messages0.ourorg2:messages0-2"
)

# Clean up
rm -f *.ks *.cert
TRUSTSTORE="truststore.ks"
TESTER_ARGS=""

echo "Generating Keystores..."

for entry in "${DOMAINS[@]}"; do
    # Split the entry into domain and the filename prefix
    DOMAIN="${entry%%:*}"
    
    # Use the FULL domain name in the filename to satisfy the tester
    FILE_NAME="${DOMAIN}-server.ks"

    echo "Processing $DOMAIN -> $FILE_NAME"

    # 1. Generate KeyPair
    keytool -genkeypair \
        -alias "$DOMAIN" \
        -keyalg RSA \
        -validity 365 \
        -keystore "$FILE_NAME" \
        -storetype pkcs12 \
        -storepass "$PW" \
        -keypass "$PW" \
        -dname "CN=$DOMAIN, OU=SD, O=OurOrg, L=Lisbon, C=PT" \
        -ext "SAN=dns:$DOMAIN,dns:localhost"

    # 2. Export Cert
    keytool -exportcert \
        -alias "$DOMAIN" \
        -keystore "$FILE_NAME" \
        -file "${FILE_BASE}.cert" \
        -storepass "$PW"

    # 3. Import to Truststore
    keytool -importcert \
        -file "${FILE_BASE}.cert" \
        -alias "$DOMAIN" \
        -keystore "$TRUSTSTORE" \
        -storepass "$PW" \
        -noprompt

    # 4. Build the argument string for the tester (only for message servers)
    if [[ $DOMAIN == messages* ]]; then
        TESTER_ARGS+="$DOMAIN,$FILE_NAME,$PW "
    fi
done

# Cleanup temp certs
rm -f *.cert

echo "-------------------------------------------------------"
echo "DONE!"
echo ""
echo "Use the following string for your SERVERS_KEYSTORES argument:"
echo "-------------------------------------------------------"
echo $TESTER_ARGS
echo "-------------------------------------------------------"