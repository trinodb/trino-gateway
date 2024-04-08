**Trino Gateway documentation**

<table>
  <tr>
    <td>
      <img src="./assets/logos/trino-gateway-v.png"/>
    </td>
    <td>
      <ul>
        <li><a href="quickstart.md">Quickstart</a></li>
        <li><a href="installation.md">Installation</a></li>
        <li><a href="security.md">Security</a></li>
        <li><a href="operation.md">Operation</a></li>
      </ul>
    </td>
    <td>
      <ul>
        <li><a href="gateway-api.md">Gateway API</a></li>
        <li><a href="resource-groups-api.md">Resource groups API</a></li>
        <li><a href="routing-rules.md">Routing rules</a></li>
      </ul>
    </td>
    <td>
      <ul>
        <li><a href="design.md">Design</a></li>
        <li><a href="development.md">Development</a></li>
        <li><a href="release-notes.md">Release notes</a></li>
        <li><a href="references.md">References</a></li>
      </ul>
    </td>
  </tr>
</table>

# Security

Trino Gateway has its own security with its own authentication and authorization.
These features are used only to authenticate and authorize its user interface and
the APIs. All Trino-related requests are passed through to the Trino cluster
without any authentication or authorization check in Trino Gateway.

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

Trino Gateway supports the following roles in regex string format (for API calls):

- admin : Allows access to the Editor tab, which can be used to configure the
  backends
- user : Allows access to the rest of the website
- api : Allows access to rest apis to configure the backends

Users with attributes matching each role regex will be giving the privileges of that role (admin, user, or api) for Api calls. You can use the preset users defined in the yaml file.
LDAP Authorization is also supported by adding user attribute configs in file.

```
# Roles should be in regex format
authorization:
  admin: (.*)ADMIN(.*)
  user: (.*)USER(.*)
  api: (.*)API(.*)
  ldapConfigPath: '<ldap_config_path>'
```

**Caution:**
The regex pattern matching for roles only works for API calls, and not on the webapp. 
The webapp only supports ADMIN, USER, and API roles. 
For instance, in the case that a user has `privileges: SUPER_USER_API` and the authorization section includes `admin: (.*)SUPER(.*)` the user will *not* have ADMIN role privileges in the webapp. 

To match the behavior of the API with the webapp, just use the previous configuration example for the `authorization` section.

### LDAP Authorization
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
  groupAdminRoleMap: <regex to map group => role>
  groupUserRoleMap: <regex to map group => role>
  groupApiRoleMap: <regex to map group => role>
```

The group mappings will grant ADMIN, USER, or API roles when the `groupMemberAttribute` of the user matches the corresponding regular expression.
- Check out [LDAPTestConfig.yml](https://github.com/trinodb/trino-gateway/blob/main/gateway-ha/src/test/resources/auth/ldapTestConfig.yml) file for a configuration example


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

