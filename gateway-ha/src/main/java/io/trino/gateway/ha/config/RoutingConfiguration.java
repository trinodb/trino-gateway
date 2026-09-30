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
import static java.util.concurrent.TimeUnit.MINUTES;

public class RoutingConfiguration
{
    private Duration asyncTimeout = new Duration(2, MINUTES);

    private boolean forwardedHeadersEnabled = true;

    private String defaultRoutingGroup = "adhoc";

    // Off (default, opt-in dark launch): when on, the gateway pins every request of a Trino
    // OAuth2 token-exchange handshake (the driver's /oauth2/token/{authId} poll loop and the
    // browser's /oauth2/token/initiate/{authIdHash} redirect) to the coordinator that minted the
    // authId. Only that coordinator holds the in-memory token-exchange state, so without pinning a
    // multi-coordinator deployment routes the poll loop stochastically and the handshake stalls.
    // See OAuth2RoutingUtils.
    private boolean oauth2RoutingEnabled;

    // Caps pin writes per gateway instance per second. The write path (recording a pin from a proxied
    // 401 challenge) is reachable by anyone who can hit the gateway unauthenticated, so without a
    // limit a flood of bogus challenges could be used to hammer the shared oauth2_routing table.
    // Exceeding the limit just skips the pin for that challenge (falls back to normal routing); it
    // never fails the request. The limit is shared across every client of one instance, so it is
    // sized to comfortably absorb legitimate bursts (many logins at once, or a connection pool whose
    // tokens expire together) rather than to bound a single bad actor. Monitor
    // OAuth2RoutingStats.pinWriteRateLimited (JMX-exported) and alert if it is ever nonzero.
    private double oauth2RoutingMaxPinWritesPerSecond = 2000;

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

    public boolean isOauth2RoutingEnabled()
    {
        return oauth2RoutingEnabled;
    }

    public void setOauth2RoutingEnabled(boolean oauth2RoutingEnabled)
    {
        this.oauth2RoutingEnabled = oauth2RoutingEnabled;
    }

    public double getOauth2RoutingMaxPinWritesPerSecond()
    {
        return oauth2RoutingMaxPinWritesPerSecond;
    }

    public void setOauth2RoutingMaxPinWritesPerSecond(double oauth2RoutingMaxPinWritesPerSecond)
    {
        // RateLimiter.create requires a strictly positive rate; fail fast here with a clear message
        // instead of an opaque IllegalArgumentException out of Guava at HaOAuth2RoutingStore
        // construction time, which -- being an eagerly built singleton -- would otherwise stop the
        // gateway from starting at all, even with oauth2RoutingEnabled left off.
        checkArgument(oauth2RoutingMaxPinWritesPerSecond > 0,
                "routing.oauth2RoutingMaxPinWritesPerSecond must be > 0, got: %s",
                oauth2RoutingMaxPinWritesPerSecond);
        this.oauth2RoutingMaxPinWritesPerSecond = oauth2RoutingMaxPinWritesPerSecond;
    }
}
