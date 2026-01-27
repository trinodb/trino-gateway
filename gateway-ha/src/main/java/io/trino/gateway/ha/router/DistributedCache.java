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
package io.trino.gateway.ha.router;

import java.util.Optional;

/**
 * Interface for distributed caching operations.
 * Provides a simple key-value cache abstraction for sharing data across gateway instances.
 */
public interface DistributedCache
{
    /**
     * Retrieves a value from the cache.
     *
     * @param key the cache key
     * @return Optional containing the value if present, empty otherwise
     */
    Optional<String> get(String key);

    /**
     * Stores a value in the cache with TTL.
     *
     * @param key the cache key
     * @param value the value to store
     * @param ttlSeconds time-to-live in seconds
     * @return true if successful, false otherwise
     */
    boolean set(String key, String value, long ttlSeconds);

    /**
     * Stores a value in the cache without TTL (persists until explicitly deleted).
     *
     * @param key the cache key
     * @param value the value to store
     * @return true if successful, false otherwise
     */
    boolean set(String key, String value);

    /**
     * Removes a value from the cache.
     *
     * @param key the cache key
     * @return true if the key existed and was deleted, false otherwise
     */
    boolean delete(String key);

    /**
     * Removes multiple values from the cache.
     *
     * @param keys the cache keys to delete
     * @return number of keys that were deleted
     */
    long delete(String... keys);

    /**
     * Checks if the cache is available and healthy.
     *
     * @return true if cache is operational, false otherwise
     */
    boolean isHealthy();

    /**
     * Closes the cache connection and releases resources.
     */
    void close();
}
