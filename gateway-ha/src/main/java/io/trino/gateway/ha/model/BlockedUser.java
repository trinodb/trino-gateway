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
package io.trino.gateway.ha.model;

import jakarta.annotation.Nullable;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

public record BlockedUser(
        @ColumnName("username") String username,
        @ColumnName("scope_type") String scopeType,
        @ColumnName("scope_value") String scopeValue,
        @ColumnName("reason") @Nullable String reason,
        @ColumnName("blocked_by") @Nullable String blockedBy,
        @ColumnName("blocked_datetime_utc") LocalDateTime blockedAt)
{
    public BlockedUser
    {
        requireNonNull(username, "username is null");
        requireNonNull(scopeType, "scopeType is null");
        requireNonNull(scopeValue, "scopeValue is null");
        requireNonNull(blockedAt, "blockedAt is null");
    }
}
