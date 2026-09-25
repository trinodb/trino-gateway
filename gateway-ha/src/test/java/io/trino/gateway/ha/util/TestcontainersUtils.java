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
package io.trino.gateway.ha.util;

import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;

public final class TestcontainersUtils
{
    private static final String VERSIONS_RESOURCE = "/test-versions.properties";
    private static final String POSTGRESQL_IMAGE = "postgres:17";
    private static final String MYSQL_IMAGE = "mysql:8.0.36";

    private TestcontainersUtils() {}

    public static PostgreSQLContainer createPostgreSqlContainer()
    {
        //noinspection resource
        return new PostgreSQLContainer(POSTGRESQL_IMAGE)
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forListeningPort())
                        .withStrategy(new LogMessageWaitStrategy()
                                .withRegEx(".*database system is ready to accept connections.*\\s")
                                .withTimes(2)
                                .withStartupTimeout(Duration.ofMinutes(1))));
    }

    public static MySQLContainer createMySqlContainer()
    {
        //noinspection resource
        return new MySQLContainer(MYSQL_IMAGE);
    }

    /**
     * Creates a Trino container using the Trino version the project depends on, so that
     * a Dependabot update of dep.trino.version also updates the tested Trino release.
     */
    public static TrinoContainer createTrinoContainer()
    {
        //noinspection resource
        return new TrinoContainer("trinodb/trino:" + trinoVersion());
    }

    private static String trinoVersion()
    {
        Properties properties = new Properties();
        try (InputStream input = TestcontainersUtils.class.getResourceAsStream(VERSIONS_RESOURCE)) {
            checkState(input != null, "Resource %s not found. It is filtered by Maven and only exists in a Maven build.", VERSIONS_RESOURCE);
            properties.load(input);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = properties.getProperty("trino.version");
        checkState(version != null && !version.isBlank(), "Property trino.version not set in %s", VERSIONS_RESOURCE);
        return version;
    }
}
