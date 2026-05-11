#!/bin/bash

# The password used for all keystores and the truststore
PW="banana"

# List of domains provided
DOMAINS=(
    "users.ourorg0"
    "messages0.ourorg0"
    "messages1.ourorg0"
    "messages2.ourorg0"
    "users.ourorg1"
    "messages0.ourorg1"
    "messages1.ourorg1"
    "messages2.ourorg1"
    "users.ourorg2"
    "messages.ourorg2"
)

# Optional: Clean up existing files to start fresh
rm -f *.ks *.cert

echo "Generating certificates for ${#DOMAINS[@]} domains..."

for DOMAIN in "${DOMAINS[@]}"; do
    echo "-------------------------------------------------------"
    echo "Processing: $DOMAIN"

    # 1. Generate KeyPair and Keystore
    # We include 'dns:localhost' in SAN so you can also test locally
    keytool -genkeypair \
        -alias "$DOMAIN" \
        -keyalg RSA \
        -validity 365 \
        -keystore "${DOMAIN}-server.ks" \
        -storetype pkcs12 \
        -storepass "$PW" \
        -keypass "$PW" \
        -dname "CN=$DOMAIN, OU=DistributedSystems, O=OurOrg, L=Lisbon, C=PT" \
        -ext "SAN=dns:$DOMAIN,dns:localhost"

    # 2. Export the Public Certificate
    keytool -exportcert \
        -alias "$DOMAIN" \
        -keystore "${DOMAIN}-server.ks" \
        -file "${DOMAIN}.cert" \
        -storepass "$PW"

    # 3. Import the Certificate into the global TrustStore
    # -noprompt allows the script to run without asking "Trust this certificate?"
    keytool -importcert \
        -file "${DOMAIN}.cert" \
        -alias "$DOMAIN" \
        -keystore "truststore.ks" \
        -storepass "$PW" \
        -noprompt

    echo "Successfully created ${DOMAIN}-server.ks and updated truststore.ks"
done

# Clean up temporary .cert files
rm -f *.cert

echo "-------------------------------------------------------"
echo "All done! You now have one .ks file per server and one shared truststore.ks."