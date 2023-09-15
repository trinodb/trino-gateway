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

import io.airlift.units.Duration;

import java.util.List;

public class OAuth2GatewayCookieConfigurationPropertiesProvider
{
    private static final OAuth2GatewayCookieConfigurationPropertiesProvider instance = new OAuth2GatewayCookieConfigurationPropertiesProvider();

    private OAuth2GatewayCookieConfiguration oAuth2GatewayCookieConfiguration;

    private OAuth2GatewayCookieConfigurationPropertiesProvider()
    {}

    public static OAuth2GatewayCookieConfigurationPropertiesProvider getInstance()
    {
        return instance;
    }

    public void initialize(OAuth2GatewayCookieConfiguration oAuth2GatewayCookieConfiguration)
    {
        this.oAuth2GatewayCookieConfiguration = oAuth2GatewayCookieConfiguration;
    }

    public List<String> getDeletePaths()
    {
        ensureInitialized();
        return oAuth2GatewayCookieConfiguration.getDeletePaths();
    }

    public List<String> getRoutingPaths()
    {
        ensureInitialized();
        return oAuth2GatewayCookieConfiguration.getRoutingPaths();
    }

    public Duration getLifetime()
    {
        ensureInitialized();
        return oAuth2GatewayCookieConfiguration.getLifetime();
    }

    private void ensureInitialized()
    {
        if (oAuth2GatewayCookieConfiguration == null) {
            throw new IllegalStateException("getInstance.initialize(OAuth2GatewayCookieConfiguration) must be called before use");
        }
    }
}
