# Trino Gateway — Data Storage Schema Reference

This document describes every database table used by Trino Gateway, the purpose of each table,
all columns with types and constraints, and key architectural notes.

Trino Gateway uses **Flyway** for schema migrations and **JDBI v3** as its persistence layer.
Three databases are supported: **MySQL**, **PostgreSQL**, and **Oracle**.

---

## Migration History

Schema evolution is managed by Flyway. Scripts live under:

| Path | Description |
|------|-------------|
| `gateway-ha/src/main/resources/mysql/` | MySQL-specific V1–V3 scripts |
| `gateway-ha/src/main/resources/postgresql/` | PostgreSQL-specific V1–V3 scripts |
| `gateway-ha/src/main/resources/oracle/` | Oracle-specific V1–V3 scripts |

| Version | Script | Change |
|---------|--------|--------|
| V1 | `V1__create_schema.sql` | Creates all six application tables |
| V2 | `V2__add_routingGroup_to_query_history.sql` | Adds `routing_group VARCHAR(255)` to `query_history` |
| V3 | `V3__add_externalUrl_to_query_history.sql` | Adds `external_url VARCHAR(255)` to `query_history` |

Flyway is configured with `baselineOnMigrate=true` and `baselineVersion=0` so it can adopt
pre-existing databases. A `flyway_schema_history` table is automatically created and managed
by Flyway to track applied migrations.

---

## Tables

### 1. `gateway_backend`

**Purpose:** The central registry of Trino backend clusters. Every routing decision made by the
gateway ultimately resolves to a row in this table. The gateway health-checker marks backends as
active/inactive; the router selects an active backend that matches the requested routing group.

**Java model:** `io.trino.gateway.ha.persistence.dao.GatewayBackend`  
**DAO:** `io.trino.gateway.ha.persistence.dao.GatewayBackendDao`

```sql
CREATE TABLE IF NOT EXISTS gateway_backend (
    name          VARCHAR(256) PRIMARY KEY,
    routing_group VARCHAR(256),
    backend_url   VARCHAR(256),
    external_url  VARCHAR(256),
    active        BOOLEAN          -- NUMBER(1) on Oracle
);
```

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `name` | VARCHAR(256) | **PRIMARY KEY**, NOT NULL | Unique human-readable identifier for the backend cluster |
| `routing_group` | VARCHAR(256) | nullable | Label used to group backends (e.g. `"adhoc"`, `"etl"`). Clients select a group via the `X-Trino-Routing-Group` header |
| `backend_url` | VARCHAR(256) | nullable | Internal Trino coordinator URL used for proxying requests |
| `external_url` | VARCHAR(256) | nullable | Public-facing URL returned in query history for client redirects |
| `active` | BOOLEAN | nullable | `true` when the backend is healthy and eligible to receive traffic |

**Indexes:** Primary key on `name` only.

---

### 2. `query_history`

**Purpose:** Append-only audit log of every query proxied through the gateway. Used for: the admin
UI query history view, per-user query lookup, backend attribution (which cluster handled a given
`query_id`), and load-distribution analytics. Rows are automatically purged by a background task;
the retention window defaults to **4 hours** (configurable via `queryHistoryHoursRetention`) and
cleanup runs every **120 minutes**.

**Java model:** `io.trino.gateway.ha.persistence.dao.QueryHistory`  
**DAO:** `io.trino.gateway.ha.persistence.dao.QueryHistoryDao`

