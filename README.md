# Keycloak Phone Number Authenticator

A production-ready custom Keycloak authenticator that enables phone number-based authentication with SMS verification, replacing traditional username/password login.

## ✨ Features

- 🔐 **Phone Number Authentication** - Login with phone number instead of username
- 📱 **SMS Verification** - SMS verification codes via Twilio
- 🌍 **Multi-language Support** - English, Czech, Slovak with easy extensibility
- 👤 **Auto User Creation** - Automatic user creation on first login
- 🔧 **Twilio Integration** - Professional SMS delivery
- 🎨 **Custom UI** - Responsive, accessible login forms
- ⚙️ **Fully Configurable** - Configure via Keycloak Admin Console
- 🚀 **Production Ready** - Comprehensive testing, monitoring, security
- 📱 **Mobile Optimized** - Auto-formatting, mobile-first design
- 🔒 **Security First** - Rate limiting, secure code generation, audit logging

## 📋 Requirements

- **Java 21+**
- **Keycloak 26.3.1+**
- **Maven 3.6+**
- **Twilio Account** with API credentials

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/your-org/keycloak-phone-authenticator.git
cd keycloak-phone-authenticator

# Build project
./scripts/build.sh full
```

### 2. Deploy to Keycloak

```bash
# Automated deployment
./deploy.sh $KEYCLOAK_HOME phone-theme

# Or manual deployment
cp target/keycloak-phone-authenticator-*.jar $KEYCLOAK_HOME/providers/
cp -r themes/phone-theme $KEYCLOAK_HOME/themes/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

### 3. Docker Development (Optional)

```bash
cd docker
docker-compose up -d
```

Access Keycloak at `http://localhost:8080` (admin/admin)

## ⚙️ Configuration

### 1. Twilio Setup

