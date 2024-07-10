# Routers
Gateway offers two entry-level router options, providing users with
a straightforward and easy-to-use starting point for their routing needs.

## StochasticRoutingManager
The gateway's primary routing mechanism employs a straightforward,
decentralized approach, dispatching incoming queries in a random manner
without utilizing advanced optimization techniques.

## QueryCountBasedRouterProvider
This router utilizes near real-time user-level cluster load statistics,
looking at running queries count and queue lengths to determine
the most suitable cluster for each individual user.
It then directs queries to the least loaded cluster (for that user),
optimizing the likelihood of successful execution.

## How to add a new router?
To enhance the gateway's capabilities, we invite developers to contribute
new and advanced router modules with intelligent routing features.
To integrate a new router, you will need to create a provider module
that can be configured via the configuration file. This allows for seamless
addition of new routers without disrupting existing functionality.

### Router Provider module
To incorporate a new router with advanced capabilities, follow these steps:
- Derive a class from `RouterBaseModule`
- This is the module instantiates the router and holds a reference to it.
- To integrate the new router, you must first add the module name to the
'modules' section of the configuration file. This tells the gateway to load
the provider module and make the new router available.
- e.g. `QueryCountBasedRouterProvider`

### Router class
- Derive a class from `StochasticRoutingManager`,
 this is the router which does the actual work
- Override the methods `provideAdhocBackend` and `provideBackendForRoutingGroup`
 for the new smarter logic
- The router listens to the list of `ClusterStats`
via the `updateBackEndStats` method.
- This method gets called on regular intervals defined
in the config parameter `monitor=>taskDelaySeconds`
- Each element in the list corresponds to each backend cluster
- Only the stats from the healthy cluster are reported, unhealthy clusters
are not included in the list. so if you have 3 cluster backends and 1 is
unhealthy then the pamameter `List<ClusterStats> stats` will have only 2 elements.
- In order to get the cluster stats use set the parameter
`clusterStatsConfiguration=>monitorType` to `UI_API` or `JDBC`
which in turn needs the setup of `backendState` section in the config file.

### Config file reference
```
backendState:
  username: <usernme>
  password: <password>
  ssl: <false/true>

clusterStatsConfiguration:
  monitorType: UI_API

monitor:
  taskDelaySeconds: 10

modules:
  - io.trino.gateway.ha.module.QueryCountBasedRouterProvider
```






