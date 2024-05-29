# Resource groups API

For resource group and selector apis, we can now specify a query parameter with
the request supporting multiple trino databases for different trino backends.
This allows a user to configure a db for every trino backend with their own
resource groups and selector tables. To use this, just specify the query
parameter ?useSchema=<schemaname> to the request. Example, to list all resource
groups,

```bash
curl -X GET http://localhost:8080/trino/resourcegroup/read/{INSERT_ID_HERE}?useSchema=newdatabasename
```

## Add a resource group

To add a single resource group, specify all relevant fields in the body.
Resource group id should not be specified since the database should
autoincrement it.

```bash
curl -X POST http://localhost:8080/trino/resourcegroup/create \
 -d '{
        "name": "resourcegroup1", \
        "softMemoryLimit": "100%", \
        "maxQueued": 100, \
        "softConcurrencyLimit": 100, \
        "hardConcurrencyLimit": 100, \
        "schedulingPolicy": null, \
        "schedulingWeight": null, \
        "jmxExport": null, \
        "softCpuLimit": null, \
        "hardCpuLimit": null, \
        "parent": null, \
        "environment": "test" \
    }'
```

## Get existing resource group(s)

If no resourceGroupId (type long) is specified, then all existing resource
groups are fetched.

```bash
curl -X GET http://localhost:8080/trino/resourcegroup/read/{INSERT_ID_HERE}
```

## Update a resource group

Specify all columns in the body, which will overwrite properties for the
resource group with that specific resourceGroupId.

```bash
curl -X POST http://localhost:8080/trino/resourcegroup/update \
 -d '{  "resourceGroupId": 1, \
        "name": "resourcegroup_updated", \
        "softMemoryLimit": "80%", \
        "maxQueued": 50, \
        "softConcurrencyLimit": 40, \
        "hardConcurrencyLimit": 60, \
        "schedulingPolicy": null, \
        "schedulingWeight": null, \
        "jmxExport": null, \
        "softCpuLimit": null, \
        "hardCpuLimit": null, \
        "parent": null, \
        "environment": "test" \
    }'
```

## Delete a resource group

To delete a resource group, specify the corresponding resourceGroupId (type
long).

```bash
curl -X POST http://localhost:8080/trino/resourcegroup/delete/{INSERT_ID_HERE}
```

## Add a selector

To add a single selector, specify all relevant fields in the body. Resource
group id should not be specified since the database should autoincrement it.

```bash
curl -X POST http://localhost:8080/trino/selector/create \
 -d '{
        "priority": 1, \
        "userRegex": "selector1", \
        "sourceRegex": "resourcegroup1", \
        "queryType": "insert" \
     }'
```

## Get existing selectors(s)

If no resourceGroupId (type long) is specified, then all existing selectors are
fetched.

```bash
curl -X GET http://localhost:8080/trino/selector/read/{INSERT_ID_HERE}
```

## Update a selector

To update a selector, the existing selector must be specified with all relevant
fields under "current". The updated version of that selector is specified under
"update", with all relevant fields included. If the selector under "current"
does not exist, a new selector will be created with the details under "update".
Both "current" and "update" must be included to update a selector.

```bash
curl -X POST http://localhost:8080/trino/selector/update \
 -d '{  "current": {
            "resourceGroupId": 1, \
            "priority": 1, \
            "userRegex": "selector1", \
            "sourceRegex": "resourcegroup1", \
            "queryType": "insert" \
        },
        "update":  {
            "resourceGroupId": 1, \
            "priority": 2, \
            "userRegex": "selector1_updated", \
            "sourceRegex": "resourcegroup1", \
            "queryType": null \
        }
}'
```

## Delete a selector

To delete a selector, specify all relevant fields in the body.

```bash
curl -X POST http://localhost:8080/trino/selector/delete \
 -d '{  "resourceGroupId": 1, \
        "priority": 2, \
        "userRegex": "selector1_updated", \
        "sourceRegex": "resourcegroup1", \
        "queryType": null \
     }'
```

## Add a global property

To add a single global property, specify all relevant fields in the body.

```bash
curl -X POST http://localhost:8080/trino/globalproperty/create \
 -d '{
        "name": "cpu_quota_period", \
        "value": "1h" \
     }'
```

## Get existing global properties

If no name (type String) is specified, then all existing global properties are
fetched.

```bash
curl -X GET http://localhost:8080/trino/globalproperty/read/{INSERT_NAME_HERE}
```

## Update a global property

Specify all columns in the body, which will overwrite properties for the global
property with that specific name.

```bash
curl -X POST http://localhost:8080/trino/globalproperty/update \
 -d '{
        "name": "cpu_quota_period", \
        "value": "2h" \
     }'
```

## Delete a global property

To delete a global property, specify the corresponding name (type String).

```bash
curl -X POST http://localhost:8080/trino/globalproperty/delete/{INSERT_NAME_HERE}
```
