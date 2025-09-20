# Keycloak Phone Number Authenticator

Custom Keycloak authenticator, který umožňuje přihlášení pomocí telefonního čísla a SMS ověření místo tradičního username/password přihlášení.

## Funkce

- 🔐 **Phone Number Authentication** - Přihlášení pomocí telefonního čísla
- 📱 **SMS Verification** - Ověřovací kód poslaný přes SMS
- 🔧 **Twilio Integration** - Integrace s Twilio pro odesílání SMS
- 👤 **Auto User Creation** - Automatické vytváření uživatelů při prvním přihlášení
- 🇨🇿 **Czech Phone Numbers** - Automatické formátování českých čísel (+420)
- 🎨 **Custom UI** - Přizpůsobené přihlašovací formuláře
- ⚙️ **Configurable** - Plně konfigurovatelné v Keycloak admin konzoli

## Požadavky

- Java 21+
- Keycloak 26.3.1+
- Maven 3.6+
- Twilio účet s API credentials

## Build a Installation

### 1. Build projektu

```bash
git clone <repository-url>
cd keycloak-phone-authenticator
mvn clean package
```

### 2. Deploy do Keycloak

```bash
# Zkopírovat JAR do providers složky
cp target/keycloak-phone-authenticator-1.0.0.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak s novým providerem
$KEYCLOAK_HOME/bin/kc.sh build

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

### 3. Zkopírovat template soubory

```bash
# Vytvořit custom theme nebo použít existující
mkdir -p $KEYCLOAK_HOME/themes/your-theme/login

# Zkopírovat template soubory
cp phone-input.ftl $KEYCLOAK_HOME/themes/your-theme/login/
cp phone-code-verification.ftl $KEYCLOAK_HOME/themes/your-theme/login/
```

## Konfigurace

### 1. Twilio Setup

1. Vytvořte Twilio účet na [twilio.com](https://www.twilio.com)
2. Získejte:
    - Account SID
    - Auth Token
    - Phone Number (pro odesílání SMS)

### 2. Keycloak Configuration

#### A. Vytvoření Authentication Flow

1. Přihlaste se do Keycloak Admin konzole
2. Jděte na **Authentication** → **Flows**
3. Klikněte **Copy** u Browser flow
4. Pojmenujte nový flow např. "Phone Authentication"
5. **Upravte flow:**
    - Smažte "Username Password Form"
    - Smažte "Browser - Conditional OTP"
    - Klikněte **Add execution**
    - Vyberte "Phone Number Authentication"
    - Nastavte na **REQUIRED**

#### B. Konfigurace Authenticatoru

1. U "Phone Number Authentication" klikněte **Actions** → **Config**
2. Vyplňte hodnoty:
    - **Twilio Account SID**: Váš Twilio Account SID
    - **Twilio Auth Token**: Váš Twilio Auth Token
    - **Twilio From Number**: Telefonní číslo pro odesílání (formát: +1234567890)
    - **SMS Code TTL**: 300 (5 minut)
    - **SMS Code Length**: 6
    - **Create User If Not Exists**: true

#### C. Nastavení Flow Bindings

1. Jděte na **Authentication** → **Bindings**
2. U **Browser Flow** vyberte váš nový "Phone Authentication" flow
3. Klikněte **Save**

#### D. User Profile Configuration (volitelné)

Pro skrytí username/email polí:

1. Jděte na **Realm Settings** → **User Profile**
2. **Username** a **Email** nastavte:
    - **Required for**: Admin only
    - **Enabled when**: Admin only

### 3. Theme Configuration

Pokud používáte custom theme:

1. V **Realm Settings** → **Themes**
2. Nastavte **Login Theme** na váš custom theme
3. Klikněte **Save**

## Použití

### Pro koncové uživatele

1. Uživatel jde na přihlašovací stránku
2. Zadá telefonní číslo (automaticky se formátuje pro CZ čísla)
3. Pro nové uživatele vyplní jméno a příjmení
4. Klikne "Odeslat SMS kód"
5. Na telefon obdrží 6místný kód
6. Zadá kód pro dokončení přihlášení

### Phone Number Format

- **České čísla**: Automaticky se převedou na +420 formát
    - `123 456 789` → `+420 123 456 789`
    - `0123 456 789` → `+420 123 456 789`
- **Mezinárodní čísla**: Musí začínat +
    - `+1 555 123 4567` (USA)
    - `+44 7911 123456` (UK)

## API Endpoints

Provider nepřidává žádné custom API endpoints. Veškerá komunikace probíhá přes standardní Keycloak authentication flow.

## User Attributes

Provider automaticky vytváří/používá tyto user atributy:

- `phoneNumber` - Telefonní číslo uživatele
- `phoneNumberVerified` - Boolean, zda je telefon ověřený

## Troubleshooting

### SMS se neodesilají

1. **Zkontrolujte Twilio konfiguraci:**
   ```bash
   # V Keycloak logu hledejte chyby typu:
   # "Failed to send SMS" nebo "Twilio configuration error"
   ```

2. **Zkontrolujte Twilio Account:**
    - Account SID a Auth Token jsou správně
    - From Number je ověřený v Twilio konzoli
    - Máte dostatečný kredit

3. **Test Twilio připojení:**
   ```bash
   curl -X POST https://api.twilio.com/2010-04-01/Accounts/YOUR_SID/Messages.json \
   --data-urlencode "To=+420123456789" \
   --data-urlencode "From=YOUR_TWILIO_NUMBER" \
   --data-urlencode "Body=Test message" \
   -u YOUR_SID:YOUR_AUTH_TOKEN
   ```

### Uživatelé se nevytvářejí

1. Zkontrolujte, že máte nastaven `Create User If Not Exists` = true
2. Zkontrolujte permissions v Keycloak
3. Zkontrolujte logy pro chyby typu "Failed to create user"

### UI problémy

1. Zkontrolujte, že template soubory jsou ve správné složce
2. Zkontrolujte, že theme je správně nastaven v Realm Settings
3. Vymažte browser cache

## Vývoj

### Testování lokálně

```bash
# Build a test
mvn clean package

# Spuštění s embedded Keycloak (pokud máte test setup)
mvn test
```

### Logování

Pro debug logování přidejte do Keycloak konfigurace:

```bash
$KEYCLOAK_HOME/bin/kc.sh start --log-level=DEBUG --log-console-color=true
```

Nebo v `conf/keycloak.conf`:
```
log-level=DEBUG
log=console,file
```

## Security Considerations

- SMS kódy jsou platné pouze 5 minut
- Kódy jsou generovány pomocí SecureRandom
- Telefonní čísla jsou maskována v logu
- Unikátnost telefonních čísel je vynucována

## Licence

MIT License - viz LICENSE soubor

## Podpora

Pro problémy a feature requests použijte GitHub Issues.

## Changelog

### v1.0.0
- Základní phone number authentication
- Twilio SMS integrace
- Automatické vytváření uživatelů
- České telefonní čísla support
- Custom UI templates