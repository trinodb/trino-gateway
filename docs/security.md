# Security

Trino Gateway has its own security with its own authentication and authorization.
These features are used only to authenticate and authorize its user interface and
the APIs. All Trino-related requests are passed through to the Trino cluster
without any authentication or authorization check in Trino Gateway.

## TLS configuration

All authentication and authorization mechanisms require configuring TLS as the
foundational layer. Your site or cloud environment may already have a load balancer
or proxy server configured and running with a valid, globally trusted TLS certificate.
In this case, you can work with your network administrators to set up your Trino
Gateway behind the load balancer.

You can also configure an end-to-end TLS connection using Trino Gateway.
This requires you to obtain and install a TLS certificate and configure Trino
Gateway to use it for client connections. The following configuration
enables TLS for Trino Gateway.

```yaml
serverConfig:
    http-server.http.enabled: false
    http-server.https.enabled: true
    http-server.https.port: 8443
    http-server.https.keystore.path: certificate.pem
    http-server.https.keystore.key: changeme
```

For advanced configurations, refer to the [Trino
TLS documentation](https://trino.io/docs/current/security/tls.html)
for more details.


## Authentication

The authentication would happen on https protocol only. Add the
`authentication:` section in the config file. The default authentication type is
set using `defaultType: "form"` Following types of the authentications are
supported.

### OAuth/OpenIDConnect

It can be configured as below

```yaml
authentication:
  defaultType: "oauth"
  oauth:
    issuer:
    clientId:
    clientSecret:
    tokenEndpoint:
    authorizationEndpoint:
    jwkEndpoint:
    redirectUrl:
    redirectWebUrl: 
    userIdField:
    scopes:
      - s1
      - s2
      - s3
```

Set the `privilegesField` to retrieve privileges from an OAuth claim.

### Note

- For OAuth Trino Gateway uses `oidc/callback` where as Trino uses `oauth2` path
- Trino Gateway should have its own client id
- All the Trino clusters should have a single client id.
- Trino Gateway needs to pass thorugh the Trino Oauth2 requests only to one of the clusters.
- One way to handle it is to set a special rule like below:
```yaml
  ---
  name: "Oauth requests"
  description: "Oauth requests need to go to a single backed"
  condition: "request.getRequestURI.startsWith(\"/oauth2\")"
  actions:
    - "result.put(\"routingGroup\", \"oauth2-handler\")"
```
- That also means you need to have a cluster with that routing group.
- It's ok to replicate an existing Trino cluster record with a different name for that purpose.

### Form/Basic authentication

The authentication happens with the pre-defined users from the configuration
file. To define the preset user use the following section.
Please note that 'privileges' can only be a combination of 'ADMIN', 'USER', and 'API', with '_' used for segmentation.

```yaml
presetUsers:
  user1:
    password: <password>
    privileges: ADMIN_USER
  user2:
    password: <password>
    privileges: API
```

Also provide a random key pair in RSA format.

```yaml
authentication:
  defaultType: "form"
  form:
    selfSignKeyPair:
      privateKeyRsa: <private_key_path>
      publicKeyRsa: <public_key_path>
```

### Form/LDAP

LDAP requires both random key pair and config path for LDAP

```yaml
authentication:
  defaultType: "form"
  form:
    ldapConfigPath: <ldap_config_path>
    selfSignKeyPair:
      privateKeyRsa: <private_key_path>
      publicKeyRsa: <public_key_path>
```

### JWT authentication

JWT (JSON Web Token) authentication enables access to Trino Gateway
without interactive login. This is useful for integration with external identity providers.

JWT authentication supports three key sources:

- **RSA public key file**: A PEM-encoded RSA public key file
- **HMAC secret file**: A file containing the HMAC secret
- **JWKS URL**: A URL to a JSON Web Key Set (for dynamic key retrieval)

```yaml
authentication:
  defaultType: "jwt" # Using JWT authentication
  jwt:
    # Key file can be:
    # - Path to RSA public key PEM file
    # - Path to HMAC secret file
    # - JWKS URL (e.g., https://idp.example.com/.well-known/jwks.json)
    keyFile: "/etc/trino-gateway/jwt-public-key.pem"

    # Required issuer - tokens must have this issuer claim
    requiredIssuer: "https://idp.example.com"

    # Required audience - tokens must have this audience claim
    requiredAudience: "trino-gateway"

    # JWT claim field containing the principal/username (default: "sub")
    principalField: "sub"

    # User mapping (optional) - transform the principal to a different username
    # Use either userMappingPattern OR userMappingFile, not both
    userMappingPattern: "(.*)@example\\.com"  # Extracts username before @
    # userMappingFile: "/etc/trino-gateway/user-mapping.json"
```

**Example with JWKS URL:**

```yaml
authentication:
  jwt:
    keyFile: "https://login.microsoftonline.com/tenant-id/discovery/v2.0/keys"
    requiredIssuer: "https://login.microsoftonline.com/tenant-id/v2.0"
    requiredAudience: "api://trino-gateway"
    principalField: "preferred_username"
    userMappingPattern: "(.*)@company\\.com"
```

When using JWT authentication, clients include the token in the Authorization header:

```
Authorization: Bearer <jwt-token>
```

## Authorization

Trino Gateway supports the following roles in regex string format:

- admin : Allows access to the Editor tab, which can be used to configure the
  clusters

- user : Allows access to the rest of the website

- api : Allows access to rest apis to configure the clusters

Users with attributes next to the role will be giving those privileges the
users. You can use the preset users defined in the yaml file. 
LDAP Authorization is also supported by adding user attribute configs in file.
An OAuth claim can be used by setting the `privilegesField` in the OAuth
configuration.

- Check out [LDAPTestConfig.yml](https://github.com/trinodb/trino-gateway/blob/main/gateway-ha/src/test/resources/auth/ldapTestConfig.yml) file for config details

```yaml
# Roles should be in regex format
authorization:
  admin: (.*)ADMIN(.*)
  user: (.*)USER(.*)
  api: (.*)API(.*)
  ldapConfigPath: '<ldap_config_path>'
```

The LDAP config file should have the following contents:

```yaml
  ldapHost: '<ldap sever>'
  ldapPort: <port>
  useTls: <true/false>
  useSsl: <true/false>
  ldapAdminBindDn: <>
  ldapUserBaseDn: <>
  ldapUserSearch: <>
  ldapGroupMemberAttribute: <>
  ldapAdminPassword: <>
  ldapTrustStorePath: <for a secure ldap connectivity>
  ldapTrustStorePassword: '<for a secure ldap connectivity>'
  poolMaxIdle: 8
  poolMaxTotal: 8
  poolMinIdle: 0
  poolTestOnBorrow: true
```

## User mapping

User mapping transforms the authenticated user identity before it is used for
authorization decisions. This is useful for:

- Extracting usernames from email addresses (e.g., `john.doe@company.com` → `john.doe`)
- Normalizing usernames to a specific case
- Denying access to specific users or patterns
- Mapping external identity provider names to internal usernames

### How user mapping works

1. **Input**: The principal extracted from the authentication source
   - For JWT: The value of the claim specified by `principalField` (default: `sub`)
   - For OAuth: The value of `userIdField` claim
   - For Form/LDAP: The authenticated username

2. **Transformation**: The principal is matched against user mapping rules
   - Rules are evaluated in order until a match is found
   - Each rule contains a regex pattern and optional transformation
   - If no rule matches, authentication fails

3. **Output**: The mapped username used for authorization
   - The mapped user is checked against the authorization manager
   - Privileges (admin, user, api) are determined based on the mapped identity

### Configuration options

User mapping can be configured in two ways:

**Simple pattern (inline regex):**

```yaml
authentication:
  jwt:
    keyFile: "/etc/trino-gateway/jwt-public-key.pem"
    principalField: "email"
    userMappingPattern: "(.*)@company\\.com"
```

This pattern extracts the first capture group as the username. For example,
`alice@company.com` becomes `alice`.

**Rules file (advanced):**

```yaml
authentication:
  jwt:
    keyFile: "/etc/trino-gateway/jwt-public-key.pem"
    principalField: "sub"
    userMappingFile: "/etc/trino-gateway/user-mapping.json"
```

### User mapping rules file format

The rules file is a JSON file with an array of rules:

```json
{
  "rules": [
    {
      "pattern": "(.*)@banned-domain\\.com",
      "allow": false
    },
    {
      "pattern": "admin@company\\.com",
      "user": "super-admin"
    },
    {
      "pattern": "(.*)@company\\.com",
      "user": "$1",
      "case": "LOWER"
    },
    {
      "pattern": "(.*)",
      "user": "$1"
    }
  ]
}
```

**Rule properties:**

| Property  | Required | Default | Description |
|-----------|----------|---------|-------------|
| `pattern` | Yes      | -       | Regex pattern to match against the principal |
| `user`    | No       | `$1`    | Replacement string (supports regex capture groups) |
| `allow`   | No       | `true`  | Set to `false` to deny matching principals |
| `case`    | No       | `KEEP`  | Case transformation: `KEEP`, `LOWER`, or `UPPER` |

**How rules are evaluated:**

1. Rules are evaluated in order from first to last
2. The first matching rule determines the result
3. If `allow` is `false`, authentication fails with "Principal is not allowed"
4. If `allow` is `true` (default), the `user` replacement is applied
5. Capture groups from the pattern can be referenced in `user` (e.g., `$1`, `$2`)
6. If no rule matches, authentication fails

### User mapping examples

**Example 1: Strip email domain**

Pattern: `(.*)@.*`

| Input | Output |
|-------|--------|
| `alice@company.com` | `alice` |
| `bob@external.org` | `bob` |

**Example 2: Allow only specific domain and lowercase**

Rules file:
```json
{
  "rules": [
    {
      "pattern": "(.*)@company\\.com",
      "user": "$1",
      "case": "LOWER"
    }
  ]
}
```

| Input | Output |
|-------|--------|
| `Alice@company.com` | `alice` |
| `Bob@COMPANY.COM` | No match (domain is case-sensitive) |
| `carol@external.com` | No match (authentication fails) |

**Example 3: Deny specific users**

Rules file:
```json
{
  "rules": [
    {
      "pattern": "service-account-blocked",
      "allow": false
    },
    {
      "pattern": "(.*)",
      "user": "$1"
    }
  ]
}
```

| Input | Output |
|-------|--------|
| `service-account-blocked` | Denied |
| `alice` | `alice` |

## Web page permissions

By default, all pages are accessible to all roles.
To limit page access, you can set page permissions by pages 
and `_` as separator field.

The following pages are available:

- `dashboard`
- `cluster`
- `resource-group`
- `selector`
- `history`

```yaml
# admin/api can access all pages, while user can only access dashboard/history
pagePermissions:
  admin: 
  user: dashboard_history 
  api: 
```

## Extra: Self-signed certificate in Trino Gateway

If Trino Gateway is using a self-signed certificate, client should use the
`--insecure` config.

```shell
java -jar trino-cli-executable.jar --server https://localhost:8443 --insecure
```

## Extra: Self-signed certificate in Trino <a name="cert-trino"></a>

If Trino is using a self-signed certificate, the following JVM config for
Trino Gateway should be added:

```properties
-Djavax.net.ssl.trustStore=<truststore file>
-Djavax.net.ssl.trustStorePassword=<truststore password>
```

If you want to skip the hostname validation for a self-signed certificate, 
the `serverConfig` configuration should contain the following:

```yaml
serverConfig:
  proxy.http-client.https.hostname-verification: false
  monitor.http-client.https.hostname-verification: false
```
