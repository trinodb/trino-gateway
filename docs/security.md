# Security

Trino Gateway has its own security with its own authentication and authorization.
These features are used only to authenticate and authorize its user interface and
the APIs. All Trino-related requests are passed through to the Trino cluster
without any authentication or authorization check in Trino Gateway, unless the
optional client-certificate JWT bridge is configured.

## Client certificate JWT bridge

For environments where clients authenticate to Trino with mTLS certificates,
Trino Gateway can bridge validated client certificate identity to backend Trino
by minting a short-lived signed JWT and forwarding the request with
`Authorization: Bearer <token>`.

This mode is opt-in and is active when the `clientCertificateJwtAuthentication`
block is present in the config. It applies to every request the gateway forwards
to a backend cluster, except the browser and health endpoints that cannot present
a client certificate: `/ui`, `/oauth2`, `/v1/info` and `/v1/node`. Defining it
this way - as everything that is proxied, minus an explicit exemption list -
means Trino endpoints added in the future, and any path added through
`extraWhitelistPaths`, are authenticated rather than silently skipped.

```yaml
requestAnalyzerConfig:
  # Required when client certificate authentication is configured (mapping
  # pattern/file, or the clientCertificateJwtAuthentication block). No default.
  # Use SUBJECT_DN to match Trino's own certificate authentication, which maps
  # the whole subject DN. Any other value (CN, OU, ...) extracts that RDN from
  # the subject DN first and maps only its value - see the note below.
  clientCertificateIdentityField: SUBJECT_DN
  # Optional. Mutually exclusive with clientCertificateUserMappingFile.
  clientCertificateUserMappingPattern:
  # Optional. Mutually exclusive with clientCertificateUserMappingPattern.
  clientCertificateUserMappingFile:

clientCertificateJwtAuthentication:
  # Optional. Defaults to sub.
  jwtPrincipalClaim: sub
  # Optional. Configure only if backend Trino validates audience.
  jwtAudiences:
    - trino
  # Optional. Configure only if backend Trino validates issuer.
  jwtIssuer:
  # Optional. Configure only if JWT verification uses a key id.
  jwtKeyId:
  # Required.
  jwtSigningKeyPair:
    privateKey: <bridge_private_key_path>
    publicKey: <bridge_public_key_path>
  # Optional. Defaults to false. When true, requests on the authenticated
  # paths described above are rejected with 401 if no client certificate
  # identity could be resolved, instead of being forwarded unauthenticated by
  # the bridge. This does not affect the HTTPS listener's TLS handshake, which
  # always requests but never requires a client certificate (see below), so
  # the UI and other authentication methods on the gateway are unaffected.
  clientCertificateRequired: false
  # Optional. Defaults to false. When true, the bridge also overwrites the
  # X-Trino-User and X-Trino-Original-User headers with the certificate
  # identity, so the backend session user matches the certificate principal.
  # Leave false to preserve a client-supplied X-Trino-User (impersonation),
  # see below.
  overrideTrinoUser: false
```

When using this bridge:

- the gateway requests, but does not require, client certificates on its
  HTTPS listener (`ClientCertificate.REQUESTED`, not `NEED`); this is
  intentional, since the listener is shared with the gateway's own UI and
  API, which do not authenticate with client certificates, and enforcing a
  mandatory client certificate at the TLS layer would block them
- to require a client certificate specifically for Trino query traffic, set
  `clientCertificateRequired: true` under `clientCertificateJwtAuthentication`
  rather than trying to make the TLS listener mandatory