```sql
CREATE TABLE IF NOT EXISTS query_history (
    query_id      VARCHAR(256) PRIMARY KEY,
    query_text    VARCHAR(256),
    created       BIGINT,           -- NUMBER on Oracle
    backend_url   VARCHAR(256),
    user_name     VARCHAR(256),
    source        VARCHAR(256),
    routing_group VARCHAR(255),     -- added in V2
    external_url  VARCHAR(255)      -- added in V3
);

CREATE INDEX query_history_created_idx ON query_history (created);
```

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `query_id` | VARCHAR(256) | **PRIMARY KEY**, NOT NULL | Trino query ID (e.g. `20240101_120000_00001_xxxxx`) |
| `query_text` | VARCHAR(256) | nullable | SQL text of the query, truncated to 256 characters |
| `created` | BIGINT | nullable | Unix epoch timestamp in **milliseconds** when the query was received |
| `backend_url` | VARCHAR(256) | nullable | Internal URL of the backend that executed the query |
| `user_name` | VARCHAR(256) | nullable | Trino username extracted from the request |
| `source` | VARCHAR(256) | nullable | Client source identifier (e.g. application name) |
| `routing_group` | VARCHAR(255) | nullable | Routing group the query was dispatched to *(added V2)* |
| `external_url` | VARCHAR(255) | nullable | Public URL of the backend *(added V3)* |

**Indexes:**
- Primary key on `query_id`
- `query_history_created_idx` on `created` — used for time-range lookups and the purge DELETE

---

### 3. `resource_groups`

**Purpose:** Mirrors the Trino native resource-group configuration. The gateway manages this table
and propagates resource-group definitions to connected Trino clusters, controlling query scheduling,
memory quotas, concurrency limits, and CPU quotas. Supports a self-referential parent hierarchy
(a group can be a child of another group).

**Java model:** `io.trino.gateway.ha.persistence.dao.ResourceGroups`  
**DAO:** `io.trino.gateway.ha.persistence.dao.ResourceGroupsDao`

```sql
-- MySQL
CREATE TABLE IF NOT EXISTS resource_groups (
    resource_group_id      BIGINT NOT NULL AUTO_INCREMENT,  -- SERIAL on PostgreSQL
    name                   VARCHAR(250) NOT NULL UNIQUE,
    parent                 BIGINT NULL,
    jmx_export             BOOLEAN NULL,
    scheduling_policy      VARCHAR(128) NULL,
    scheduling_weight      INT NULL,
    soft_memory_limit      VARCHAR(128) NOT NULL,
    max_queued             INT NOT NULL,
    hard_concurrency_limit INT NOT NULL,
    soft_concurrency_limit INT NULL,
    soft_cpu_limit         VARCHAR(128) NULL,
    hard_cpu_limit         VARCHAR(128) NULL,
    environment            VARCHAR(128) NULL,
    PRIMARY KEY (resource_group_id),
    FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id)
);
```

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `resource_group_id` | BIGINT (auto-increment) | **PRIMARY KEY**, NOT NULL | Surrogate key for the resource group |
| `name` | VARCHAR(250) | NOT NULL, UNIQUE | Human-readable name of the resource group |
| `parent` | BIGINT | nullable, FK → `resource_groups.resource_group_id` | Self-referential parent group; `NULL` means top-level |
| `jmx_export` | BOOLEAN | nullable | Whether to expose this group's metrics over JMX |
| `scheduling_policy` | VARCHAR(128) | nullable | Scheduling algorithm: `fair`, `weighted`, `weighted_fair`, `query_priority` |
| `scheduling_weight` | INT | nullable | Relative weight when `scheduling_policy = 'weighted'` |
| `soft_memory_limit` | VARCHAR(128) | **NOT NULL** | Soft memory cap (e.g. `"80%"`, `"100GB"`) |
| `max_queued` | INT | **NOT NULL** | Maximum number of queries that may queue before being rejected |
| `hard_concurrency_limit` | INT | **NOT NULL** | Hard cap on simultaneously running queries |
| `soft_concurrency_limit` | INT | nullable | Soft concurrency cap; queries above this level are deprioritised |
| `soft_cpu_limit` | VARCHAR(128) | nullable | Soft CPU-time cap per `cpu_quota_period` |
| `hard_cpu_limit` | VARCHAR(128) | nullable | Hard CPU-time cap per `cpu_quota_period` |
| `environment` | VARCHAR(128) | nullable | Environment tag used to filter which groups apply to a given cluster |

