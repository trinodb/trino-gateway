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

### Form/Basic Auth

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

```
authentication:
  defaultType: "form"
  form:
    ldapConfigPath: <ldap_config_path>
```


## Authorization

The roles supported by trino load balancer

- admin : Allows access to the Editor tab, which can be used to configure the
  backends

- user : Allows access to the rest of the website

- api : Allows access to to rest apis to configure the backends

Users with attributes next to the role will be giving those privileges the
users. User attributes from LDAP is supported or you can use the preset users
defined in the yaml file. Authorization is supported via LDAP user attributes

```
authorization:
  admin: 'lb_admin'
  user: 'lb_user'
  api: "lb_api"
  ldapHost:
  ldapPort:
  ldapBindDn:
  ldapSearch:
  ldapPassword:
```



