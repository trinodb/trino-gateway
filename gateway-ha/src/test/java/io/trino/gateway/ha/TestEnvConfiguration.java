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
package io.trino.gateway.ha;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.TrinoContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(Lifecycle.PER_CLASS)
final class TestEnvConfiguration
        extends TestGatewayHaSingleBackend
{
    @Override
    @BeforeAll
    void setup()
            throws Exception
    {
        // These environment variables are set in maven-surefire-plugin in the pom.xml. You may need to
        // update your run configuration to run this test from your IDE
        assertThat(System.getenv("DB_USER")).isEqualTo("sa");
        assertThat(System.getenv("DB_PASSWORD")).isEqualTo("sa");

        trino = new TrinoContainer("trinodb/trino");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();
        int backendPort = trino.getMappedPort(8080);

        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-with-env-template.yml");
        String[] args = {testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }
}
