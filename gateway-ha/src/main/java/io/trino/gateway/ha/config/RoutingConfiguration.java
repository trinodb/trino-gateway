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

import static java.util.concurrent.TimeUnit.MINUTES;

public class RoutingConfiguration
{
    private Duration asyncTimeout = new Duration(2, MINUTES);

    private boolean forwardedHeadersEnabled = true;

    private String defaultRoutingGroup = "adhoc";

    // On (default): a request matching no routing rule or header goes to the default routing group.
    // Off: it is rejected, so every request must be explicitly routed.
    private boolean unmatchedRequestFallbackEnabled = true;

    // On (default): a routing group with no healthy backend falls back to the default routing group.
    // Off: the request is rejected, so it never lands on a backend outside the group it asked for.
    private boolean unavailableGroupFallbackEnabled = true;

    // Off (default, opt-in dark launch): when on, the gateway pins every request of a Trino
    // OAuth2 token-exchange handshake (the driver's /oauth2/token/{authId} poll loop and the
    // browser's /oauth2/token/initiate/{authIdHash} redirect) to the coordinator that minted the
    // authId. Only that coordinator holds the in-memory token-exchange state, so without pinning a
    // multi-coordinator deployment routes the poll loop stochastically and the handshake stalls.
    // See OAuth2RoutingUtils.
    private boolean oauth2RoutingEnabled;

    public Duration getAsyncTimeout()
    {
        return asyncTimeout;
    }

    public void setAsyncTimeout(Duration asyncTimeout)
    {
        this.asyncTimeout = asyncTimeout;
    }

    public boolean isForwardedHeadersEnabled()
    {
        return forwardedHeadersEnabled;
    }

    public void setForwardedHeadersEnabled(boolean forwardedHeadersEnabled)
    {
        this.forwardedHeadersEnabled = forwardedHeadersEnabled;
    }

    public String getDefaultRoutingGroup()
    {
        return defaultRoutingGroup;
    }

    public void setDefaultRoutingGroup(String defaultRoutingGroup)
    {
        this.defaultRoutingGroup = defaultRoutingGroup;
    }

    public boolean isUnmatchedRequestFallbackEnabled()
    {
        return unmatchedRequestFallbackEnabled;
    }

    public void setUnmatchedRequestFallbackEnabled(boolean unmatchedRequestFallbackEnabled)
    {
        this.unmatchedRequestFallbackEnabled = unmatchedRequestFallbackEnabled;
    }

    public boolean isUnavailableGroupFallbackEnabled()
    {
        return unavailableGroupFallbackEnabled;
    }

    public void setUnavailableGroupFallbackEnabled(boolean unavailableGroupFallbackEnabled)
    {
        this.unavailableGroupFallbackEnabled = unavailableGroupFallbackEnabled;
    }

    public boolean isOauth2RoutingEnabled()
    {
        return oauth2RoutingEnabled;
    }

    public void setOauth2RoutingEnabled(boolean oauth2RoutingEnabled)
    {
        this.oauth2RoutingEnabled = oauth2RoutingEnabled;
    }
}
