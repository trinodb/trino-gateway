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

import javax.crypto.SecretKey;

public class GatewayCookieConfigurationPropertiesProvider
{
    private static final GatewayCookieConfigurationPropertiesProvider instance = new GatewayCookieConfigurationPropertiesProvider();
    private GatewayCookieConfiguration gatewayCookieConfiguration;

    private GatewayCookieConfigurationPropertiesProvider()
    {}

    public void initialize(GatewayCookieConfiguration gatewayCookieConfiguration)
    {
        if (gatewayCookieConfiguration.isEnabled() && gatewayCookieConfiguration.getCookieSigningKey() == null) {
            throw new IllegalArgumentException("gatewayCookieConfiguration.cookieSigningSecret must be provided when cookies are enabled");
        }
        this.gatewayCookieConfiguration = gatewayCookieConfiguration;
    }

    public static GatewayCookieConfigurationPropertiesProvider getInstance()
    {
        return instance;
    }

    public boolean isEnabled()
    {
        ensureInitialized();
        return gatewayCookieConfiguration.isEnabled();
    }

    public SecretKey getCookieSigningKey()
    {
        ensureInitialized();
        return gatewayCookieConfiguration.getCookieSigningKey();
    }

    private void ensureInitialized()
    {
        if (gatewayCookieConfiguration == null) {
            throw new IllegalStateException("getInstance.initialize(GatewayCookieConfiguration) must be called before use");
        }
    }
}
