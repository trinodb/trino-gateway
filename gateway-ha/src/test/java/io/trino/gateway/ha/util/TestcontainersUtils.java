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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

public final class TestcontainersUtils
{
    private TestcontainersUtils() {}

    public static PostgreSQLContainer createPostgreSqlContainer()
    {
        //noinspection resource
        return new PostgreSQLContainer("postgres:17")
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forListeningPort())
                        .withStrategy(new LogMessageWaitStrategy()
                                .withRegEx(".*database system is ready to accept connections.*\\s")
                                .withTimes(2)
                                .withStartupTimeout(Duration.ofMinutes(1))));
    }
}
