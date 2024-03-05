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
        <li><a href="routing-logic.md">Routing logic</a></li>
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

# Design

Trino Gateway is composed of the following main components:

1. **BaseApp** provides boilerplate code to add/remove pluggable components
   with config and metrics registration module. Located in the 
   `io.trino.gateway.baseapp` package of the `gateway-ha` module.

![BaseApp Class Diagram](assets/BaseApp-classes.png)

2. **ProxyServer** is a library built on top of jetty proxy which provides a
   proxy server with a pluggable proxy-handler. Located in the
   `io.trino.gateway.proxyserver` package of the `gateway-ha` module.

![ProxyServer Class Diagram](assets/ProxyServer-classes.png)

3. **Trino Gateway** acts as container for proxy-server and plugs in
   ProxyHandlers to provide proxy, routing and load balancing functionalities. It
   also exposes few end points and UI to activate, deactivate backends and view
   query history for recently submitted queries. Located in the
   `io.trino.gateway` package of the `gateway-ha` module.

![TrinoGateway Class Diagram](assets/TrinoGateway-classes.png)

