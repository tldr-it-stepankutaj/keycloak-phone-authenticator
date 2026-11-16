# Keycloak Phone Number Authenticator

[![CI](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/actions/workflows/ci.yml/badge.svg)](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/actions/workflows/ci.yml)
[![Release](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/actions/workflows/release.yml/badge.svg)](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/actions/workflows/release.yml)
![Java](https://img.shields.io/badge/Java-21+-blue?logo=openjdk)
![Keycloak](https://img.shields.io/badge/Keycloak-26.x-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-green)
[![Release](https://img.shields.io/github/v/release/tldr-it-stepankutaj/keycloak-phone-authenticator)](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/releases)

Custom Keycloak authenticator that enables phone number-based authentication with SMS verification. SMS are delivered through Twilio or a generic HTTP gateway, and the authenticator works as a first factor (phone number as the login) or as a second factor after username/password.

## Features

- **Phone Number Login** - Users login with phone number instead of username/password
- **SMS Verification** - 4-8 digit verification codes, delivered via a pluggable SMS provider (Twilio or a generic HTTP gateway)
- **First or second factor** - phone number as the login, or as a second step after username/password
- **Phone number validation** - libphonenumber based normalization and optional strict validation
- **Auto User Creation** - Automatic user registration on first login
- **Rate Limiting** - Configurable limits per phone number and IP address
- **Development Mode** - Log SMS codes instead of sending (for testing)
- **Configurable SMS Templates** - Customize SMS message text
- **Multi-language Support** - English, Czech, Slovak
- **Custom Theme** - Responsive login forms with countdown timer

## Requirements

- Java 21+
- Keycloak 26.x (built and tested against 26.8.2)
- Maven 3.6+ (only to build from source)
- An SMS channel for production: a Twilio account **or** an HTTP SMS gateway (see [SMS Providers](#sms-providers))

## Installation

### Option 1: Download from GitHub Releases (Recommended)

Download the latest release from [GitHub Releases](https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/releases).

**Available downloads:**

| File | Description |
|------|-------------|
| `keycloak-phone-authenticator-X.X.X.jar` | Provider JAR only |
| `phone-theme-X.X.X.tar.gz` | Login theme only |
| `keycloak-phone-authenticator-X.X.X-complete.tar.gz` | Complete package (JAR + theme + examples + README) |

**Quick install with complete package:**

```bash
# Download latest release (replace X.X.X with actual version)
wget https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator/releases/download/vX.X.X/keycloak-phone-authenticator-X.X.X-complete.tar.gz

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
git clone https://github.com/tldr-it-stepankutaj/keycloak-phone-authenticator.git
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
cp target/keycloak-phone-authenticator-*.jar $KEYCLOAK_HOME/providers/
cp -r themes/phone-theme $KEYCLOAK_HOME/themes/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start

# Or use build script
./build.sh deploy /opt/keycloak
```

### Option 3: Docker (Development)

```bash
./build.sh docker
```

Starts Keycloak (with the provider and theme baked into the image), PostgreSQL and MailHog (not used by phone
login) and imports the test realm `phone-auth`:

- Admin console: <http://localhost:8080> (`admin` / `admin`)
- The realm uses `phone-theme`, binds the *Browser with Phone* flow and runs in **Development Mode**: SMS are not
  sent, the code is printed in the Keycloak log
  (`docker compose -f docker/docker-compose.yml logs -f keycloak`)
- Try it: <http://localhost:8080/realms/phone-auth/protocol/openid-connect/auth?client_id=demo-app&redirect_uri=http://localhost:3000/cb&response_type=code&scope=openid>
  (the redirect to the non-existent `localhost:3000` at the end means the login worked)
- Test user `+420123456789`; any other valid number registers a new account
- The realm applies [`examples/user-profile.json`](examples/user-profile.json), so phone-only accounts are not asked
  for an e-mail after logging in

PostgreSQL is published on port `5432`; if you run a local PostgreSQL, remove the `ports` entry of the `postgres`
service in `docker/docker-compose.yml` (Keycloak reaches it through the Docker network anyway).

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
| **SMS Provider** | `twilio` or `http` (see [SMS Providers](#sms-providers)) | `twilio` |
| **Twilio Account SID** | Your Twilio Account SID (provider `twilio`) | - |
| **Twilio Auth Token** | Your Twilio Auth Token (secret, may be a `${vault.…}` expression) | - |
| **Twilio From Number** | Verified Twilio phone number (E.164 format) | - |
| **HTTP SMS Gateway URL** / **Bearer Token** | Gateway endpoint and optional token (provider `http`) | - |
| **SMS Code TTL** | Code validity in seconds | `300` (5 min) |
| **SMS Code Length** | Verification code length (4-8) | `6` |
| **Max Code Attempts** | Wrong codes accepted before the code is invalidated | `5` |
| **Resend Cooldown (seconds)** | Minimum time between two codes | `60` |
| **SMS Message Template** | SMS text with `{code}` and `{ttl}` placeholders | `Your verification code: {code}...` |
| **Default Calling Code** | Calling code (digits, no `+`) added to numbers entered without a prefix, e.g. `420` (CZ), `421` (SK) | `420` |
| **Strict Phone Validation** | Require numbers valid per libphonenumber (reject test numbers like `+420123456789`) | `false` |
| **Create User If Not Exists** | Auto-create users on first login | `true` |
| **Rate Limit Per Phone** | Max SMS per phone per hour (0 = disabled) | `5` |
| **Rate Limit Per IP** | Max SMS per IP per hour (0 = disabled) | `20` |
| **Development Mode** | Log codes instead of sending SMS. **Never enable in production**, the codes end up in the log | `false` |

3. Click **Save**

### Step 3: Bind Flow to Browser

1. Go to **Authentication** → **Flows**
2. Find your `Phone Authentication` flow
3. Click **Action** → **Bind flow**
4. Select **Browser flow**
5. Click **Save**

To use the phone number as a **second factor** instead, add the step *after* a username/password step, see
[Second Factor Mode](#second-factor-mode).

### Step 4: Set Login Theme

1. Go to **Realm Settings** → **Themes**
2. Set **Login theme** to `phone-theme`
3. Click **Save**

The theme is required: the authenticator reports errors as message keys (translated in `phone-theme` to
English, Czech and Slovak), and the code form needs its templates. With another theme users would see raw keys.

### Step 5: Test

1. Open a new browser/incognito window
2. Go to your realm's login page:
   ```
   http://localhost:8080/realms/YOUR_REALM/account
   ```
3. Enter the phone number
4. Check Keycloak logs for the SMS code (if Development Mode is ON)
5. Enter the verification code
6. A new number additionally asks for first and last name (only after the code was verified)

## Configuration Options

### Twilio Setup (provider `twilio`)

1. Create account at [twilio.com](https://www.twilio.com)
2. Get credentials from Console:
   - **Account SID**: `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
   - **Auth Token**: Your auth token
   - **Phone Number**: Purchase a number (E.164 format: `+1234567890`)

### SMS Providers

Select the provider with the **SMS Provider** option.

| Provider | Description |
|----------|-------------|
| `twilio` (default) | Twilio Programmable Messaging. Needs Account SID, Auth Token and From Number. |
| `http` | Generic HTTP gateway: `POST <url>` with `{"to": "+420601123456", "message": "..."}`, optional `Authorization: Bearer <token>`. Any `2xx` response means *accepted*; redirects are not followed. Use it for a local SMS gateway or a small adapter in front of any other provider. |

Adding another provider means implementing `SmsProvider` (one `send` method) and registering it in
`SmsService`. Twilio Verify is not supported: it generates and checks the codes itself, whereas this extension
generates its own codes.

Tokens (**Twilio Auth Token**, **HTTP SMS Gateway Bearer Token**) may be given as a Keycloak vault expression,
e.g. `${vault.twilio_auth_token}`, so the secret does not have to be stored in the realm database.

### Phone Number Handling

Numbers are normalized to E.164 with [libphonenumber](https://github.com/google/libphonenumber).
Input without an international prefix is read as a national number of the **Default Calling Code**'s country
(trunk prefixes are handled per country, e.g. the leading `0` of UK numbers is dropped, the `0` of Italian
numbers is kept). `+` and `00` prefixes are always taken as international. Anything libphonenumber does not
consider valid falls back to simple rules (9 digits, or `0` + 9 digits, get the default calling code), so test
numbers keep working.

Enable **Strict Phone Validation** in production to reject numbers that are well-formed but not real.

### Second Factor Mode

Place the authenticator **after** a step that identifies the user (e.g. *Username Password Form*) and set it to
`REQUIRED`. The phone number is then not asked for: the code is sent to the `phoneNumber` attribute of the
already identified user, the number cannot be changed and accounts are never created. Users without a (valid)
`phoneNumber` fail the step with a setup-required error (with `ALTERNATIVE` the step is skipped for them).

As a first factor (no earlier step) it behaves as described in [Authentication Flow](#authentication-flow).

### Development Mode

Enable **Development Mode** to skip the SMS provider and log codes to the console (for local testing only):

```
WARN  [com.tegadeti.keycloak.provider.SmsService] === DEVELOPMENT MODE: SMS is NOT sent ===
WARN  [com.tegadeti.keycloak.provider.SmsService] SMS would be sent to: +420123456789
WARN  [com.tegadeti.keycloak.provider.SmsService] Verification code: 123456
WARN  [com.tegadeti.keycloak.provider.SmsService] =========================================
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

Counters are stored in Keycloak's shared cache (`SingleUseObjectProvider`), so the limits apply across all
cluster nodes and are not lost on a rolling restart (best effort: concurrent requests on different nodes
may slightly exceed the limit).

**Behind a reverse proxy** start Keycloak with `--proxy-headers=xforwarded` (or `forwarded`) so that the real
client address is used for the per-IP limit. The authenticator deliberately ignores `X-Forwarded-For` /
`X-Real-IP` itself, because any client can set them.

## Authentication Flow

```
 Phone Input (Step 1)            only the phone number - nothing about the account is revealed
        │
        ▼
 Rate limit (phone + IP) → generate & send SMS code
        │
        ▼
 Code Verification (Step 2) ◄─── "Request new code" (cooldown, new code, attempts reset)
   - limited attempts (default 5), then the code is invalidated and the user returns to Step 1
   - TTL check, constant-time comparison, code is single use
   - "Change phone number" returns to Step 1
        │
        ├── existing user ──► disabled / brute-force locked? ──► rejected
        │                     otherwise ──► Login success
        │
        └── unknown number ──► (creation disabled ──► error)
                               Step 3: first / last name (only after the number was verified)
                               ──► create user ──► Login success
```

In [second factor mode](#second-factor-mode) Step 1 and Step 3 are skipped: the code goes to the stored number of the
already identified user.

## Project Structure

```
keycloak-phone-authenticator/
├── src/main/java/com/tegadeti/keycloak/provider/
│   ├── PhoneAuthenticator.java         # Main authentication logic
│   ├── PhoneAuthenticatorFactory.java  # Factory & configuration
│   ├── PhoneNumberUtils.java           # Normalization / validation (libphonenumber)
│   ├── SmsService.java                 # Message template + provider selection
│   ├── SmsProvider.java                # Provider interface
│   ├── TwilioSmsProvider.java          # Twilio provider
│   ├── HttpSmsProvider.java            # Generic HTTP gateway provider
│   └── RateLimitService.java           # Rate limiting
├── src/main/resources/META-INF/services/
│   └── org.keycloak.authentication.AuthenticatorFactory
├── src/test/java/                      # Unit tests
├── themes/phone-theme/                 # Keycloak theme
│   └── login/
│       ├── theme.properties            # must be here (theme type directory), parent keycloak.v2
│       ├── phone-input.ftl             # Phone input form
│       ├── phone-code-verification.ftl # Code verification form
│       ├── phone-user-details.ftl      # Name form for new users
│       ├── messages/                   # Translations (en, cs, sk)
│       └── resources/css/              # Styles
├── docker/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── keycloak/realm-export.json      # Test realm configuration
├── examples/user-profile.json          # User Profile config (phone attributes admin-only)
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

## User Attributes

The authenticator creates these user attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `phoneNumber` | String | User's phone number (E.164 format) |
| `phoneNumberVerified` | String | `"true"` after verification |

Username is set to the phone number.

**Protect the `phoneNumber` attribute.** Whoever controls the attribute controls which account a number logs
into, and as a second factor it decides where the code goes. In the realm's *User profile* make `phoneNumber`
and `phoneNumberVerified` editable by **admin only** (not by the user), otherwise a user could claim somebody
else's number. If two accounts ever share a number the authenticator refuses to log in (it never picks one at
random).

[`examples/user-profile.json`](examples/user-profile.json) is a ready-to-use User Profile: the two attributes are
admin-editable only (`phoneNumber` is also validated as E.164) and `email` is not required for users, so
phone-only accounts are not pushed to an *Update profile* screen. Apply it in **Realm settings → User profile →
JSON editor** (it replaces the whole User Profile, so merge it with your own custom attributes first if you have any).

## Troubleshooting

### Provider not visible in Keycloak

```bash
# Check if JAR is in providers directory
ls $KEYCLOAK_HOME/providers/keycloak-phone-authenticator*.jar

# Check JAR contains SPI file
jar tf target/keycloak-phone-authenticator-*.jar | grep AuthenticatorFactory

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### SMS not sending

1. Check the **SMS Provider** setting and its credentials (Twilio SID/token/from number, or gateway URL/token)
2. Verify phone number format (must be E.164: `+1234567890`)
3. Check Keycloak logs for errors:
   ```bash
   grep -i "twilio\|sms" $KEYCLOAK_HOME/data/log/keycloak.log
   ```
4. Enable Development Mode to test without a provider
5. With realm events enabled, failures show up as `LOGIN_ERROR` events (`sms_send_failed`, `sms_rate_limited`)

### Rate limit errors

Users see a "Too many requests" message:
- Wait for the rate limit window to expire (1 hour); the message shows how long
- The counters live in Keycloak's cache, so a restart does not necessarily clear them
- Behind a reverse proxy make sure `--proxy-headers` is set, otherwise all users share the proxy's IP and hit the per-IP limit together

### Theme not showing

1. Verify theme is copied to `$KEYCLOAK_HOME/themes/phone-theme`
2. Set theme in Realm Settings → Themes → Login theme
3. Clear browser cache
4. Raw message keys on the page (e.g. `invalidPhoneNumber`) mean the realm does not use `phone-theme`

## Security Considerations

- **SMS codes** are generated using `SecureRandom`
- **Phone numbers** are masked in logs (`+420****5678`); live codes are kept only as salted hashes in the auth session
- **Audit events** (when realm events are enabled): `LOGIN_ERROR` with `sms_rate_limited`, `sms_send_failed`, `too_many_code_attempts`, and `REGISTER` for new accounts
- **Rate limiting** limits SMS sending per phone and per IP (shared across cluster nodes)
- **Code attempts** are limited (default 5), the code is single use and compared in constant time
- **Disabled users** and users locked by Keycloak's brute force detection cannot log in
- **No account enumeration** - the phone step looks the same for known and unknown numbers
- **Twilio clients** are created per credentials with connect/response timeouts (no global `Twilio.init`)
- **TTL** ensures codes expire (default 5 minutes)
- **Client IP** is taken from the connection only; `X-Forwarded-For` / `X-Real-IP` are ignored (configure `--proxy-headers` instead)
- **Provider secrets** (Twilio token, gateway token) should be vault expressions, not plain values in the realm
- **Development Mode** logs the codes, never use it in production

## Testing

```bash
# Run all tests
./build.sh test

# Tests include:
# - PhoneAuthenticatorFlowTest (authentication flow against mocked Keycloak SPI)
# - PhoneNumberUtilsTest (normalization / validation / masking)
# - RateLimitServiceTest
# - SmsServiceTest / HttpSmsProviderTest (provider selection, template, HTTP gateway)
# - PhoneAuthenticatorFactoryTest
# - PhoneAuthenticatorTest
```

## License

Apache License 2.0

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request