- configure TLS on Trino Gateway to require and validate client certificates
- the certificate identity selected by `requestAnalyzerConfig` can be mapped
  with Trino-style user-mapping rules before the JWT is created; see
  [certificate identity and user mapping](#certificate-identity-and-user-mapping)
  before copying rules from a cluster
- configure Trino with JWT authentication using the matching public key
- the signing key pair can be RSA or EC; the gateway selects the matching JWT
  algorithm automatically

### Certificate identity and user mapping

Trino's own certificate authentication applies
[user mapping](https://trino.io/docs/current/security/user-mapping.html) to the
**whole subject DN** of the client certificate, as rendered by
`X500Principal.toString()`. Setting `clientCertificateIdentityField: SUBJECT_DN`
reproduces that exactly, so user mapping rules can be copied from a cluster's
`http-server.authentication.certificate.user-mapping.*` configuration and behave
identically on the gateway.

Any other value of `clientCertificateIdentityField` is a gateway-specific
convenience: the named RDN is extracted from the subject DN first (the
leaf-most one, if the DN repeats it) and the mapping rules are applied to that
value alone. **Rules written for Trino do not carry over unchanged in this
mode.** A rule such as `{"pattern": "CN=admin,.*", "allow": false}` matches a
DN, not a bare `admin`, so it would stop matching - and stop denying - while a
trailing catch-all rule still maps the principal through. The gateway refuses
to start on that combination: a mapping file containing `"allow": false` rules
requires `clientCertificateIdentityField: SUBJECT_DN`. Either use `SUBJECT_DN`,
or rewrite the rules against the extracted value.

A certificate whose selected RDN carries a hex-encoded (BER) attribute value is
rejected rather than mapped, since there is no meaningful text identity to
extract from it.

### Impersonation

The bridge replaces the `Authorization` header, and - only when
`overrideTrinoUser` is enabled - the user headers; it does not otherwise strip
or overwrite an `X-Trino-User` header the client sends. If a client presents a
certificate mapped to `alice` but also sends `X-Trino-User: bob`, Trino
receives a JWT authenticating the principal as `alice` and a session user of
`bob`. Trino resolves this the same way it would for a direct mTLS
connection: it checks whether the principal is allowed to run queries as the
session user (impersonation), and grants it unless the cluster's system
access control says otherwise. With no `access-control.properties`
configured, Trino's default `AllowAllSystemAccessControl` permits any
principal to impersonate any user, so `alice`'s certificate would let her run
queries on the backend cluster as `bob`. This is independent of the gateway's
own routing and query-history records, which use the certificate identity
(`alice`) once the client certificate is present - it is specifically the
backend Trino cluster's session user, and whatever query history and
row/column access control it drives, that is affected.

To make the backend session user always match the certificate identity, set
`overrideTrinoUser: true`. The bridge then overwrites both `X-Trino-User` and
`X-Trino-Original-User` with the mapped certificate identity, so `alice`'s
certificate always runs as `alice` and the gateway and cluster records agree.
Both headers matter: Trino builds the session's *original* identity from
`X-Trino-Original-User` and evaluates `checkCanSetUser` and
`checkCanImpersonateUser` against it, so pinning only `X-Trino-User` would
leave part of the backend identity client-controlled. `X-Trino-Original-Roles`
is forwarded unchanged, because Trino validates those roles against the
original identity, which is now pinned to the certificate identity. Note that
enabling this disables `SET SESSION AUTHORIZATION` for clients behind the
gateway, which is the intended effect. This is left off by default so that a
client can still request a session user for impersonation.

The certificate-to-JWT bridge on its own does not close this off - the
identity guarantee it provides is about authentication (who the client is),
not authorization (who they're allowed to act as). To restrict impersonation,
configure Trino's file-based system access control
(`access-control.name=file`) on the backend cluster and add impersonation
rules to its rules file restricting which principals may run queries as which
users. See the [Trino system access control
documentation](https://trino.io/docs/current/security/file-system-access-control.html)
for details.

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
`authentication:` section in the config file and configure one or more method
blocks (`oauth` and/or `form`) — configuring a block is what enables that method.
The `defaultType` property then sets the order the methods are tried in and which
appear on the login page; it accepts either a single value (`defaultType: "form"`)
or a list (`defaultType: ["oauth", "form"]`), where the first entry is the primary
(pre-selected) method on the login page and the rest are fallbacks.
Following types of the authentications are supported.

> **What `defaultType` controls (and what it doesn't):** Configuring an `oauth`
> or `form` block is what makes that method *accepted* by the gateway — the
> authentication chain always accepts every configured method, so a configured
> method can never be dropped by leaving it out of `defaultType`. `defaultType`
> only sets two things: the order methods are tried in (fallback priority) and
> which method(s) the login page advertises. A configured method that is not
> listed in `defaultType` is still accepted by the API; it is simply not shown on
> the login page. This is what lets humans sign in through the primary method
> (for example `oauth`) while automation keeps using form/basic auth.

> **Login page vs. API fallback:** By default the login page offers every
> configured *and listed* authentication method (the first is pre-selected, and
> any additional methods are shown as options to switch between). Set
> `showFirstTypeOnly: true` to restrict the login page to only the first such
> method. This affects the login page only — the authentication chain still
> accepts every configured method, so API clients keep their multi-method
> fallback (for example, form/basic auth for automation even when `oauth` is
> listed first and shown on the login page):
>
> ```yaml
> authentication:
>   defaultType: ["oauth", "form"]
>   showFirstTypeOnly: true  # login page shows only oauth; API still accepts form/basic
> ```

> **Migrating from the scalar `defaultType`:** Earlier releases configured a
> single string `defaultType: "form"`. That form still works unchanged — a scalar
> is bound as a one-element list — so existing configurations keep booting without
> edits. Use the list form (`defaultType: ["oauth", "form"]`) to choose which
> configured methods the login page shows and in what order (the first entry is
> the primary method). Listing a method does not enable it; each method is enabled
> by its own `oauth`/`form` block.

### OAuth/OpenIDConnect

It can be configured as below

```yaml
authentication:
  defaultType: ["oauth"]
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

Also provide a signing key pair in RSA or EC format.

```yaml
authentication:
  defaultType: ["form"]
  form:
    selfSignKeyPair:
      privateKey: <private_key_path>
      publicKey: <public_key_path>
```

### Form/LDAP

LDAP requires both random key pair and config path for LDAP

```yaml
authentication:
  defaultType: ["form"]
  form:
    ldapConfigPath: <ldap_config_path>
    selfSignKeyPair:
      privateKey: <private_key_path>
      publicKey: <public_key_path>
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
  ldapUserDnPattern: <optional, enables direct bind>
  ldapGroupMemberAttribute: <>
  ldapAdminPassword: <>
  ldapTrustStorePath: <for a secure ldap connectivity>
  ldapTrustStorePassword: '<for a secure ldap connectivity>'
  poolMaxIdle: 8
  poolMaxTotal: 8
  poolMinIdle: 0
  poolTestOnBorrow: true
```

### Direct bind

By default the gateway authenticates a user in two steps. It binds with
`ldapAdminBindDn` and `ldapAdminPassword`, searches for the user entry under
`ldapUserBaseDn` with `ldapUserSearch`, and then binds as the DN it found.

If the user DN can be derived from the login name, set `ldapUserDnPattern` to
skip the search and bind directly:

```yaml
  ldapUserDnPattern: 'uid=${USER},ou=people,dc=example,dc=com'
```

The `${USER}` placeholder is replaced with the login name, escaped according to
the DN escaping rules. A bind with an empty password is rejected before it
reaches the server, because some directory servers treat it as an anonymous
unauthenticated bind and answer with success.

The pattern is validated while the configuration is loaded. It must contain the
`${USER}` placeholder, otherwise every login would bind as the same DN, and it
must resolve to a valid DN. An invalid pattern fails the startup instead of
failing every login attempt.

When `ldapUserDnPattern` is not set, the search based flow is used, so existing
configurations are unaffected.

LDAP authorization still performs a search to read
`ldapGroupMemberAttribute`, and therefore still requires `ldapAdminBindDn`.
Direct bind removes the need for an admin account only when LDAP authorization
is not used.

### Default privileges

By default, a user who doesn't match a preset user, an LDAP group, or an OAuth
`privilegesField` claim resolves to no privileges and is denied access. To instead
give every such user a baseline set of privileges, set `enableDefaultPrivilege: true`
under `authorization:` and configure the baseline in `defaultPrivilege`. This is
especially useful when `oauth` is used as (one of) the `defaultType` for
authentication, since SSO users typically aren't individually preset but should
still get at least `USER` access after logging in.

```yaml
authorization:
  admin: (.*)ADMIN(.*)
  user: (.*)USER(.*)
  api: (.*)API(.*)
  enableDefaultPrivilege: true
  defaultPrivilege: "USER"
```

With the configuration above, any authenticated user not otherwise matched
receives `USER` privileges (view access), while users explicitly listed in
`presetUsers`, resolved via LDAP, or matched via an OAuth claim keep their own
configured privileges instead.

> **LDAP is authoritative when configured.** `defaultPrivilege` only applies to
> users that aren't preset and aren't resolved through LDAP — typically OAuth/SSO
> (or passwordless) users. When `authorization.ldapConfigPath` is set, a user that
> LDAP resolves with no groups (an empty search result) is denied access rather
> than falling back to `defaultPrivilege`, so a misconfigured LDAP search (wrong
> base DN, member attribute, etc.) cannot silently widen access. Preset users with
> empty privileges still fall back to `defaultPrivilege`.

To deny access to every unrecognized user, leave `enableDefaultPrivilege` unset
(it defaults to `false`), in which case `defaultPrivilege` is ignored entirely and
unmatched users get no privileges:

```yaml
authorization:
  admin: (.*)ADMIN(.*)
  user: (.*)USER(.*)
  api: (.*)API(.*)
  enableDefaultPrivilege: false   # the default; unmatched users are denied
```

Even with `enableDefaultPrivilege: true`, a `defaultPrivilege` that is missing,
empty, or `NONE` (case-insensitive) resolves unrecognized users to no privileges,
so they are denied access regardless of the `admin`, `user`, and `api` regexes.
This makes the deny-by-default intent explicit while the feature is otherwise
enabled.

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
