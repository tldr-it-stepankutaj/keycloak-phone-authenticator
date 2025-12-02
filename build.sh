#!/bin/bash

# =============================================================================
# Keycloak Phone Authenticator - Build & Deploy Script
# =============================================================================
# Usage: ./build.sh [command]
#
# Commands:
#   build     - Build JAR package (default)
#   test      - Run tests
#   verify    - Verify JAR structure
#   docker    - Build and start Docker environment
#   deploy    - Deploy to existing Keycloak installation
#   clean     - Clean build artifacts
#   help      - Show this help
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Logging functions
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Checks
# =============================================================================

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed"
        exit 1
    fi

    # Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        log_error "Java 21+ required, found: $JAVA_VERSION"
        exit 1
    fi

    log_success "Prerequisites OK (Java $JAVA_VERSION)"
}

# =============================================================================
# Build commands
# =============================================================================

cmd_clean() {
    log_info "Cleaning build artifacts..."
    mvn clean -q
    log_success "Clean complete"
}

cmd_build() {
    check_prerequisites
    log_info "Building project..."
    mvn clean package -DskipTests -q

    JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" ! -name "original-*" | head -1)
    if [ -f "$JAR_FILE" ]; then
        log_success "Build complete: $JAR_FILE ($(du -h "$JAR_FILE" | cut -f1))"
    else
        log_error "JAR file not found"
        exit 1
    fi
}

cmd_test() {
    check_prerequisites
    log_info "Running tests..."
    mvn test
    log_success "Tests complete"
}

cmd_verify() {
    JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" ! -name "original-*" 2>/dev/null | head -1)

    if [ ! -f "$JAR_FILE" ]; then
        log_warn "JAR not found, building first..."
        cmd_build
        JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" ! -name "original-*" | head -1)
    fi

    log_info "Verifying JAR structure..."

    local errors=0

    # Check SPI service file
    if jar tf "$JAR_FILE" | grep -q "META-INF/services/org.keycloak.authentication.AuthenticatorFactory"; then
        log_success "SPI service file found"
    else
        log_error "SPI service file missing"
        errors=$((errors + 1))
    fi

    # Check main classes
    for class in PhoneAuthenticatorFactory PhoneAuthenticator TwilioSmsService RateLimitService; do
        if jar tf "$JAR_FILE" | grep -q "com/tegadeti/keycloak/provider/${class}.class"; then
            log_success "$class found"
        else
            log_error "$class missing"
            errors=$((errors + 1))
        fi
    done

    if [ $errors -gt 0 ]; then
        log_error "Verification failed with $errors errors"
        exit 1
    fi

    log_success "JAR verification passed"
}

cmd_docker() {
    log_info "Building Docker environment..."

    # Build JAR first
    cmd_build

    # Build and start Docker
    cd docker
    docker compose build
    log_success "Docker image built"

    echo
    log_info "Starting Docker environment..."
    docker compose up -d

    echo
    log_success "Docker environment started!"
    echo
    echo "Services:"
    echo "  - Keycloak:  http://localhost:8080 (admin/admin)"
    echo "  - MailHog:   http://localhost:8025"
    echo "  - PostgreSQL: localhost:5432"
    echo
    echo "Commands:"
    echo "  docker compose -f docker/docker-compose.yml logs -f keycloak"
    echo "  docker compose -f docker/docker-compose.yml down"
}

cmd_deploy() {
    local KEYCLOAK_HOME=${1:-"/opt/keycloak"}

    if [ ! -d "$KEYCLOAK_HOME" ] || [ ! -f "$KEYCLOAK_HOME/bin/kc.sh" ]; then
        log_error "Invalid Keycloak directory: $KEYCLOAK_HOME"
        echo "Usage: $0 deploy [KEYCLOAK_HOME]"
        exit 1
    fi

    log_info "Deploying to $KEYCLOAK_HOME..."

    # Build
    cmd_build

    # Copy JAR
    JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" ! -name "original-*" | head -1)
    mkdir -p "$KEYCLOAK_HOME/providers"
    cp "$JAR_FILE" "$KEYCLOAK_HOME/providers/"
    log_success "JAR deployed"

    # Copy themes
    if [ -d "themes/phone-theme" ]; then
        cp -r themes/phone-theme "$KEYCLOAK_HOME/themes/"
        log_success "Theme deployed"
    fi

    # Rebuild Keycloak
    log_info "Rebuilding Keycloak..."
    "$KEYCLOAK_HOME/bin/kc.sh" build --health-enabled=true >/dev/null 2>&1
    log_success "Keycloak rebuilt"

    echo
    log_success "Deployment complete!"
    echo
    echo "Next steps:"
    echo "  1. Start Keycloak: $KEYCLOAK_HOME/bin/kc.sh start"
    echo "  2. Configure authentication flow in Admin Console"
}

cmd_help() {
    echo "Keycloak Phone Authenticator - Build Script"
    echo
    echo "Usage: $0 [command]"
    echo
    echo "Commands:"
    echo "  build     Build JAR package (default)"
    echo "  test      Run tests"
    echo "  verify    Verify JAR structure"
    echo "  docker    Build and start Docker environment"
    echo "  deploy    Deploy to Keycloak: $0 deploy [KEYCLOAK_HOME]"
    echo "  clean     Clean build artifacts"
    echo "  help      Show this help"
    echo
}

# =============================================================================
# Main
# =============================================================================

case "${1:-build}" in
    build)   cmd_build ;;
    test)    cmd_test ;;
    verify)  cmd_verify ;;
    docker)  cmd_docker ;;
    deploy)  cmd_deploy "$2" ;;
    clean)   cmd_clean ;;
    help)    cmd_help ;;
    *)
        log_error "Unknown command: $1"
        cmd_help
        exit 1
        ;;
esac