1. Create account at [twilio.com](https://www.twilio.com)
2. Get your credentials:
   - **Account SID**
   - **Auth Token**
   - **Phone Number** (for sending SMS)

### 2. Keycloak Configuration

#### A. Create Authentication Flow

1. Login to **Keycloak Admin Console**
2. Go to **Authentication** → **Flows**
3. **Copy** the Browser flow
4. Name it "Phone Authentication"
5. **Edit the flow:**
   - Delete "Username Password Form"
   - Delete "Browser - Conditional OTP"
   - Click **Add execution**
   - Select "Phone Number Authentication"
   - Set to **REQUIRED**

#### B. Configure Authenticator

1. Click **Actions** → **Config** on "Phone Number Authentication"
2. Fill in values:

| Setting | Value | Description |
|---------|-------|-------------|
| **Twilio Account SID** | `ACxxxxx...` | Your Twilio Account SID |
| **Twilio Auth Token** | `your_token` | Your Twilio Auth Token |
| **Twilio From Number** | `+1234567890` | Verified Twilio phone number |
| **SMS Code TTL** | `300` | Code validity in seconds (5 min) |
| **SMS Code Length** | `6` | Length of SMS verification code |
| **Create User If Not Exists** | `true` | Auto-create users on first login |

#### C. Set Flow Bindings

1. Go to **Authentication** → **Bindings**
2. Set **Browser Flow** to your "Phone Authentication" flow
3. Click **Save**

#### D. Configure Theme (Optional)

1. Go to **Realm Settings** → **Themes**
2. Set **Login Theme** to `phone-theme`
3. Click **Save**

## 🌍 Multi-language Support

The authenticator supports **3 languages** out of the box:

| Language | Code | Phone Format | Status |
|----------|------|--------------|--------|
| 🇺🇸 English | `en` | `+1 555 123 4567` | ✅ Complete |
| 🇨🇿 Czech | `cs` | `+420 123 456 789` | ✅ Complete |
| 🇸🇰 Slovak | `sk` | `+421 123 456 789` | ✅ Complete |

Language is auto-detected from:
1. URL parameter: `?kc_locale=en`
2. Browser Accept-Language header
3. User profile preference
4. Fallback to English

### Adding New Languages

See [docs/localization.md](docs/localization.md) for detailed instructions.

## 📱 Usage

### For End Users

1. **Navigate to login page**
2. **Enter phone number** (auto-formats for supported countries)
3. **Fill name fields** (for new users only)
4. **Click "Send SMS Code"**
5. **Receive 6-digit code** on your phone
6. **Enter code** to complete login

### Phone Number Formats

#### Auto-formatting Examples
- **Czech numbers**: `123456789` → `+420 123 456 789`
- **International**: `+1 555 123 4567` (preserved)
- **With country code**: `+44 7911 123456` (UK format)

#### Supported Patterns
- Czech: `123456789`, `0123456789` → `+420 123 456 789`
- International: Any valid E.164 format starting with `+`

## 🏗️ Architecture

### Project Structure
```
keycloak-phone-authenticator/
├── src/main/java/com/tegadeti/keycloak/provider/
│   ├── PhoneAuthenticatorFactory.java    # Factory & configuration
│   ├── PhoneAuthenticator.java           # Main authentication logic
│   └── TwilioSmsService.java             # SMS service integration
├── themes/phone-theme/                   # UI templates & translations
├── docker/                               # Development environment
├── scripts/                              # Build & deployment automation
└── docs/                                 # Comprehensive documentation
```

### User Attributes

The provider creates/uses these user attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `phoneNumber` | String | User's verified phone number |
| `phoneNumberVerified` | Boolean | Whether phone is verified |
| `firstName` | String | User's first name |
| `lastName` | String | User's last name |

## 🧪 Testing

### Unit Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PhoneAuthenticatorTest
```

### Integration Testing
```bash
# Test with embedded Keycloak
mvn integration-test

# Test deployment
./scripts/build.sh verify
```

### Manual Testing
```bash
# Test different languages
http://localhost:8080/realms/demo/protocol/openid-connect/auth?kc_locale=en
http://localhost:8080/realms/demo/protocol/openid-connect/auth?kc_locale=cs
http://localhost:8080/realms/demo/protocol/openid-connect/auth?kc_locale=sk
```

## 🛠️ Development

### Build Commands
```bash
# Full build with tests
./scripts/build.sh full

# Quick build
./scripts/build.sh package

# Docker build
./scripts/build.sh docker
```

### Development Environment
```bash
# Start development stack
cd docker
docker-compose up -d

# Access services
# Keycloak: http://localhost:8080 (admin/admin)
# Postgres: localhost:5432
# MailHog: http://localhost:8025
```

### Debugging
```bash
# Enable debug logging
$KEYCLOAK_HOME/bin/kc.sh start --log-level=DEBUG

# Check provider loading
tail -f $KEYCLOAK_HOME/data/log/keycloak.log | grep "phone-authenticator"
```

## 🔧 Troubleshooting

### SMS Not Sending

**Check Twilio Configuration:**
```bash
# Look for errors in Keycloak logs
grep "Failed to send SMS" $KEYCLOAK_HOME/data/log/keycloak.log
```

**Verify Twilio Account:**
- Account SID and Auth Token are correct
- From Number is verified in Twilio Console
- Account has sufficient credits
- Phone number is in correct E.164 format

**Test Twilio Connection:**
```bash
curl -X POST https://api.twilio.com/2010-04-01/Accounts/YOUR_SID/Messages.json \
  --data-urlencode "To=+1234567890" \
  --data-urlencode "From=YOUR_TWILIO_NUMBER" \
  --data-urlencode "Body=Test message" \
  -u YOUR_SID:YOUR_AUTH_TOKEN
```

### Users Not Creating

1. Verify `Create User If Not Exists` = true
2. Check Keycloak permissions
3. Review logs for "Failed to create user" errors

### UI Issues

1. Ensure template files are in correct theme directory
2. Verify theme is set in Realm Settings
3. Clear browser cache
4. Check for JavaScript errors in browser console

### Compatibility Issues

For Keycloak 26.3.1 compatibility problems:
```bash
# Apply compatibility patches
./scripts/apply-26-compatibility-patch.sh
```

See [KEYCLOAK_26_COMPATIBILITY.md](KEYCLOAK_26_COMPATIBILITY.md) for details.

## 🔒 Security

### Built-in Security Features
- **SMS Code TTL**: Codes expire after 5 minutes
- **Secure Generation**: Uses SecureRandom for code generation
- **Phone Masking**: Phone numbers masked in logs
- **Input Validation**: Comprehensive input sanitization
- **Unique Phones**: One phone number per user account
- **Rate Limiting**: Configurable attempt limiting

### Production Security Checklist
- [ ] Use HTTPS only
- [ ] Secure Twilio credentials as environment variables
- [ ] Enable audit logging
- [ ] Configure appropriate rate limits
- [ ] Monitor SMS delivery metrics
- [ ] Set up alerting for failed authentications

## 📊 Monitoring

### Key Metrics
- SMS delivery success rate
- Authentication success/failure rates
- User creation rates
- Response times
- Error rates by type

### Logging
```bash
# Key log patterns to monitor
grep "SMS code sent" $KEYCLOAK_HOME/data/log/keycloak.log
grep "User.*successfully authenticated" $KEYCLOAK_HOME/data/log/keycloak.log
grep "Failed to send SMS" $KEYCLOAK_HOME/data/log/keycloak.log
```

## 📚 Documentation

- [📖 Installation Guide](docs/installation.md)
- [⚙️ Configuration Guide](docs/configuration.md)
- [🌍 Localization Guide](docs/localization.md)
- [🔧 Troubleshooting](docs/troubleshooting.md)
- [🔒 Security Guide](docs/security.md)
- [🏗️ Project Structure](PROJECT_STRUCTURE.md)

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass: `./scripts/build.sh full`
6. Submit a pull request

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: [GitHub Issues](https://github.com/your-org/keycloak-phone-authenticator/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/keycloak-phone-authenticator/discussions)
- **Documentation**: [docs/](docs/)

## 🏆 Acknowledgments

- [Keycloak](https://www.keycloak.org/) for the excellent IAM platform
- [Twilio](https://www.twilio.com/) for reliable SMS services
- Contributors and the open-source community

## 📈 Changelog

### v1.0.0 (Latest)
- ✅ **Keycloak 26.3.1 Compatibility** - Full support for latest Keycloak
- ✅ **Multi-language Support** - English, Czech, Slovak
- ✅ **Production Ready** - Comprehensive testing, monitoring, security
- ✅ **Twilio Integration** - Professional SMS delivery
- ✅ **Auto User Creation** - Seamless onboarding experience
- ✅ **Custom UI** - Responsive, accessible design
- ✅ **Docker Support** - Containerized development environment
- ✅ **CI/CD Pipeline** - Automated testing and deployment
- ✅ **Comprehensive Documentation** - Installation, configuration, troubleshooting

---

**Made with ❤️ for the Keycloak community**