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
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

final class TestRoutingConfiguration
{
    @Test
    void testDefaults()
    {
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        assertThat(routingConfiguration.getAsyncTimeout()).isEqualTo(new Duration(2, MINUTES));
        assertThat(routingConfiguration.isAddXForwardedHeaders()).isTrue();
        assertThat(routingConfiguration.getDefaultRoutingGroup()).isEqualTo("adhoc");
    }

    @Test
    void testExplicitPropertyMappings()
    {
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();

        Duration customTimeout = new Duration(5, MINUTES);
        routingConfiguration.setAsyncTimeout(customTimeout);
        assertThat(routingConfiguration.getAsyncTimeout()).isEqualTo(customTimeout);

        routingConfiguration.setAddXForwardedHeaders(false);
        assertThat(routingConfiguration.isAddXForwardedHeaders()).isFalse();

        routingConfiguration.setDefaultRoutingGroup("batch");
        assertThat(routingConfiguration.getDefaultRoutingGroup()).isEqualTo("batch");
    }
}
