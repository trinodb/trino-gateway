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
package io.trino.gateway.ha.router;

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.destroyTestingDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestHaOAuth2RoutingStore
{
    private DataStoreConfiguration dataStoreConfig;
    private OAuth2RoutingStore store;

    @BeforeAll
    void setUp()
    {
        dataStoreConfig = dataStoreConfig();
        JdbcConnectionManager connectionManager = createTestingJdbcConnectionManager(dataStoreConfig);
        store = new HaOAuth2RoutingStore(connectionManager.getJdbi());
    }

    @AfterAll
    void tearDown()
    {
        destroyTestingDatabase(dataStoreConfig);
    }

    @Test
    void testSetFindRemove()
    {
        assertThat(store.findBackend("auth-x")).isEmpty();

        store.setBackend("auth-x", "http://coord-a:8080");
        assertThat(store.findBackend("auth-x")).hasValue("http://coord-a:8080");

        // Idempotent re-pin replaces the row rather than failing on the primary key.
        store.setBackend("auth-x", "http://coord-b:8080");
        assertThat(store.findBackend("auth-x")).hasValue("http://coord-b:8080");

        // A forced re-auth drops the pin.
        store.removeBackend("auth-x");
        assertThat(store.findBackend("auth-x")).isEmpty();
    }

    @Test
    void testPinIsVisibleAcrossPods()
    {
        // A pin written by one pod must be readable by another pod sharing the DB.
        store.setBackend("auth-shared", "http://coord-c:8080");

        OAuth2RoutingStore otherPod = new HaOAuth2RoutingStore(createTestingJdbcConnectionManager(dataStoreConfig).getJdbi());
        assertThat(otherPod.findBackend("auth-shared")).hasValue("http://coord-c:8080");
    }
}
