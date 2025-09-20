#!/bin/bash

# Build script pro Keycloak Phone Authenticator
# Použití: ./scripts/build.sh [clean|test|package|docker]

set -e

# Barvy pro output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Projekt root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# Funkce pro výpis zpráv
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Kontrola Maven instalace
check_maven() {
    if ! command -v mvn &> /dev/null; then
        log_error "Maven není nainstalován nebo není v PATH"
        exit 1
    fi

    log_info "Maven version: $(mvn --version | head -1)"
}

# Kontrola Java verze
check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java není nainstalována nebo není v PATH"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        log_error "Vyžaduje Java 21+, nalezena: $JAVA_VERSION"
        exit 1
    fi

    log_info "Java version: $(java -version 2>&1 | head -1)"
}

# Clean build
clean_build() {
    log_info "Cleaning project..."
    mvn clean
    log_success "Project cleaned"
}

# Compile project
compile_project() {
    log_info "Compiling project..."
    mvn compile
    log_success "Project compiled"
}

# Run tests
run_tests() {
    log_info "Running tests..."
    mvn test

    if [ $? -eq 0 ]; then
        log_success "All tests passed"
    else
        log_error "Some tests failed"
        exit 1
    fi
}

# Package project
package_project() {
    log_info "Packaging project..."
    mvn package -DskipTests

    # Check if JAR was created
    JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" | head -1)
    if [ -f "$JAR_FILE" ]; then
        log_success "JAR created: $JAR_FILE"

        # Show JAR info
        log_info "JAR size: $(du -h "$JAR_FILE" | cut -f1)"
        log_info "JAR contents:"
        jar tf "$JAR_FILE" | grep -E "(Factory|Authenticator|Service)" | head -10
    else
        log_error "JAR file not found"
        exit 1
    fi
}

# Verify JAR structure
verify_jar() {
    JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" | head -1)
    if [ ! -f "$JAR_FILE" ]; then
        log_error "JAR file not found. Run 'package' first."
        exit 1
    fi

    log_info "Verifying JAR structure..."

    # Check META-INF/services
    if jar tf "$JAR_FILE" | grep -q "META-INF/services/org.keycloak.authentication.AuthenticatorFactory"; then
        log_success "✓ SPI service file found"
    else
        log_error "✗ SPI service file missing"
        exit 1
    fi

    # Check main classes
    if jar tf "$JAR_FILE" | grep -q "com/tegadeti/keycloak/provider/PhoneAuthenticatorFactory.class"; then
        log_success "✓ AuthenticatorFactory class found"
    else
        log_error "✗ AuthenticatorFactory class missing"
        exit 1
    fi

    if jar tf "$JAR_FILE" | grep -q "com/tegadeti/keycloak/provider/PhoneAuthenticator.class"; then
        log_success "✓ Authenticator class found"
    else
        log_error "✗ Authenticator class missing"
        exit 1
    fi

    if jar tf "$JAR_FILE" | grep -q "com/tegadeti/keycloak/provider/TwilioSmsService.class"; then
        log_success "✓ SMS Service class found"
    else
        log_error "✗ SMS Service class missing"
        exit 1
    fi

    log_success "JAR structure verification passed"
}

# Build Docker image
build_docker() {
    log_info "Building Docker image..."

    # Check if JAR exists
    if [ ! -f "target/keycloak-phone-authenticator-"*.jar ]; then
        log_warn "JAR not found, building first..."
        package_project
    fi

    cd docker
    docker build -t keycloak-phone-auth:latest .

    if [ $? -eq 0 ]; then
        log_success "Docker image built: keycloak-phone-auth:latest"

        # Show image info
        log_info "Image size: $(docker images keycloak-phone-auth:latest --format "table {{.Size}}" | tail -1)"
    else
        log_error "Docker build failed"
        exit 1
    fi

    cd "$PROJECT_ROOT"
}

# Full build
full_build() {
    log_info "Starting full build..."
    check_maven
    check_java
    clean_build
    compile_project
    run_tests
    package_project
    verify_jar
    log_success "Full build completed successfully!"
}

# Show help
show_help() {
    echo "Keycloak Phone Authenticator Build Script"
    echo
    echo "Usage: $0 [command]"
    echo
    echo "Commands:"
    echo "  clean      - Clean build artifacts"
    echo "  compile    - Compile source code"
    echo "  test       - Run unit tests"
    echo "  package    - Create JAR package"
    echo "  verify     - Verify JAR structure"
    echo "  docker     - Build Docker image"
    echo "  full       - Run full build (default)"
    echo "  help       - Show this help"
    echo
}

# Main logic
case "${1:-full}" in
    clean)
        check_maven
        clean_build
        ;;
    compile)
        check_maven
        check_java
        compile_project
        ;;
    test)
        check_maven
        check_java
        run_tests
        ;;
    package)
        check_maven
        check_java
        package_project
        ;;
    verify)
        verify_jar
        ;;
    docker)
        build_docker
        ;;
    full)
        full_build
        ;;
    help)
        show_help
        ;;
    *)
        log_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac