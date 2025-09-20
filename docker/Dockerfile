FROM quay.io/keycloak/keycloak:26.3.1

# Metadata
LABEL maintainer="tegadeti"
LABEL description="Keycloak with Phone Number Authenticator"
LABEL version="1.0.0"

# Switch to root for installation
USER 0

# Copy provider JAR
COPY ../target/keycloak-phone-authenticator-*.jar /opt/keycloak/providers/

# Copy themes
COPY ../themes/ /opt/keycloak/themes/

# Set permissions
RUN chown -R keycloak:keycloak /opt/keycloak/providers/ && \
    chown -R keycloak:keycloak /opt/keycloak/themes/ && \
    chmod 644 /opt/keycloak/providers/*.jar

# Switch back to keycloak user
USER keycloak

# Build Keycloak with the new provider
RUN /opt/keycloak/bin/kc.sh build \
    --health-enabled=true \
    --metrics-enabled=true \
    --db=postgres

# Entry point
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]