**Indexes:** Primary key on `resource_group_id`; unique index on `name`.
Oracle DDL adds `ON DELETE CASCADE` to the FK.

---

### 4. `selectors`

**Purpose:** Regex-based assignment rules that map incoming queries to resource groups. The gateway
evaluates selectors in descending `priority` order; the first selector whose non-null fields all
match the query's attributes wins, and the query is assigned to the corresponding `resource_group_id`.
This replicates Trino's native selector mechanism.

**Java model:** `io.trino.gateway.ha.persistence.dao.Selectors`  
**DAO:** `io.trino.gateway.ha.persistence.dao.SelectorsDao`

```sql
CREATE TABLE IF NOT EXISTS selectors (
    resource_group_id          BIGINT NOT NULL,
    priority                   BIGINT NOT NULL,
    user_regex                 VARCHAR(512),
    source_regex               VARCHAR(512),
    query_type                 VARCHAR(512),
    client_tags                VARCHAR(512),
    selector_resource_estimate VARCHAR(1024),
    FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id)
);
```

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `resource_group_id` | BIGINT | NOT NULL, FK → `resource_groups.resource_group_id` | Target resource group when this selector matches |
| `priority` | BIGINT | NOT NULL | Evaluation order — higher value = higher priority |
| `user_regex` | VARCHAR(512) | nullable | Java regex matched against the query submitter's username |
| `source_regex` | VARCHAR(512) | nullable | Java regex matched against the client source identifier |
| `query_type` | VARCHAR(512) | nullable | Exact match on query type (`DATA_DEFINITION`, `DELETE`, `DESCRIBE`, `EXPLAIN`, `INSERT`, `SELECT`) |
| `client_tags` | VARCHAR(512) | nullable | Exact match on the set of client tags |
| `selector_resource_estimate` | VARCHAR(1024) | nullable | Resource estimate predicate (e.g. `"executionTime > 5m"`) |

**Indexes:** Only the implicit FK index.
**No primary key** — the DAO identifies rows by the combination of all columns, using
`IS NOT DISTINCT FROM` for nullable fields in UPDATE/DELETE statements.

---

### 5. `resource_groups_global_properties`

**Purpose:** Stores global Trino resource-group properties. Currently only one property is valid:
`cpu_quota_period`, which defines the rolling window over which CPU quotas (`soft_cpu_limit` /
`hard_cpu_limit` in `resource_groups`) are measured. Equivalent to the global `cpu-quota-period`
setting in a standalone Trino resource-groups configuration file.

**Java model:** `io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalProperties`  
**DAO:** `io.trino.gateway.ha.persistence.dao.ResourceGroupsGlobalPropertiesDao`

```sql
CREATE TABLE IF NOT EXISTS resource_groups_global_properties (
    name  VARCHAR(128) NOT NULL PRIMARY KEY,
    value VARCHAR(512) NULL,
    CHECK (name IN ('cpu_quota_period'))
);
```

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `name` | VARCHAR(128) | **PRIMARY KEY**, NOT NULL, CHECK `IN ('cpu_quota_period')` | Property name (currently only `cpu_quota_period` is permitted) |
| `value` | VARCHAR(512) | nullable | Property value, e.g. `"1h"` |

**Indexes:** Primary key on `name`.

---

### 6. `exact_match_source_selectors`

**Purpose:** Complements the regex-based `selectors` table with exact-string source matching.
When a query's `source`, `environment`, and optionally `query_type` exactly match a row in this
table, the query is assigned to the specified `resource_group_id` without regex evaluation.
Provides faster and more deterministic routing for known, well-defined sources.

> **Design note:** `resource_group_id` is stored as `VARCHAR(256)` in this table, whereas
> `resource_groups.resource_group_id` is `BIGINT`. This inconsistency (noted in the source
> code comments) means there is no enforced foreign-key relationship between the two tables.

