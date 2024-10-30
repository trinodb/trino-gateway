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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class OAuth2GatewayCookieProvider
        extends AbstractModule
{
    private final List<String> deletePaths;
    private final Duration ttl;

    @Inject
    public OAuth2GatewayCookieProvider(HaGatewayConfiguration configuration)
    {
        requireNonNull(configuration.getOauth2GatewayCookieConfiguration(), "OAuth2GatewatCookieConfiguration is null");
        this.deletePaths = ImmutableList.copyOf(configuration.getOauth2GatewayCookieConfiguration().getDeletePaths());
        this.ttl = configuration.getOauth2GatewayCookieConfiguration().getLifetime();
    }

    public OAuth2GatewayCookie getOAuth2GatewayCookie(String backend)
    {
        return new OAuth2GatewayCookie(backend, deletePaths, ttl);
    }
}
