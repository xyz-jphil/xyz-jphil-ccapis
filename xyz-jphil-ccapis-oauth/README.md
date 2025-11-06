# xyz-jphil-ccapis-oauth

OAuth 2.0 client module for Claude Compatible API (CCAPI) with PKCE support, automatic token refresh, and secure token storage.

## Features

- **OAuth 2.0 Authorization Code Flow with PKCE** (RFC 7636)
- **Automatic Token Refresh** - Tokens are automatically refreshed before expiration
- **Secure Token Storage** - Tokens persisted to local filesystem in JSON format
- **Bearer Token Authentication** - API client with automatic token management
- **Environment Variable Support** - Configuration values support %VAR% placeholders

## Architecture

The module consists of several key components:

1. **CCAPICredentialOauth** - OAuth credential model (loaded from XML)
2. **PKCEGenerator** - PKCE code verifier and challenge generator
3. **OAuthUrlBuilder** - Authorization URL builder
4. **OAuthClient** - Handles token exchange and refresh
5. **OAuthTokenStorage** - Token persistence to filesystem
6. **OAuthTokenManager** - Manages token lifecycle with auto-refresh
7. **OAuthApiClient** - API client with Bearer token authentication

## Setup

### 1. Add OAuth Credentials to CCAPIsCredentials.xml

Location: `<userhome>/xyz-jphil/ccapis/CCAPIsCredentials.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<CCAPIsCredentials>
    <!-- OAuth Credentials -->
    <CCAPICredentialOauth
        id="LGL"
        name="Legal"
        email="legal@uskfoundation.or.ke"
        clientId="9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        ccapiBaseUrl="%CCAPI_BASE_URL%"
        ccapiConsoleUrl="%CCAPI_CONSOLE_URL%"
        ccapiApiUrl="%CCAPI_API_URL%"
        tier="1"
        active="true"
        trackUsage="true"
        ping="true" />
</CCAPIsCredentials>
```

### 2. Set Environment Variables

```bash
# Windows (cmd)
set CCAPI_BASE_URL=https://console.anthropic.com
set CCAPI_CONSOLE_URL=https://console.anthropic.com
set CCAPI_API_URL=https://api.anthropic.com

# Windows (PowerShell)
$env:CCAPI_BASE_URL="https://console.anthropic.com"
$env:CCAPI_CONSOLE_URL="https://console.anthropic.com"
$env:CCAPI_API_URL="https://api.anthropic.com"

# Linux/Mac
export CCAPI_BASE_URL=https://console.anthropic.com
export CCAPI_CONSOLE_URL=https://console.anthropic.com
export CCAPI_API_URL=https://api.anthropic.com
```

## Usage

### Complete OAuth Flow

```java
// 1. Load credentials
var credManager = new OAuthCredentialsManager();
var credentials = credManager.load();
var credential = credentials.getById("LGL");

// 2. Generate PKCE challenge
var pkce = PKCEGenerator.generate();

// 3. Build authorization URL
var authUrl = OAuthUrlBuilder.buildAuthorizationUrl(credential, pkce);

// 4. User opens URL in browser and authorizes
System.out.println("Open this URL: " + authUrl);
System.out.println("Enter authorization code:");
var authCode = new Scanner(System.in).nextLine();

// 5. Exchange code for tokens
var oauthClient = new OAuthClient();
var tokens = oauthClient.exchangeCodeForTokens(credential, authCode, pkce.verifier());

// 6. Save tokens
var tokenManager = new OAuthTokenManager();
tokenManager.saveTokens(credential, tokens);
```

### Making API Calls

```java
// Create API client (automatically handles token refresh)
var apiClient = new OAuthApiClient();

// Make authenticated requests
var response = apiClient.get(credential, "https://api.anthropic.com/v1/messages");

// POST with JSON
var payload = Map.of(
    "model", "claude-3-5-sonnet-20241022",
    "max_tokens", 100,
    "messages", List.of(Map.of("role", "user", "content", "Hello"))
);
var response = apiClient.post(credential, "https://api.anthropic.com/v1/messages", payload);
```

### Token Management

```java
var tokenManager = new OAuthTokenManager();

// Check if tokens exist
if (tokenManager.hasTokens(credential)) {
    // Get valid token (auto-refreshes if expired)
    var accessToken = tokenManager.getValidAccessToken(credential);

    // Make API call
    // ...
}

// Manually refresh tokens
var currentTokens = tokenManager.getTokens(credential);
var newTokens = tokenManager.refreshAndSaveTokens(credential, currentTokens);

// Delete tokens (logout)
tokenManager.deleteTokens(credential);
```

## OAuth Endpoints

Based on PRP requirements:

- **Authorization URL**: `https://%CCAPI_BASE_URL%/oauth/authorize`
- **Token Exchange**: `https://%CCAPI_CONSOLE_URL%/v1/oauth/token`
- **Token Refresh**: `https://%CCAPI_CONSOLE_URL%/v1/oauth/token` (with grant_type=refresh_token)
- **API Endpoint**: `https://%CCAPI_API_URL%` (with Authorization: Bearer {access_token})

## OAuth Scopes

Default scopes requested:
- `org:create_api_key`
- `user:profile`
- `user:inference`

## Token Storage

Tokens are stored at: `<userhome>/xyz-jphil/ccapis/oauth-tokens/<credential-id>.tokens.json`

Example token file:
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "expires_in": 3600,
  "token_type": "Bearer",
  "expiresAt": 1730112300
}
```

## Testing

Run the interactive OAuth flow test:

```bash
mvn test -Dtest=OAuthFlowBasicTest -pl xyz-jphil-ccapis-oauth
```

This test will:
1. Load OAuth credentials
2. Generate authorization URL
3. Prompt you to authorize in browser
4. Exchange code for tokens
5. Save tokens
6. Test API call
7. Verify token persistence

## Security Notes

- Tokens are stored in plaintext JSON files in user's home directory
- Code verifier is used as CSRF state parameter
- PKCE S256 challenge method is used for enhanced security
- Tokens are automatically refreshed 60 seconds before expiration

## Dependencies

- OkHttp3 4.12.0 - HTTP client
- Jackson 2.15.2 - JSON processing
- Jakarta XML Bind 4.0.0 - XML credential parsing
- Lombok 1.18.38 - Boilerplate reduction

## References

- [RFC 7636 - PKCE](https://tools.ietf.org/html/rfc7636)
- [OAuth 2.0 Authorization Code Flow](https://oauth.net/2/grant-types/authorization-code/)
- ccflare project: https://github.com/snipeship/ccflare
