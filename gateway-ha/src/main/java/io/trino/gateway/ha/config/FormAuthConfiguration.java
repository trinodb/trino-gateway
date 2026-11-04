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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;

public class FormAuthConfiguration
{
    private SelfSignKeyPairConfiguration selfSignKeyPair;
    private String ldapConfigPath;
    // Absolute lifetime of a login session, counted from login; activity does not extend it
    private Duration sessionTimeout = new Duration(30, MINUTES);

    public FormAuthConfiguration(SelfSignKeyPairConfiguration selfSignKeyPair, String ldapConfigPath)
    {
        this.selfSignKeyPair = selfSignKeyPair;
        this.ldapConfigPath = ldapConfigPath;
    }

    public FormAuthConfiguration() {}

    public SelfSignKeyPairConfiguration getSelfSignKeyPair()
    {
        return this.selfSignKeyPair;
    }

    public void setSelfSignKeyPair(SelfSignKeyPairConfiguration selfSignKeyPair)
    {
        this.selfSignKeyPair = selfSignKeyPair;
    }

    public String getLdapConfigPath()
    {
        return this.ldapConfigPath;
    }

    public void setLdapConfigPath(String ldapConfigPath)
    {
        this.ldapConfigPath = ldapConfigPath;
    }

    public Duration getSessionTimeout()
    {
        return sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout)
    {
        // Bean validation does not run on this configuration, so check explicitly
        requireNonNull(sessionTimeout, "sessionTimeout is null");
        checkArgument(sessionTimeout.compareTo(new Duration(1, MINUTES)) >= 0, "sessionTimeout must be at least 1m");
        this.sessionTimeout = sessionTimeout;
    }
}
