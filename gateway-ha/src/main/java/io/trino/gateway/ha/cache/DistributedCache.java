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

import java.util.Optional;

/**
 * Interface for distributed caching of query metadata across gateway instances.
 * Implementations should handle serialization/deserialization of QueryMetadata objects.
 */
public interface DistributedCache
{
    /**
     * Retrieves query metadata from the distributed cache.
     *
     * @param queryId the query identifier
     * @return Optional containing the QueryMetadata if found, empty otherwise
     */
    Optional<QueryMetadata> get(String queryId);

    /**
     * Stores query metadata in the distributed cache.
     *
     * @param queryId the query identifier
     * @param metadata the query metadata to store
     */
    void set(String queryId, QueryMetadata metadata);

    /**
     * Removes query metadata from the distributed cache.
     *
     * @param queryId the query identifier
     */
    void invalidate(String queryId);
}
