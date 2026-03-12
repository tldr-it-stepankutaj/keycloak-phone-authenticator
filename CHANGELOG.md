# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
