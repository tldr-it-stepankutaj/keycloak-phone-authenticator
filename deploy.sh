#!/bin/bash

# Deployment script pro Keycloak Phone Authenticator
# Použití: ./deploy.sh [KEYCLOAK_HOME] [THEME_NAME]

set -e

# Defaultní hodnoty
KEYCLOAK_HOME=${1:-"/opt/keycloak"}
THEME_NAME=${2:-"phone-theme"}

# Barvy pro output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Keycloak Phone Authenticator Deployment ===${NC}"
echo

# Kontrola parametrů
if [ ! -d "$KEYCLOAK_HOME" ]; then
    echo -e "${RED}Error: Keycloak directory $KEYCLOAK_HOME not found!${NC}"
    echo "Usage: $0 [KEYCLOAK_HOME] [THEME_NAME]"
    exit 1
fi

if [ ! -f "$KEYCLOAK_HOME/bin/kc.sh" ]; then
    echo -e "${RED}Error: $KEYCLOAK_HOME doesn't appear to be a valid Keycloak installation!${NC}"
    exit 1
fi

echo -e "${YELLOW}Deploying to: $KEYCLOAK_HOME${NC}"
echo -e "${YELLOW}Theme name: $THEME_NAME${NC}"
echo

# 1. Build projektu
echo -e "${BLUE}1. Building project...${NC}"
if ! mvn clean package -q; then
    echo -e "${RED}Error: Maven build failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Build successful${NC}"

# 2. Deploy JAR souboru
echo -e "${BLUE}2. Deploying JAR file...${NC}"
JAR_FILE=$(find target -name "keycloak-phone-authenticator-*.jar" | head -1)
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found in target directory!${NC}"
    exit 1
fi

mkdir -p "$KEYCLOAK_HOME/providers"
cp "$JAR_FILE" "$KEYCLOAK_HOME/providers/"
echo -e "${GREEN}✓ JAR file deployed to $KEYCLOAK_HOME/providers/${NC}"

# 3. Deploy theme souborů
echo -e "${BLUE}3. Deploying theme files...${NC}"
THEME_DIR="$KEYCLOAK_HOME/themes/$THEME_NAME"
mkdir -p "$THEME_DIR/login"

# Zkopírovat template soubory
if [ -f "phone-input.ftl" ]; then
    cp phone-input.ftl "$THEME_DIR/login/"
    echo -e "${GREEN}✓ phone-input.ftl deployed${NC}"
fi

if [ -f "phone-code-verification.ftl" ]; then
    cp phone-code-verification.ftl "$THEME_DIR/login/"
    echo -e "${GREEN}✓ phone-code-verification.ftl deployed${NC}"
fi

# Vytvořit theme.properties pokud neexistuje
if [ ! -f "$THEME_DIR/theme.properties" ]; then
    cat > "$THEME_DIR/theme.properties" << EOF
parent=keycloak
import=common/keycloak

# Phone theme properties
name=$THEME_NAME
displayName=Phone Authentication Theme
version=1.0.0
EOF
    echo -e "${GREEN}✓ theme.properties created${NC}"
fi

# 4. Build Keycloak
echo -e "${BLUE}4. Building Keycloak with new provider...${NC}"
cd "$KEYCLOAK_HOME"
if ! ./bin/kc.sh build --health-enabled=true >/dev/null 2>&1; then
    echo -e "${RED}Error: Keycloak build failed!${NC}"
    echo "Try running manually: $KEYCLOAK_HOME/bin/kc.sh build"
    exit 1
fi
echo -e "${GREEN}✓ Keycloak build successful${NC}"

# 5. Kontrola deployment
echo -e "${BLUE}5. Verifying deployment...${NC}"
if [ -f "$KEYCLOAK_HOME/providers/keycloak-phone-authenticator-"*.jar ]; then
    echo -e "${GREEN}✓ Provider JAR found in providers directory${NC}"
else
    echo -e "${RED}✗ Provider JAR not found!${NC}"
fi

if [ -d "$THEME_DIR/login" ]; then
    echo -e "${GREEN}✓ Theme directory created${NC}"
else
    echo -e "${RED}✗ Theme directory not found!${NC}"
fi

echo
echo -e "${GREEN}=== Deployment completed successfully! ===${NC}"
echo
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Start/restart Keycloak:"
echo "   $KEYCLOAK_HOME/bin/kc.sh start --optimized"
echo
echo "2. In Keycloak Admin Console:"
echo "   - Go to Authentication → Flows"
echo "   - Copy Browser flow and add 'Phone Number Authentication'"
echo "   - Configure Twilio credentials"
echo "   - Set flow as Browser binding"
echo
echo "3. Set login theme (if using custom theme):"
echo "   - Go to Realm Settings → Themes"
echo "   - Set Login Theme to '$THEME_NAME'"
echo
echo -e "${BLUE}Configuration guide: See README.md for detailed instructions${NC}"

# Volitelně - start Keycloak
echo
read -p "Do you want to start Keycloak now? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Starting Keycloak...${NC}"
    cd "$KEYCLOAK_HOME"
    ./bin/kc.sh start --optimized &
    KC_PID=$!
    echo -e "${GREEN}Keycloak started with PID: $KC_PID${NC}"
    echo "Access admin console at: http://localhost:8080/admin"
    echo "To stop: kill $KC_PID"
fi