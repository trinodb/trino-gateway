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
package io.trino.gateway.ha.persistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class JdbcConnectionManager
{
    private static final Logger log = Logger.get(JdbcConnectionManager.class);

    private final Jdbi jdbi;
    private final DataStoreConfiguration configuration;
    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    @Inject
    public JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration)
    {
        this.jdbi = requireNonNull(jdbi, "jdbi is null");
        this.configuration = configuration;
        startCleanUps();
    }

    public Jdbi getJdbi()
    {
        return jdbi;
    }

    public Jdbi getJdbi(@Nullable String routingGroupDatabase)
    {
        if (routingGroupDatabase == null) {
            return jdbi;
        }

        return Jdbi.create(buildJdbcUrl(routingGroupDatabase), configuration.getUser(), configuration.getPassword())
                .installPlugin(new SqlObjectPlugin())
                .registerRowMapper(new RecordAndAnnotatedConstructorMapper());
    }

    @VisibleForTesting
    String buildJdbcUrl(@Nullable String routingGroupDatabase)
    {
        String jdbcUrl = configuration.getJdbcUrl();
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL cannot be null");
        }
        if (routingGroupDatabase == null) {
            return jdbcUrl;
        }
        try {
            int index = jdbcUrl.indexOf("/") + 1;
            if (index == 0) {
                throw new IllegalArgumentException("Invalid JDBC URL: no '/' found in " + jdbcUrl);
            }

            URI newUri = getUriWithRoutingGroupDatabase(routingGroupDatabase, index, jdbcUrl);
            return jdbcUrl.substring(0, index) + newUri;
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static URI getUriWithRoutingGroupDatabase(String routingGroupDatabase, int index, String jdbcUrl)
            throws URISyntaxException
    {
        URI uri = new URI(jdbcUrl.substring(index));
        return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                Path.of(uri.getPath()).resolveSibling(routingGroupDatabase).toString(),
                uri.getQuery(),
                uri.getFragment());
    }

    private void startCleanUps()
    {
        executorService.scheduleWithFixedDelay(
                () -> {
                    log.info("Performing query history cleanup task");
                    long created = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(this.configuration.getQueryHistoryHoursRetention());
                    jdbi.onDemand(QueryHistoryDao.class).deleteOldHistory(created);
                },
                1,
                120,
                TimeUnit.MINUTES);
    }
}
