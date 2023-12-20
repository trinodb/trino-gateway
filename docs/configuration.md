**Trino Gateway documentation**

<table>
  <tr>
    <td><b><a href="configuration.md">Configuration</a></b></td>
    <td><a href="design.md">Design</a></td>
    <td><a href="development.md">Development</a></td>
    <td><a href="quickstart.md">Quickstart</a></td>
    <td><a href="security.md">Security</a></td>
    <td><a href="operation.md">Operation</a></td>
    <td><a href="gateway-api.md">Gateway API</a></td>
    <td><a href="resource-groups-api.md">Resource groups API</a></td>
    <td><a href="routing-rules.md">Routing rules</a></td>
    <td><a href="references.md">References</a></td>
    <td><a href="release-notes.md">Release notes</a></td>
  </tr>
</table>

# Configuration

The Trino Gateway is configured by passing a yaml when running the start command.
```shell
java -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED -jar gateway-ha.jar server gateway-config.yml
```
Each component of the Trino Gateway will have a corresponding node in the configuration yaml. 

## Proxying additional paths

By default, Trino Gateway only proxies requests to paths starting with 
`/v1/statement`, `/v1/query`, `/ui`, `/v1/info`, `/v1/node`, 
`/ui/api/stats` and `/oauth`. 

If you want to proxy additional paths, 
you can add them by adding the `extraWhitelistPaths` node to your gateway 
configuration yaml:

```yaml
extraWhitelistPaths:
  - "/ui/insights"
  - "/api/v1/biac"
  - "/api/v1/dataProduct"
  - "/api/v1/dataproduct"
  - "/ext/faster"
```

This example enables additional proxying of any requests to path starting with the specified paths. 