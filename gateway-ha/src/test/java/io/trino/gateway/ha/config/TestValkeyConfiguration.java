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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(config.getDatabase()).isEqualTo(0);
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
        config.setPassword("secret-password");
        config.setDatabase(2);
        config.setMaxTotal(50);
        config.setMaxIdle(25);
        config.setMinIdle(10);
        config.setTimeoutMs(5000);
        config.setCacheTtlSeconds(3600);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getHost()).isEqualTo("valkey.example.com");
        assertThat(config.getPort()).isEqualTo(7000);
        assertThat(config.getPassword()).isEqualTo("secret-password");
        assertThat(config.getDatabase()).isEqualTo(2);
        assertThat(config.getMaxTotal()).isEqualTo(50);
        assertThat(config.getMaxIdle()).isEqualTo(25);
        assertThat(config.getMinIdle()).isEqualTo(10);
        assertThat(config.getTimeoutMs()).isEqualTo(5000);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(3600);
    }

    @Test
    void testEnabledFlag()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThat(config.isEnabled()).isFalse();

        config.setEnabled(true);
        assertThat(config.isEnabled()).isTrue();

        config.setEnabled(false);
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void testHostConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setHost("192.168.1.100");
        assertThat(config.getHost()).isEqualTo("192.168.1.100");

        config.setHost("valkey-cluster.local");
        assertThat(config.getHost()).isEqualTo("valkey-cluster.local");
    }

    @Test
    void testPortConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setPort(6380);
        assertThat(config.getPort()).isEqualTo(6380);

        config.setPort(7000);
        assertThat(config.getPort()).isEqualTo(7000);
    }

    @Test
    void testPasswordConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThat(config.getPassword()).isNull();

        config.setPassword("my-secret-password");
        assertThat(config.getPassword()).isEqualTo("my-secret-password");

        config.setPassword(null);
        assertThat(config.getPassword()).isNull();
    }

    @Test
    void testDatabaseConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setDatabase(0);
        assertThat(config.getDatabase()).isEqualTo(0);

        config.setDatabase(5);
        assertThat(config.getDatabase()).isEqualTo(5);

        config.setDatabase(15);
        assertThat(config.getDatabase()).isEqualTo(15);
    }

    @Test
    void testPoolSizeConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setMaxTotal(100);
        config.setMaxIdle(50);
        config.setMinIdle(25);

        assertThat(config.getMaxTotal()).isEqualTo(100);
        assertThat(config.getMaxIdle()).isEqualTo(50);
        assertThat(config.getMinIdle()).isEqualTo(25);
    }

    @Test
    void testTimeoutConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setTimeoutMs(1000);
        assertThat(config.getTimeoutMs()).isEqualTo(1000);

        config.setTimeoutMs(10000);
        assertThat(config.getTimeoutMs()).isEqualTo(10000);
    }

    @Test
    void testCacheTtlConfiguration()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        config.setCacheTtlSeconds(600);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(600);

        config.setCacheTtlSeconds(7200);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(7200);
    }

    @Test
    void testInvalidPortValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setPort(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");

        assertThatThrownBy(() -> config.setPort(65536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");

        assertThatThrownBy(() -> config.setPort(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");
    }

    @Test
    void testInvalidMaxTotalValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setMaxTotal(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotal must be at least 1");

        assertThatThrownBy(() -> config.setMaxTotal(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotal must be at least 1");
    }

    @Test
    void testInvalidMaxIdleValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setMaxIdle(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIdle cannot be negative");
    }

    @Test
    void testInvalidMinIdleValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setMinIdle(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minIdle cannot be negative");
    }

    @Test
    void testInvalidTimeoutValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setTimeoutMs(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeoutMs cannot be negative");
    }

    @Test
    void testInvalidCacheTtlValidation()
    {
        ValkeyConfiguration config = new ValkeyConfiguration();

        assertThatThrownBy(() -> config.setCacheTtlSeconds(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheTtlSeconds cannot be negative");
    }
}
