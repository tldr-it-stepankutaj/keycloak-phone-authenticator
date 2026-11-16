# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
## [1.0.4] - 2026-11-16
### Security
- Limit of 5 attempts per SMS code; the code is single use and compared in constant time
- Disabled users and users locked by Keycloak brute force detection can no longer log in; failed codes feed brute force detection
- `X-Forwarded-For` / `X-Real-IP` are no longer trusted (the per-IP rate limit could be bypassed by spoofing them)
- Rate limit counters are stored in Keycloak's shared cache (cluster-wide), expired entries are cleaned up (the in-memory map used to grow forever)
- No account enumeration: first/last name are asked only after the number was verified by the code
- Two accounts with the same phone number are refused instead of picking a random one

### Fixed
- Expired code left the user stuck: added "Request new code" (60 s cooldown) and "Change phone number"
- A missing code timestamp is treated as expired (the check used to be skipped)
- Twilio credentials were set through the global `Twilio.init()` and could leak between realms / race; now one client per credentials with timeouts
- Concurrent first logins of the same number no longer fail/duplicate the account
- Rate limit value `0` now really disables the limit (it used to fall back to the default)
- Countdown in the code form uses the configured TTL instead of a hardcoded 5 minutes
- Slovak numbers without prefix were prefixed with `+420`

### Added
- Pluggable SMS providers (`SMS Provider` setting): Twilio (default, unchanged behavior) and a generic HTTP gateway (`POST {"to","message"}` + optional bearer token); `SmsProvider` interface for adding more
- Second factor mode: placed after a step that identifies the user, the code is sent to the user's `phoneNumber` attribute (no number input, no account creation); `configuredFor` reports users without a number as not configured
- libphonenumber based normalization (country specific trunk prefixes) and `Strict Phone Validation` setting
- `Max Code Attempts` and `Resend Cooldown (seconds)` settings (defaults 5 and 60)
- `examples/user-profile.json`: User Profile with admin-only `phoneNumber` / `phoneNumberVerified` and optional email
- Audit events: `LOGIN_ERROR` with `sms_rate_limited`, `sms_send_failed`, `too_many_code_attempts` and `REGISTER` for accounts created by the authenticator (details: `auth_method`, masked `phone_number`, `mode`)
- Twilio / HTTP gateway tokens can be vault expressions (`${vault.my_token}`)
- `Default Calling Code` setting (replaces hardcoded `+420`); `00` prefix is understood
- Error messages are theme message keys (cs/sk/en) instead of hardcoded English
- Theme template `phone-user-details.ftl` (name step for new users)
- Flow tests with mocked Keycloak SPI

### Changed
- Dependencies updated: Keycloak 26.8.2 (also the Docker image), Twilio SDK 13.2.0, libphonenumber 9.0.43, Jackson 2.23.0, JBoss Logging 3.6.3, JUnit 6.1.3, Mockito 5.24.0, maven-compiler-plugin 3.16.0
- **Theme was unusable:** `theme.properties` sat in `themes/phone-theme/` instead of the type directory `themes/phone-theme/login/`, so the theme had no parent and no `template.ftl` and every login page failed with HTTP 500. Moved, parent is now `keycloak.v2`, own CSS is appended to the parent's styles
- **Docker test realm did not import** (Keycloak 26.8.2 failed with `flow is null`): `browserFlow` pointed at an ID instead of the alias and the realm listed only the two custom flows, without the built-in ones. Rebuilt from a real Keycloak export, the authenticator config is linked to the flow executions and the realm now carries the example User Profile (otherwise every phone-only login ended on the "Update account information" screen asking for an e-mail)
- Docker: `KC_HEALTH_ENABLED` is also set at runtime (`start-dev` re-runs the build and dropped the health endpoint, so the healthcheck could never pass)
- Removed `config.example.properties`: nothing ever read it (all settings are entered in the Keycloak admin console, see README)
- `build.sh verify` checks the current class names (it still looked for the removed `TwilioSmsService`); the CI JAR check now fails on missing classes / SPI file / bundled Jackson instead of only printing the contents
- Docker: the test realm now links the authenticator config to the flow executions (the config, including development mode, was never applied); the Keycloak healthcheck uses the management port 9000 without curl (the old `curl localhost:8080/health/ready` cannot work on Keycloak 25+)
- Release notes describe the providers and second factor mode
- Build: shade merges `META-INF/services`, drops `module-info`/`DEPENDENCIES` of bundled libraries and no longer writes `dependency-reduced-pom.xml`; surefire pinned (3.6.1) with Mockito as an explicit java agent and the JBoss log manager
- Rate limit resets and the duplicate "limit exceeded" warning are logged at DEBUG (the authenticator already logs the exceeded limit with phone, IP and type)
- A lost registration race is logged as a one-line warning instead of a stack trace
- Verification codes are no longer kept in plain text in the auth session (salted SHA-256)
- Development mode logs only the masked phone number (the code is still logged)
- Jackson (core, annotations, databind, jsr310) is declared `provided` through the `jackson-bom` and no longer shaded into the jar (Keycloak provides it); mockito versions aligned
- `TwilioSmsService` replaced by `SmsService` + `TwilioSmsProvider` (log category in development mode is now `SmsService`)
- Name fields moved from the phone form to a separate step shown only to new users after code verification

## [1.0.3] - 2026-06-12
### Changed
- SMS verification via Twilio API
- Development mode (logs codes instead of sending SMS)

## [1.0.2] - 2026-04-12
### Changed
- build.sh automation script
- Multi-language support: Czech, English, Slovak

## [1.0.0] - 2026-03-12
### Added
- Phone number authentication flow for Keycloak 26.x
- SMS verification via Twilio API
- Automatic user creation on first login
- Rate limiting per phone number and per IP address
- Development mode (logs codes instead of sending SMS)
- Customizable SMS message templates with {code} and {ttl} placeholders
- Custom responsive login theme (phone-theme)
- Multi-language support: Czech, English, Slovak
- Docker Compose development environment (Keycloak + PostgreSQL + MailHog)
- GitHub Actions CI pipeline (build, test, verify JAR structure)
- GitHub Actions release pipeline (creates GitHub releases with artifacts)
- Comprehensive unit tests (60 tests)
- build.sh automation script
