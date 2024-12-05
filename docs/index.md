# Overview

<img src="./assets/logos/trino-gateway-v.png" style="float: right"/>

Trino Gateway is a load balancer, proxy server, and configurable routing 
gateway for multiple [Trino](https://trino.io) clusters.

## Use cases, advantages, and features

* Use of a single connections URL for client tool users with workload
  distribution across multiple Trino clusters.
* Automatic routing of queries to dedicated Trino clusters for specific
  workloads or specific queries and data sources.
* No-downtime upgrades for Trino clusters behind the Trino Gateway in a
  blue/green model or canary deployment model.
* Transparent change of capacity of Trino clusters without user interruptions.

## High-level architecture

![High-level architecture](assets/high-level-architecture.png)

Find more information in our [resources page](resources.md), the
[users page](users.md), and the rest  of the documentation.
