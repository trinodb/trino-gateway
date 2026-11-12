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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.model.BlockedUser;
import io.trino.gateway.ha.model.BlockedUserScope;
import io.trino.gateway.ha.persistence.dao.BlockedUserDao;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.trino.gateway.ha.model.BlockedUserScope.GLOBAL_SCOPE_VALUE;
import static java.util.Objects.requireNonNull;

public class BlockedUserService
{
    private static final Logger log = Logger.get(BlockedUserService.class);
    private static final Duration CACHE_REFRESH_INTERVAL = Duration.ofSeconds(5);

    private final BlockedUserDao dao;
    private final Map<String, BlockedUser> blockedUsers = new ConcurrentHashMap<>();
    private final Duration cacheRefreshInterval;
    private final AtomicReference<Instant> lastRefresh = new AtomicReference<>(Instant.EPOCH);

    @Inject
    public BlockedUserService(Jdbi jdbi)
    {
        this(jdbi, CACHE_REFRESH_INTERVAL);
    }

    @VisibleForTesting
    BlockedUserService(Jdbi jdbi, Duration cacheRefreshInterval)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(BlockedUserDao.class);
        this.cacheRefreshInterval = requireNonNull(cacheRefreshInterval, "cacheRefreshInterval is null");
        refreshBlockedUsers();
    }

    public List<BlockedUser> listBlockedUsers()
    {
        return blockedUsers.values().stream()
                .sorted(Comparator.comparing(BlockedUser::username)
                        .thenComparing(BlockedUser::scopeType)
                        .thenComparing(BlockedUser::scopeValue))
                .toList();
    }

    public boolean isBlocked(String username)
    {
        return isBlocked(username, null);
    }

    public boolean isBlocked(String username, String routingGroup)
    {
        refreshBlockedUsersIfStale();
        String normalizedUsername = normalizeUsername(username);
        return blockedUsers.containsKey(blockKey(normalizedUsername, BlockedUserScope.GLOBAL.name(), GLOBAL_SCOPE_VALUE)) ||
                (routingGroup != null && blockedUsers.containsKey(blockKey(normalizedUsername, BlockedUserScope.ROUTING_GROUP.name(), normalizeRequiredValue(routingGroup, "routingGroup"))));
    }

    public BlockedUser blockUser(String username, String reason, String blockedBy)
    {
        return blockUser(username, BlockedUserScope.GLOBAL.name(), GLOBAL_SCOPE_VALUE, reason, blockedBy);
    }

    public BlockedUser blockUser(String username, String scopeType, String scopeValue, String reason, String blockedBy)
    {
        String normalizedUsername = normalizeUsername(username);
        BlockedUserScope normalizedScope = normalizeScopeType(scopeType);
        String normalizedScopeValue = normalizeScopeValue(normalizedScope, scopeValue);
        String normalizedBlockedBy = normalizeUsername(blockedBy);
        dao.delete(normalizedUsername, normalizedScope.name(), normalizedScopeValue);
        dao.insert(normalizedUsername, normalizedScope.name(), normalizedScopeValue, normalizeOptionalValue(reason), normalizedBlockedBy);

        BlockedUser blockedUser = dao.findByUsernameAndScope(normalizedUsername, normalizedScope.name(), normalizedScopeValue);
        blockedUsers.put(blockKey(blockedUser), blockedUser);
        return blockedUser;
    }

    public boolean unblockUser(String username)
    {
        return unblockUser(username, BlockedUserScope.GLOBAL.name(), GLOBAL_SCOPE_VALUE);
    }

    public boolean unblockUser(String username, String scopeType, String scopeValue)
    {
        String normalizedUsername = normalizeUsername(username);
        BlockedUserScope normalizedScope = normalizeScopeType(scopeType);
        String normalizedScopeValue = normalizeScopeValue(normalizedScope, scopeValue);
        int deletedRows = dao.delete(normalizedUsername, normalizedScope.name(), normalizedScopeValue);
        blockedUsers.remove(blockKey(normalizedUsername, normalizedScope.name(), normalizedScopeValue));
        return deletedRows > 0;
    }

    private static String normalizeUsername(String username)
    {
        String normalizedUsername = requireNonNull(username, "username is null").trim();
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("username is empty");
        }
        return normalizedUsername;
    }

    private static String normalizeOptionalValue(String value)
    {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeRequiredValue(String value, String fieldName)
    {
        String normalizedValue = requireNonNull(value, fieldName + " is null").trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is empty");
        }
        return normalizedValue;
    }

    private static BlockedUserScope normalizeScopeType(String scopeType)
    {
        return BlockedUserScope.from(normalizeRequiredValue(scopeType, "scopeType"));
    }

    private static String normalizeScopeValue(BlockedUserScope scopeType, String scopeValue)
    {
        if (scopeType == BlockedUserScope.GLOBAL) {
            return GLOBAL_SCOPE_VALUE;
        }
        return normalizeRequiredValue(scopeValue, "scopeValue");
    }

    private static String blockKey(BlockedUser blockedUser)
    {
        return blockKey(blockedUser.username(), blockedUser.scopeType(), blockedUser.scopeValue());
    }

    private static String blockKey(String username, String scopeType, String scopeValue)
    {
        return username + '\0' + scopeType + '\0' + scopeValue;
    }

    private void refreshBlockedUsersIfStale()
    {
        Instant now = Instant.now();
        Instant previousRefresh = lastRefresh.get();
        if (Duration.between(previousRefresh, now).compareTo(cacheRefreshInterval) < 0) {
            return;
        }
        if (lastRefresh.compareAndSet(previousRefresh, now)) {
            refreshBlockedUsers();
        }
    }

    private void refreshBlockedUsers()
    {
        try {
            Map<String, BlockedUser> refreshedBlockedUsers = new ConcurrentHashMap<>();
            dao.findAll().forEach(blockedUser -> refreshedBlockedUsers.put(blockKey(blockedUser), blockedUser));
            blockedUsers.clear();
            blockedUsers.putAll(refreshedBlockedUsers);
            lastRefresh.set(Instant.now());
        }
        catch (Exception e) {
            log.warn(e, "Failed to refresh blocked users from database; serving from last known cache");
            lastRefresh.set(Instant.now());
        }
    }
}
