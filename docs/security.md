**Trino Gateway documentation**

<table>
  <tr>
    <td><b><a href="design.md">Design</a></b></td>
    <td><a href="development.md">Development</a></td>
    <td><b><a href="security.md">Security</a></b></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><a href="release-notes.md">Release notes</a></td>
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

```
presetUsers:
  user1:
    password: <password>
    privileges: "lb_admin, lb_user"
  user2:
    password: <password>
    privileges: "lb_api"
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
  admin: 'lb_admin'
  user: 'lb_user'
  api: 'lb_api'
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
```
