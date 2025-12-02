# Keycloak Phone Number Authenticator

[![CI](https://github.com/tegadeti/keycloak-phone-authenticator/actions/workflows/ci.yml/badge.svg)](https://github.com/tegadeti/keycloak-phone-authenticator/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/tegadeti/keycloak-phone-authenticator)](https://github.com/tegadeti/keycloak-phone-authenticator/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Custom Keycloak authenticator that enables phone number-based authentication with SMS verification via Twilio.

## Features

- **Phone Number Login** - Users login with phone number instead of username/password
- **SMS Verification** - 4-8 digit verification codes sent via Twilio
- **Auto User Creation** - Automatic user registration on first login
- **Rate Limiting** - Configurable limits per phone number and IP address
- **Development Mode** - Log SMS codes instead of sending (for testing)
- **Configurable SMS Templates** - Customize SMS message text
- **Multi-language Support** - English, Czech, Slovak
- **Custom Theme** - Responsive login forms with countdown timer

## Requirements

- Java 21+
- Keycloak 26.x
- Maven 3.6+
- Twilio Account (for production)

## Installation

### Option 1: Download from GitHub Releases (Recommended)

Download the latest release from [GitHub Releases](https://github.com/tegadeti/keycloak-phone-authenticator/releases).

**Available downloads:**

| File | Description |
|------|-------------|
| `keycloak-phone-authenticator-X.X.X.jar` | Provider JAR only |
| `phone-theme-X.X.X.tar.gz` | Login theme only |
| `keycloak-phone-authenticator-X.X.X-complete.tar.gz` | Complete package (JAR + theme + README) |

**Quick install with complete package:**

```bash
# Download latest release (replace X.X.X with actual version)
wget https://github.com/tegadeti/keycloak-phone-authenticator/releases/download/vX.X.X/keycloak-phone-authenticator-X.X.X-complete.tar.gz

# Extract
tar -xzf keycloak-phone-authenticator-X.X.X-complete.tar.gz
cd keycloak-phone-authenticator-X.X.X

# Copy to Keycloak
cp keycloak-phone-authenticator-X.X.X.jar $KEYCLOAK_HOME/providers/
cp -r phone-theme $KEYCLOAK_HOME/themes/

# Rebuild and start Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

### Option 2: Build from Source

```bash
git clone https://github.com/tegadeti/keycloak-phone-authenticator.git
cd keycloak-phone-authenticator

# Build JAR
./build.sh

# Run tests
./build.sh test

# Verify JAR structure
./build.sh verify
```

**Deploy to Keycloak:**

```bash
# Manual deployment
cp target/keycloak-phone-authenticator-1.0.0.jar $KEYCLOAK_HOME/providers/
cp -r themes/phone-theme $KEYCLOAK_HOME/themes/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start

# Or use build script
./build.sh deploy /opt/keycloak
```

### Option 3: Docker (Development)

```bash
./build.sh docker

# Access:
# - Keycloak: http://localhost:8080 (admin/admin)
# - MailHog: http://localhost:8025
```

## Keycloak Configuration

### Step 1: Create Authentication Flow

1. Login to **Keycloak Admin Console** (`http://localhost:8080/admin`)
2. Select your realm (or create new one)
3. Go to **Authentication** → **Flows**
4. Click **Create flow**:
   - Name: `Phone Authentication`
   - Type: `basic-flow`
5. Click **Add step** → **Add step**
6. Search for `Phone Number Authentication`
7. Click **Add**
8. Set requirement to **Required**

### Step 2: Configure Authenticator

1. In your flow, click ⚙️ (gear icon) on "Phone Number Authentication"
2. Configure settings:

| Setting | Description | Default |
|---------|-------------|---------|
| **Twilio Account SID** | Your Twilio Account SID | - |
| **Twilio Auth Token** | Your Twilio Auth Token (secret) | - |
| **Twilio From Number** | Verified Twilio phone number (E.164 format) | - |
| **SMS Code TTL** | Code validity in seconds | `300` (5 min) |
| **SMS Code Length** | Verification code length (4-8) | `6` |
| **Create User If Not Exists** | Auto-create users on first login | `true` |
| **SMS Message Template** | SMS text with `{code}` and `{ttl}` placeholders | `Váš ověřovací kód: {code}...` |
| **Rate Limit Per Phone** | Max SMS per phone per hour (0 = disabled) | `5` |
| **Rate Limit Per IP** | Max SMS per IP per hour (0 = disabled) | `20` |
| **Development Mode** | Log codes instead of sending SMS | `false` |

3. Click **Save**

### Step 3: Bind Flow to Browser

1. Go to **Authentication** → **Flows**
2. Find your `Phone Authentication` flow
3. Click **Action** → **Bind flow**
4. Select **Browser flow**
5. Click **Save**

### Step 4: Set Login Theme (Optional)

1. Go to **Realm Settings** → **Themes**
2. Set **Login theme** to `phone-theme`
3. Click **Save**

### Step 5: Test

1. Open a new browser/incognito window
2. Go to your realm's login page:
   ```
   http://localhost:8080/realms/YOUR_REALM/account
   ```
3. Enter phone number and name
4. Check Keycloak logs for SMS code (if Development Mode is ON)
5. Enter verification code

## Configuration Options

### Twilio Setup (Production)

1. Create account at [twilio.com](https://www.twilio.com)
2. Get credentials from Console:
   - **Account SID**: `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
   - **Auth Token**: Your auth token
   - **Phone Number**: Purchase a number (E.164 format: `+1234567890`)

### Development Mode

Enable **Development Mode** to skip Twilio and log codes to console:

```
INFO  [com.tegadeti.keycloak.provider.TwilioSmsService] === DEVELOPMENT MODE ===
INFO  [com.tegadeti.keycloak.provider.TwilioSmsService] SMS would be sent to: +420123456789
INFO  [com.tegadeti.keycloak.provider.TwilioSmsService] Verification code: 123456
INFO  [com.tegadeti.keycloak.provider.TwilioSmsService] ========================
```

### SMS Template

Customize the SMS message using placeholders:
- `{code}` - Verification code
- `{ttl}` - Time to live in minutes

Example:
```
Your verification code is: {code}. Valid for {ttl} minutes.
```

### Rate Limiting

Protect against abuse:
- **Per Phone**: Max SMS to same phone number per hour
- **Per IP**: Max SMS from same IP address per hour

Set to `0` to disable.

## Project Structure

```
keycloak-phone-authenticator/
├── src/main/java/com/tegadeti/keycloak/provider/
│   ├── PhoneAuthenticator.java         # Main authentication logic
│   ├── PhoneAuthenticatorFactory.java  # Factory & configuration
│   ├── TwilioSmsService.java           # SMS sending service
│   └── RateLimitService.java           # Rate limiting
├── src/main/resources/META-INF/services/
│   └── org.keycloak.authentication.AuthenticatorFactory
├── src/test/java/                      # Unit tests
├── themes/phone-theme/                 # Keycloak theme
│   ├── theme.properties
│   └── login/
│       ├── phone-input.ftl             # Phone input form
│       ├── phone-code-verification.ftl # Code verification form
│       ├── messages/                   # Translations (en, cs, sk)
│       └── resources/css/              # Styles
├── docker/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── keycloak/realm-export.json      # Test realm configuration
├── build.sh                            # Build script
├── pom.xml
└── README.md
```

## Build Commands

```bash
./build.sh              # Build JAR (default)
./build.sh build        # Build JAR
./build.sh test         # Run unit tests
./build.sh verify       # Verify JAR structure
./build.sh docker       # Build & start Docker environment
./build.sh deploy PATH  # Deploy to Keycloak at PATH
./build.sh clean        # Clean build artifacts
./build.sh help         # Show help
```

## Authentication Flow

```
┌─────────────────┐
│   User visits   │
│   login page    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Phone Input    │
│  Form (Step 1)  │
│  - Phone number │
│  - First name   │
│  - Last name    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Rate Limit     │
│  Check          │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Generate &     │
│  Send SMS Code  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Code Verify    │
│  Form (Step 2)  │
│  - 6-digit code │
│  - Countdown    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Verify Code    │
│  & TTL Check    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Find/Create    │
│  User           │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Login Success  │
└─────────────────┘
```

## User Attributes

The authenticator creates these user attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `phoneNumber` | String | User's phone number (E.164 format) |
| `phoneNumberVerified` | String | `"true"` after verification |

Username is set to the phone number.

## Troubleshooting

### Provider not visible in Keycloak

```bash
# Check if JAR is in providers directory
ls $KEYCLOAK_HOME/providers/keycloak-phone-authenticator*.jar

# Check JAR contains SPI file
jar tf target/keycloak-phone-authenticator-1.0.0.jar | grep AuthenticatorFactory

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### SMS not sending

1. Check Twilio credentials are correct
2. Verify phone number format (must be E.164: `+1234567890`)
3. Check Keycloak logs for errors:
   ```bash
   grep -i "twilio\|sms" $KEYCLOAK_HOME/data/log/keycloak.log
   ```
4. Enable Development Mode to test without Twilio

### Rate limit errors

Users see "Too many requests" message:
- Wait for rate limit window to expire (1 hour)
- Or restart Keycloak to clear in-memory rate limits

### Theme not showing

1. Verify theme is copied to `$KEYCLOAK_HOME/themes/phone-theme`
2. Set theme in Realm Settings → Themes → Login theme
3. Clear browser cache

## Security Considerations

- **SMS codes** are generated using `SecureRandom`
- **Phone numbers** are masked in logs (`+420****5678`)
- **Rate limiting** prevents brute-force attacks
- **TTL** ensures codes expire (default 5 minutes)
- **Twilio credentials** should be stored securely (use env vars in production)

## Testing

```bash
# Run all tests (60 tests)
./build.sh test

# Tests include:
# - RateLimitServiceTest (19 tests)
# - PhoneAuthenticatorFactoryTest (20 tests)
# - PhoneAuthenticatorTest (21 tests)
```

## License

Apache License 2.0

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request
