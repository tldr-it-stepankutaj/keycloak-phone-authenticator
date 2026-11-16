# =============================================================================
# Keycloak with Phone Number Authenticator
# =============================================================================
# Build from project root: docker build -f docker/Dockerfile -t keycloak-phone-auth .
# Or use: ./build.sh docker
# =============================================================================

FROM quay.io/keycloak/keycloak:26.6.2

LABEL maintainer="tegadeti"
LABEL description="Keycloak with Phone Number Authenticator"
LABEL version="1.0.0"

# Switch to root for installation
USER root

# Copy provider JAR (built with maven)
COPY target/keycloak-phone-authenticator-*.jar /opt/keycloak/providers/

# Copy themes
COPY themes/phone-theme /opt/keycloak/themes/phone-theme

# Set permissions
RUN chown -R keycloak:keycloak /opt/keycloak/providers/ && \
    chown -R keycloak:keycloak /opt/keycloak/themes/phone-theme && \
    chmod 644 /opt/keycloak/providers/*.jar

# Switch back to keycloak user
USER keycloak

# Build Keycloak with the new provider
RUN /opt/keycloak/bin/kc.sh build \
    --health-enabled=true \
    --metrics-enabled=true \
    --db=postgres

# Default entrypoint
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
