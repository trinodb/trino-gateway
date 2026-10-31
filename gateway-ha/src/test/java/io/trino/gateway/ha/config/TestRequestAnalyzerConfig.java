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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestRequestAnalyzerConfig
{
    @Test
    void testDefaults()
    {
        assertThat(new RequestAnalyzerConfig().getOauthRequestTimeout()).isEqualTo(new Duration(5, SECONDS));
    }

    @Test
    void testRejectsNonPositiveTimeout()
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        assertThatThrownBy(() -> requestAnalyzerConfig.setOauthRequestTimeout(new Duration(0, SECONDS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oauthRequestTimeout must be positive");
    }

    @Test
    void testRejectsTooLargeTimeout()
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        assertThatThrownBy(() -> requestAnalyzerConfig.setOauthRequestTimeout(new Duration(30, DAYS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oauthRequestTimeout must be at most 2147483647 ms");
    }
}
