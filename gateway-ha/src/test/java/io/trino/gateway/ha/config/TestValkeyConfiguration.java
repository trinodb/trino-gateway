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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestValkeyConfiguration
{
    @Test
    void testDefaultValues()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getHost()).isEqualTo("localhost");
        assertThat(config.getPort()).isEqualTo(6379);
        assertThat(config.getPassword()).isNull();
        assertThat(config.getDatabase()).isZero();
        assertThat(config.getMaxTotal()).isEqualTo(20);
        assertThat(config.getMaxIdle()).isEqualTo(10);
        assertThat(config.getMinIdle()).isEqualTo(5);
        assertThat(config.getTimeoutMs()).isEqualTo(2000);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(1800);
    }

    @Test
    void testSettersAndGetters()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setEnabled(true);
        config.setHost("valkey.example.com");
        config.setPort(7000);
        config.setPassword("secret");
        config.setDatabase(5);
        config.setMaxTotal(100);
        config.setMaxIdle(50);
        config.setMinIdle(25);
        config.setTimeoutMs(5000);
        config.setCacheTtlSeconds(3600);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getHost()).isEqualTo("valkey.example.com");
        assertThat(config.getPort()).isEqualTo(7000);
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getDatabase()).isEqualTo(5);
        assertThat(config.getMaxTotal()).isEqualTo(100);
        assertThat(config.getMaxIdle()).isEqualTo(50);
        assertThat(config.getMinIdle()).isEqualTo(25);
        assertThat(config.getTimeoutMs()).isEqualTo(5000);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(3600);
    }
}
