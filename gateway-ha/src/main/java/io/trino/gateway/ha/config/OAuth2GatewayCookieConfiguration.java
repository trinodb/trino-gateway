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
package io.trino.gateway.ha.config;

import com.google.common.collect.ImmutableList;
import io.airlift.units.Duration;

import java.util.List;

public class OAuth2GatewayCookieConfiguration
{
    // Configuration initialization using dropwizard requires
    // instance method setters. Values are global, and can be accessed using static getters
    private List<String> routingPaths = ImmutableList.of("/oauth2");
    private List<String> deletePaths = ImmutableList.of("/logout", "/oauth2/logout");
    private Duration lifetime = Duration.valueOf("10m");

    public List<String> getDeletePaths()
    {
        return deletePaths;
    }

    public void setDeletePaths(List<String> deletePaths)
    {
        this.deletePaths = deletePaths;
    }

    public List<String> getRoutingPaths()
    {
        return routingPaths;
    }

    public void setRoutingPaths(List<String> routingPaths)
    {
        this.routingPaths = routingPaths;
    }

    public Duration getLifetime()
    {
        return lifetime;
    }

    public void setLifetime(String lifetime)
    {
        this.lifetime = Duration.valueOf(lifetime);
    }
}
