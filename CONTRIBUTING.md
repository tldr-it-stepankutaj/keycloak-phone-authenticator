# Contributing

Thanks for your interest in contributing to Keycloak Phone Authenticator!

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.6+
- Docker and Docker Compose (optional, for local testing)

### Build

```bash
# Build the JAR
./build.sh build

# Run tests
./build.sh test

# Verify JAR structure
./build.sh verify

# Start local Keycloak with Docker
./build.sh docker
```

### Local Development

1. Fork and clone the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Run tests: `mvn test`
5. Verify the build: `./build.sh verify`
6. Commit with a clear message
7. Push and open a Pull Request

### Project Structure

```
src/main/java/          Java source code
src/test/java/          Unit tests
themes/phone-theme/     Keycloak login theme (FreeMarker + CSS)
docker/                 Docker development environment
```

### Code Style

- Java 21 features are welcome
- Use FQCN imports (no wildcard imports)
- All comments in English
- Mask sensitive data (phone numbers, IPs) in log messages
- Write unit tests for new functionality

### Adding a New SMS Provider

1. Create a new service class implementing SMS sending logic
2. Add configuration properties in `PhoneAuthenticatorFactory`
3. Update `PhoneAuthenticator` to use the new provider
4. Add unit tests
5. Update README.md with configuration instructions

### Translations

Message files are in `themes/phone-theme/login/messages/`:
- `messages_en.properties` — English
- `messages_cs.properties` — Czech
- `messages_sk.properties` — Slovak

To add a new language, create `messages_XX.properties` and add the locale to `theme.properties`.

## Pull Request Process

1. Ensure all tests pass
2. Update documentation if needed
3. The PR will be reviewed by the maintainer
4. After approval, the PR will be merged

## Reporting Issues

- Use the bug report template for bugs
- Use the feature request template for new ideas
- Check existing issues before creating a new one

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
