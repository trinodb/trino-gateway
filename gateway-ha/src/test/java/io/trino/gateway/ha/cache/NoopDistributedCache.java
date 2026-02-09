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

public class NoopDistributedCache
        implements DistributedCache
{
    @Override
    public Optional<String> get(String key)
    {
        return Optional.empty();
    }

    @Override
    public void set(String key, String value) {}

    @Override
    public void invalidate(String key) {}

    @Override
    public boolean isEnabled()
    {
        return false;
    }
}