**Java model:** `io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectors`  
**DAO:** `io.trino.gateway.ha.persistence.dao.ExactMatchSourceSelectorsDao`

```sql
-- PostgreSQL
CREATE TABLE IF NOT EXISTS exact_match_source_selectors (
    resource_group_id VARCHAR(256) NOT NULL,
    update_time       TIMESTAMP NOT NULL,
    source            VARCHAR(512) NOT NULL,
    environment       VARCHAR(128),
    query_type        VARCHAR(128),
    PRIMARY KEY (environment, source, query_type),
    UNIQUE (source, environment, query_type, resource_group_id)
);
```

**MySQL differences:** `update_time` is `DATETIME`; PRIMARY KEY and UNIQUE use prefix lengths
(`source(128)`, `query_type(128)`) to satisfy index-length limits.  
**Oracle differences:** `query_type` is `VARCHAR(512)`; PRIMARY KEY is
`(environment, source, resource_group_id)`.

| Column | Type | Constraint | Description |
|--------|------|------------|-------------|
| `resource_group_id` | VARCHAR(256) | NOT NULL | Identifies the target resource group by string name (not FK-enforced) |
| `update_time` | TIMESTAMP / DATETIME | NOT NULL | Timestamp of the last update to this selector row |
| `source` | VARCHAR(512) | NOT NULL | Exact source string to match (e.g. application name) |
| `environment` | VARCHAR(128) | nullable | Environment tag to scope the match |
| `query_type` | VARCHAR(128/512) | nullable | Exact query type match (see `selectors.query_type` for valid values) |

**Indexes:**
- PRIMARY KEY on `(environment, source, query_type)` — PostgreSQL / MySQL
- UNIQUE on `(source, environment, query_type, resource_group_id)`

---

## Entity-Relationship Overview

```
resource_groups  (self-referential hierarchy via parent FK)
    │
    ├── selectors.resource_group_id            (BIGINT FK — regex-based routing rules)
    │
    └── resource_groups_global_properties      (independent — global CPU quota config)

exact_match_source_selectors                   (no FK — VARCHAR resource_group_id mismatch)

gateway_backend                                (independent — Trino cluster registry)

query_history                                  (independent — append-only audit log)
```

---

## Architectural Notes

| Topic | Detail |
|-------|--------|
| **ORM layer** | JDBI v3 with `SqlObjectPlugin`. No JPA / Hibernate. Models are plain Java records with `@ColumnName` annotations. SQL is written inline in `@SqlQuery` / `@SqlUpdate` DAO methods. |
| **Supported databases** | MySQL, PostgreSQL, Oracle. Detected at startup from the JDBC URL prefix in `FlywayMigration.getLocation()`. H2 appears only in legacy/test fixtures and is not supported for production. |
| **Multi-database routing** | `JdbcConnectionManager.getJdbi(routingGroupDatabase)` can target a different database for resource-group CRUD, enabling per-routing-group Trino configuration. |
| **Query history retention** | Background task deletes rows from `query_history` older than `queryHistoryHoursRetention` hours (default: **4 h**). Runs every **120 minutes**. Index `query_history_created_idx` makes this DELETE efficient. |
| **No PK on `selectors`** | Rows are identified by all seven columns. The DAO uses `IS NOT DISTINCT FROM` for nullable-column equality in UPDATE/DELETE statements. |
| **`exact_match_source_selectors.resource_group_id` type mismatch** | This column is `VARCHAR(256)`, not `BIGINT`, so there is no foreign-key constraint linking it to `resource_groups`. This is a known design inconsistency acknowledged in the source code comments. |

---

*Generated from source analysis of `gateway-ha/src/main/resources/*/V*.sql` and
`gateway-ha/src/main/java/io/trino/gateway/ha/persistence/dao/`.*
