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
 * No-op distributed cache for testing purposes.
 */
public class NoopDistributedCache
        implements DistributedCache
{
    @Override
    public Optional<String> get(String key)
    {
        return Optional.empty();
    }

    @Override
    public boolean set(String key, String value, long ttlSeconds)
    {
        return true;
    }

    @Override
    public boolean set(String key, String value)
    {
        return true;
    }

    @Override
    public boolean delete(String key)
    {
        return true;
    }

    @Override
    public long delete(String... keys)
    {
        return keys.length;
    }

    @Override
    public boolean isHealthy()
    {
        return true;
    }

    @Override
    public void close()
    {
        // no-op
    }
}
