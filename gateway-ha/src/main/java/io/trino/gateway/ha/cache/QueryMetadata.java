/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

/**
 * Immutable data object representing all cached metadata for a query.
 * This consolidates backend, routing group, and external URL into a single entity,
 * enabling atomic cache operations and reducing network overhead.
 *
 * <p>Fields can be null to support partial updates where not all metadata is known initially.
 * Use static factory methods (withBackend, withRoutingGroup, withExternalUrl) to create
 * partial metadata objects, and the merge() method to combine with existing data.
 *
 * <p>This class uses Jackson annotations for JSON serialization/deserialization
 * to support distributed caching via Valkey/Redis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryMetadata(
        @JsonProperty("backend") String backend,
        @JsonProperty("routingGroup") String routingGroup,
        @JsonProperty("externalUrl") String externalUrl)
{
    /**
     * Constructor for Jackson deserialization and manual construction.
     * Fields are nullable to support partial metadata scenarios.
     */
    @JsonCreator
    public QueryMetadata {}

    /**
     * Creates a QueryMetadata with only the backend field set.
     * Used when only backend information is known (e.g., from backend search).
     */
    public static QueryMetadata withBackend(String backend)
    {
        requireNonNull(backend, "backend is null");
        return new QueryMetadata(backend, null, null);
    }

    /**
     * Creates a QueryMetadata with only the routing group field set.
     * Used when only routing group information is known.
     */
    public static QueryMetadata withRoutingGroup(String routingGroup)
    {
        requireNonNull(routingGroup, "routingGroup is null");
        return new QueryMetadata(null, routingGroup, null);
    }

    /**
     * Creates a QueryMetadata with only the external URL field set.
     * Used when only external URL information is known.
     */
    public static QueryMetadata withExternalUrl(String externalUrl)
    {
        requireNonNull(externalUrl, "externalUrl is null");
        return new QueryMetadata(null, null, externalUrl);
    }

    /**
     * Merges this metadata with another, preferring non-null values from the other.
     * This enables copy-on-write partial updates:
     * <pre>
     * QueryMetadata existing = cache.get(queryId);
     * QueryMetadata updated = existing.merge(QueryMetadata.withBackend(newBackend));
     * cache.put(queryId, updated);
     * </pre>
     *
     * @param other the metadata to merge with this one
     * @return a new QueryMetadata with merged values (non-null values from other take precedence)
     */
    public QueryMetadata merge(QueryMetadata other)
    {
        requireNonNull(other, "other is null");
        return new QueryMetadata(
                other.backend != null ? other.backend : this.backend,
                other.routingGroup != null ? other.routingGroup : this.routingGroup,
                other.externalUrl != null ? other.externalUrl : this.externalUrl);
    }

    /**
     * Returns true if all fields are null (empty metadata).
     */
    @JsonIgnore
    public boolean isEmpty()
    {
        return backend == null && routingGroup == null && externalUrl == null;
    }

    /**
     * Returns true if all fields are non-null (complete metadata).
     */
    @JsonIgnore
    public boolean isComplete()
    {
        return backend != null && routingGroup != null && externalUrl != null;
    }
}
