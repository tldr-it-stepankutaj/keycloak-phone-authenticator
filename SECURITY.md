# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please contact the maintainer directly:

- GitHub: [@tldr-it-stepankutaj](https://github.com/tldr-it-stepankutaj)

## What to include

When reporting a vulnerability, please include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response

You can expect an initial response within 72 hours. The maintainer will work with you to understand and address the issue before any public disclosure.

## Scope

This policy applies to the Keycloak Phone Authenticator plugin code, themes, and configuration templates. It does not cover the Keycloak server itself or Twilio infrastructure.

## Security Features

This project implements the following security measures:

- Phone numbers are masked in all log outputs
- IP addresses are masked in all log outputs
- SMS codes use `SecureRandom` for cryptographic randomness
- Rate limiting per phone number and per IP address
- Configurable code TTL (time-to-live)
- Development mode keeps secrets out of production
- No credentials stored in source code
- Twilio Auth Token marked as secret in Keycloak admin UI
