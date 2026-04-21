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

## Role permissions reference

Trino Gateway enforces three roles. A user can hold multiple roles simultaneously
(e.g. granting both ADMIN and USER access).

| Role | Purpose |
|------|---------|
| `ADMIN` | Manages cluster configuration and routing rules via the web UI and API |
| `API` | Programmatic cluster management via REST API |
| `USER` | Read access to query history, dashboards, and resource group management |

### ADMIN role

**Cluster management**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/webapp/saveBackend` | Add a new cluster |
| POST | `/webapp/updateBackend` | Update an existing cluster |
| POST | `/webapp/deleteBackend` | Delete a cluster |
| GET  | `/webapp/getRoutingRules` | Retrieve routing rules |
| POST | `/webapp/updateRoutingRules` | Update a routing rule |

**Entity management**

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/entity` | List available entity types |
| POST | `/entity` | Create or update a gateway cluster, resource group, or selector |
| GET  | `/entity/{entityType}` | List all entities of a given type |

### API role

**Gateway management**

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/gateway` | Gateway health check |
| GET  | `/gateway/backend/all` | List all clusters |
| GET  | `/gateway/backend/active` | List active clusters |
| POST | `/gateway/backend/deactivate/{name}` | Deactivate a cluster |
| POST | `/gateway/backend/activate/{name}` | Activate a cluster |
| POST | `/gateway/backend/modify/add` | Add a cluster |
| POST | `/gateway/backend/modify/update` | Update a cluster |
| POST | `/gateway/backend/modify/delete` | Delete a cluster |

### USER role

**Query history and clusters**

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/trino-gateway/api/queryHistory` | List query history |
| GET  | `/trino-gateway/api/activeBackends` | List active clusters |
| GET  | `/trino-gateway/api/queryHistoryDistribution` | Query history distribution |
| POST | `/webapp/getAllBackends` | Get all clusters |
| POST | `/webapp/findQueryHistory` | Search query history |
| POST | `/webapp/getDistribution` | Query distribution stats |

Non-ADMIN users can only see their own queries when searching query history.

**Resource group and selector management**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/webapp/saveResourceGroup` | Create a resource group |
| POST | `/webapp/updateResourceGroup` | Update a resource group |
| POST | `/webapp/deleteResourceGroup` | Delete a resource group |
| POST | `/webapp/findResourceGroup` | Search resource groups |
| POST | `/webapp/getResourceGroup` | Get a resource group |
| POST | `/webapp/saveSelector` | Create a selector |
| POST | `/webapp/updateSelector` | Update a selector |
| POST | `/webapp/deleteSelector` | Delete a selector |
| POST | `/webapp/findSelector` | Search selectors |
| POST | `/webapp/getSelector` | Get a selector |
| POST | `/webapp/saveGlobalProperty` | Create a global property |
| POST | `/webapp/updateGlobalProperty` | Update a global property |
| POST | `/webapp/deleteGlobalProperty` | Delete a global property |
| POST | `/webapp/findGlobalProperty` | Search global properties |
| POST | `/webapp/getGlobalProperty` | Get a global property |
| POST | `/webapp/saveExactMatchSourceSelector` | Create exact-match selector |
| POST | `/webapp/findExactMatchSourceSelector` | Get exact-match selectors |
| POST | `/trino/resourcegroup/create` | Create a resource group |
| GET  | `/trino/resourcegroup/read` | List all resource groups |
| GET  | `/trino/resourcegroup/read/{resourceGroupId}` | Get resource group by ID |
| POST | `/trino/resourcegroup/update` | Update a resource group |
| POST | `/trino/resourcegroup/delete/{resourceGroupId}` | Delete a resource group |
| POST | `/trino/selector/create` | Create a selector |
| GET  | `/trino/selector/read` | List all selectors |
| GET  | `/trino/selector/read/{resourceGroupId}` | Get selectors for a resource group |
| POST | `/trino/selector/update` | Update a selector |
| POST | `/trino/selector/delete/` | Delete a selector |
| POST | `/trino/globalproperty/create` | Create a global property |
| GET  | `/trino/globalproperty/read` | List all global properties |
| GET  | `/trino/globalproperty/read/{name}` | Get a global property by name |
| POST | `/trino/globalproperty/update` | Update a global property |
| POST | `/trino/globalproperty/delete/{name}` | Delete a global property by name |

**User info**

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/webapp/getUIConfiguration` | Get UI configuration |
| POST | `/userinfo` | Get info for the authenticated user |

## Web page permissions

The web UI enforces access control at two distinct layers:

### Sidebar tab visibility

By default, all sidebar tabs are visible to all roles. To restrict which tabs
appear for a given role, configure `pagePermissions` using the tab keys below
with `_` as a separator.

The following tabs are available:

- `dashboard`
- `cluster`
- `resource-group`
- `selector`
- `history`
- `routing-rules`

```yaml
# ADMIN/API can access all tabs; USER can only access dashboard and history
pagePermissions:
  admin:
  user: dashboard_history
  api:
```

### Within-page controls

Regardless of `pagePermissions` configuration, certain controls within specific
pages are always restricted to the `ADMIN` role:

- **Cluster page**: The Create, Edit, and Delete backend buttons are hidden for
  non-ADMIN users. The Active toggle is also disabled.
- **Routing Rules page**: The Edit and Save buttons are hidden for non-ADMIN
  users.

These restrictions cannot be changed through `pagePermissions` configuration.

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
