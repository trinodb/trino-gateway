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

This mode is opt-in and currently applies to Trino query protocol paths such as
`/v1/statement` and `/v1/query`. The bridge is active when the
`clientCertificateJwtAuthentication` block is present in the config.

```yaml
requestAnalyzerConfig:
  # Optional. Defaults to CN.
  clientCertificateIdentityField: CN
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
    privateKey: <private_key_path>
    publicKey: <public_key_path>
```

When using this bridge:

- the gateway requests client certificates on its HTTPS listener
- configure TLS on Trino Gateway to require and validate client certificates
- the certificate identity selected by `requestAnalyzerConfig` can be mapped
  with Trino-style user-mapping rules before the JWT is created
- configure Trino with JWT authentication using the matching public key
- the signing key pair can be RSA or EC; the gateway selects the matching JWT
  algorithm automatically

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

Also provide a signing key pair in RSA or EC format.

```yaml
authentication:
  defaultType: "form"
  form:
    selfSignKeyPair:
      privateKey: <private_key_path>
      publicKey: <public_key_path>
```

### Form/LDAP

LDAP requires both random key pair and config path for LDAP

```yaml
authentication:
  defaultType: "form"
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
  ldapGroupMemberAttribute: <>
  ldapAdminPassword: <>
  ldapTrustStorePath: <for a secure ldap connectivity>
  ldapTrustStorePassword: '<for a secure ldap connectivity>'
  poolMaxIdle: 8
  poolMaxTotal: 8
  poolMinIdle: 0
  poolTestOnBorrow: true
```

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
