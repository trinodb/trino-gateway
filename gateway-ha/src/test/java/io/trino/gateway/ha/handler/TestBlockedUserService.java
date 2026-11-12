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
package io.trino.gateway.ha.handler;

import io.trino.gateway.ha.model.BlockedUser;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestBlockedUserService
{
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private JdbcConnectionManager connectionManager;

    @BeforeAll
    void setUp()
    {
        connectionManager = new JdbcConnectionManager(dataStoreConfig(POSTGRESQL));
    }

    @AfterAll
    void tearDown()
    {
        if (connectionManager != null) {
            connectionManager.close();
        }
    }

    @Test
    void testLoadsExistingBlockedUsers()
    {
        Jdbi jdbi = createJdbi();
        jdbi.withHandle(handle -> handle.execute(
                """
                INSERT INTO blocked_users (username, reason, blocked_by)
                VALUES ('blocked_user', 'test reason', 'admin_user')
                """));

        BlockedUserService service = new BlockedUserService(jdbi);

        assertThat(service.isBlocked("blocked_user")).isTrue();
        assertThat(service.listBlockedUsers())
                .extracting(BlockedUser::username)
                .containsExactly("blocked_user");
    }

    @Test
    void testBlockUserNormalizesInputAndUpdatesCache()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());

        BlockedUser blockedUser = service.blockUser(" blocked_user ", " test reason ", " admin_user ");

        assertThat(blockedUser.username()).isEqualTo("blocked_user");
        assertThat(blockedUser.scopeType()).isEqualTo("GLOBAL");
        assertThat(blockedUser.scopeValue()).isEqualTo("*");
        assertThat(blockedUser.reason()).isEqualTo("test reason");
        assertThat(blockedUser.blockedBy()).isEqualTo("admin_user");
        assertThat(service.isBlocked("blocked_user")).isTrue();
    }

    @Test
    void testRoutingGroupScopedBlock()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());

        BlockedUser blockedUser = service.blockUser("blocked_user", "ROUTING_GROUP", "group-a", "test reason", "admin_user");

        assertThat(blockedUser.scopeType()).isEqualTo("ROUTING_GROUP");
        assertThat(blockedUser.scopeValue()).isEqualTo("group-a");
        assertThat(service.isBlocked("blocked_user")).isFalse();
        assertThat(service.isBlocked("blocked_user", "group-a")).isTrue();
        assertThat(service.isBlocked("blocked_user", "group-b")).isFalse();
    }

    @Test
    void testGlobalBlockAppliesToAnyRoutingGroup()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());
        service.blockUser("blocked_user", null, "admin_user");

        assertThat(service.isBlocked("blocked_user", "group-a")).isTrue();
    }

    @Test
    void testUnblockUserRemovesUserFromCache()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());
        service.blockUser("blocked_user", null, "admin_user");

        assertThat(service.unblockUser("blocked_user")).isTrue();

        assertThat(service.isBlocked("blocked_user")).isFalse();
        assertThat(service.listBlockedUsers()).isEmpty();
    }

    @Test
    void testUnblockRoutingGroupScopedUser()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());
        service.blockUser("blocked_user", "ROUTING_GROUP", "group-a", null, "admin_user");

        assertThat(service.unblockUser("blocked_user", "ROUTING_GROUP", "group-a")).isTrue();

        assertThat(service.isBlocked("blocked_user", "group-a")).isFalse();
    }

    @Test
    void testUnblockMissingUserReturnsFalse()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());

        assertThat(service.unblockUser("missing_user")).isFalse();
    }

    @Test
    void testRefreshesBlockedUsersFromDatabaseForOtherGatewayInstances()
    {
        Jdbi jdbi = createJdbi();
        BlockedUserService service = new BlockedUserService(jdbi, Duration.ZERO);

        jdbi.withHandle(handle -> handle.execute(
                """
                INSERT INTO blocked_users (username, reason, blocked_by)
                VALUES ('blocked_user', 'test reason', 'admin_user')
                """));

        assertThat(service.isBlocked("blocked_user")).isTrue();
    }

    @Test
    void testBlankUsernameThrows()
    {
        BlockedUserService service = new BlockedUserService(createJdbi());

        assertThatThrownBy(() -> service.blockUser(" ", null, "admin_user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username is empty");
    }

    @Test
    void testDatabaseFailureDuringRefreshDoesNotThrowAndServesFromCache()
    {
        Jdbi jdbi = createJdbi();
        jdbi.withHandle(handle -> handle.execute(
                """
                INSERT INTO blocked_users (username, reason, blocked_by)
                VALUES ('blocked_user', 'test reason', 'admin_user')
                """));

        BlockedUserService service = new BlockedUserService(jdbi, Duration.ZERO);

        // Confirm user is blocked from the initial cache load
        assertThat(service.isBlocked("blocked_user")).isTrue();

        // Simulate a DB failure by renaming the table; the next isBlocked call
        // will attempt a cache refresh, hit an exception, and must not propagate it
        jdbi.useHandle(handle -> handle.execute("ALTER TABLE blocked_users RENAME TO blocked_users_unavailable"));
        try {
            assertThatCode(() -> service.isBlocked("some_user"))
                    .as("DB failure during cache refresh must not propagate to callers")
                    .doesNotThrowAnyException();

            // Cache should still reflect last known state
            assertThat(service.isBlocked("blocked_user"))
                    .as("Last known blocked user remains blocked when DB is unavailable")
                    .isTrue();
        }
        finally {
            jdbi.useHandle(handle -> handle.execute("ALTER TABLE blocked_users_unavailable RENAME TO blocked_users"));
        }
    }

    private Jdbi createJdbi()
    {
        Jdbi jdbi = connectionManager.getJdbi();
        jdbi.useHandle(handle -> handle.execute("DELETE FROM blocked_users"));
        return jdbi;
    }
}
