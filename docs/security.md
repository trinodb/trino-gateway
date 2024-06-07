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
httpConfig:
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

```
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

### Note

- For OAuth Trino Gateway uses `oidc/callback` where as Trino uses `oauth2` path
- Trino Gateway should have its own client id
- All the Trino backend clusters should have a single client id.
- Trino Gateway needs to pass thorugh the Trino Oauth2 requests only to one of the clusters.
- One way to handle it is to set a special rule like below:
```
  ---
  name: "Oauth requests"
  description: "Oauth requests need to go to a single backed"
  condition: "request.getRequestURI.startsWith(\"/oauth2\")"
  actions:
    - "result.put(\"routingGroup\", \"oauth2-handler\")"
```
- That also means you need to have a cluster with that routing group.
- It's ok to replicate an existing cluster backend record with a different name for that purpose.

### Form/Basic authentication

The authentication happens with the pre-defined users from the configuration
file. To define the preset user use the following section.
Please note that 'privileges' can only be a combination of 'ADMIN', 'USER', and 'API', with '_' used for segmentation.

```
presetUsers:
  user1:
    password: <password>
    privileges: ADMIN_USER
  user2:
    password: <password>
    privileges: API
```

Also provide a random key pair in RSA format.

```
authentication:
  defaultType: "form"
  form:
    selfSignKeyPair:
      privateKeyRsa: <private_key_path>
      publicKeyRsa: <public_key_path>
```

### Form/LDAP

LDAP requires both random key pair and config path for LDAP

```
authentication:
  defaultType: "form"
  form:
    ldapConfigPath: <ldap_config_path>
    selfSignKeyPair:
      privateKeyRsa: <private_key_path>
      publicKeyRsa: <public_key_path>
```


## Authorization

Trino Gateway supports the following roles in regex string format:

- admin : Allows access to the Editor tab, which can be used to configure the
  backends

- user : Allows access to the rest of the website

- api : Allows access to rest apis to configure the backends

Users with attributes next to the role will be giving those privileges the
users. You can use the preset users defined in the yaml file. 
LDAP Authorization is also supported by adding user attribute configs in file.

- Check out [LDAPTestConfig.yml](https://github.com/trinodb/trino-gateway/blob/main/gateway-ha/src/test/resources/auth/ldapTestConfig.yml) file for config details

```
# Roles should be in regex format
authorization:
  admin: (.*)ADMIN(.*)
  user: (.*)USER(.*)
  api: (.*)API(.*)
  ldapConfigPath: '<ldap_config_path>'
```

The LDAP config file should have the following contents:

```
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

```bash
java -jar trino-cli-executable.jar --server https://localhost:8443 --insecure
```

## Extra: Self signed certificate in Trino

If Trino is using a self-signed certificate, the following JVM config for
Trino Gateway should be added:

```properties
-Djavax.net.ssl.trustStore=<truststore file>
-Djavax.net.ssl.trustStorePassword=<truststore password>
```
