# Routers

Trino Gateway offers two entry-level router options, providing users with a 
straightforward and easy-to-use starting point for their routing needs. The 
routers make the decision based on the clusters load reported in
[ClusterStats](https://github.com/trinodb/trino-gateway/blob/main/gateway-ha/src/main/java/io/trino/gateway/ha/clustermonitor/ClusterStats.java).

## StochasticRoutingManager

This primary routing mechanism employs a straightforward, decentralized 
approach, dispatching incoming queries in a random manner without utilizing 
advanced optimization techniques.

## QueryCountBasedRouterProvider

This routing mechanism utilizes near real-time, user-level cluster load
statistics. It uses running queries count and query queue lengths to determine
the most suitable cluster for each individual user. With is information it 
directs queries to the least loaded cluster for that user, optimizing the 
likelihood of successful execution.

## Adding a routing mechanism

To enhance Trino Gateway's capabilities, you can create and contribute new and
advanced router modules with intelligent routing features. To integrate a new
router, you need to create a provider module that can be configured via the 
configuration file. This allows for seamless addition of new routers without
disrupting existing functionality.

### Add router Provider module

Use the following steps to incorporate a new routing mechanism with advanced
capabilities:

- Derive a class from `RouterBaseModule`
- The module must instantiate the router and hold a reference to it.
- Add the module name to the `modules` section of the configuration file to load 
  the provider module and make the new router available.
- For example, `QueryCountBasedRouterProvider` and refer to the config file in 
  the following sections.

### Add router class

Use the following steps to create a new router:

- Derive a class from `StochasticRoutingManager` to create the router that 
  does the actual work.
- Override the methods `provideAdhocBackend` and `provideBackendForRoutingGroup`
  and implement the new smarter logic
- The router listens to the list of `ClusterStats` via the`updateBackEndStats`
  method.
- This method is called on regular intervals defined in the config 
  parameter `monitor=>taskDelay`.
- Each element in the list corresponds to each backend cluster.
- Only the stats from the healthy cluster are reported, unhealthy clusters are
  not included in the list. If you have three cluster backends and one is
  unhealthy, then the parameter `List<ClusterStats> stats` has only two
  elements.
- To get the cluster stats set the parameter
  `clusterStatsConfiguration=>monitorType` to `UI_API` or `JDBC` which in turn
  needs the setup of `backendState` section in the config file.

### Configuration file reference
    
```yaml
backendState:
  username: <usernme>
  password: <password>
  ssl: <false/true>

clusterStatsConfiguration:
  monitorType: UI_API

modules:
  - io.trino.gateway.ha.module.QueryCountBasedRouterProvider
```


