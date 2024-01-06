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
package io.trino.gateway.proxyserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.TrinoContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestProxyServer
{
    private TrinoContainer trino;
    private ProxyServer proxy;
    private int proxyPort;

    @BeforeEach
    void setUp()
    {
        trino = new TrinoContainer("trinodb/trino");
        trino.start();

        int trinoPort = trino.getMappedPort(8080);
        proxyPort = trinoPort + 1;
        ProxyServerConfiguration config = new ProxyServerConfiguration();
        config.setName("test_cluster");
        config.setProxyTo("http://localhost:" + trinoPort);
        config.setLocalPort(proxyPort);
        proxy = new ProxyServer(config, new ProxyHandler());
        proxy.start();
    }

    @AfterAll
    public void cleanup()
    {
        trino.close();
        proxy.close();
    }

    @Test
    public void testProxyServer()
            throws Exception
    {
        try (Connection connection = DriverManager.getConnection("jdbc:trino://localhost:" + proxyPort, "test_user", null);
                Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery("SELECT 'test'");
            result.next();
            assertThat(result.getString(1)).isEqualTo("test");
        }
    }
}
