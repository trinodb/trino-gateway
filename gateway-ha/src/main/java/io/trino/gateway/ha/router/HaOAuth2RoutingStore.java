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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.persistence.dao.OAuth2RoutingDao;
import org.jdbi.v3.core.Jdbi;

import java.sql.SQLException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Shared, cross-pod {@link OAuth2RoutingStore} backed by the {@code oauth2_routing} table.
 * <p>
 * The DB is the single source of truth — there is no cache. Pins are written once per handshake and
 * read only a handful of times, so the DB load is negligible and a cache would only add staleness.
 * Every DB call is wrapped so that if the DB is unreadable for any reason, {@link #findBackend}
 * returns empty and the request falls back to normal (non-pinned) routing.
 */
public class HaOAuth2RoutingStore
        implements OAuth2RoutingStore
{
    private static final Logger log = Logger.get(HaOAuth2RoutingStore.class);

    private final Jdbi jdbi;
    private final OAuth2RoutingDao dao;

    @Inject
    public HaOAuth2RoutingStore(Jdbi jdbi)
    {
        this.jdbi = requireNonNull(jdbi, "jdbi is null");
        this.dao = jdbi.onDemand(OAuth2RoutingDao.class);
    }

    @Override
    public void setBackend(String pinKey, String backend)
    {
        try {
            long created = System.currentTimeMillis();
            // Portable upsert in one transaction: a repeated challenge or re-pin replaces the row
            // instead of failing on the primary key.
            jdbi.useTransaction(handle -> {
                OAuth2RoutingDao txDao = handle.attach(OAuth2RoutingDao.class);
                txDao.delete(pinKey);
                txDao.insert(pinKey, backend, created);
            });
        }
        catch (RuntimeException e) {
            log.warn("Failed to persist OAuth2 pin: %s", describeForLog(e));
        }
    }

    @Override
    public Optional<String> findBackend(String pinKey)
    {
        try {
            return Optional.ofNullable(dao.findBackendByPinKey(pinKey));
        }
        catch (RuntimeException e) {
            // DB unreadable: fall back to normal (non-pinned) routing rather than failing the request.
            log.warn("Failed to load OAuth2 pin; falling back to normal routing: %s", describeForLog(e));
            return Optional.empty();
        }
    }

    @Override
    public void removeBackend(String pinKey)
    {
        try {
            dao.delete(pinKey);
        }
        catch (RuntimeException e) {
            log.warn("Failed to remove OAuth2 pin: %s", describeForLog(e));
        }
    }

    /**
     * Describes a failure by exception class and SQL state only. Jdbi renders bound arguments,
     * which here include the pin key, into exception messages and stack traces.
     */
    @VisibleForTesting
    static String describeForLog(RuntimeException e)
    {
        if (e.getCause() instanceof SQLException sqlException) {
            return "%s (SQLState %s)".formatted(e.getClass().getSimpleName(), sqlException.getSQLState());
        }
        return e.getClass().getSimpleName();
    }
}
