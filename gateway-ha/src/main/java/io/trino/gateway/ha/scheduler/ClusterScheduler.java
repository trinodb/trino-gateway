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
package io.trino.gateway.ha.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.annotations.VisibleForTesting;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.ScheduleConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ClusterScheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final GatewayBackendManager backendManager;
    private final ScheduleConfiguration config;
    private final Map<String, ExecutionTime> executionTimes = new ConcurrentHashMap<>();
    private final CronParser cronParser;
    private final ZoneId timezone;

    @Inject
    public ClusterScheduler(GatewayBackendManager backendManager, ScheduleConfiguration config) {
        this.backendManager = backendManager;
        this.config = config;
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        this.cronParser = new CronParser(cronDefinition);

        // Initialize timezone from config, default to GMT if not specified or invalid
        ZoneId configuredTimezone;
        try {
            String timezoneStr = config.getTimezone();
            if (timezoneStr == null || timezoneStr.trim().isEmpty()) {
                configuredTimezone = ZoneId.of("GMT");
                log.info("No timezone specified in configuration, using default: GMT");
            } else {
                configuredTimezone = ZoneId.of(timezoneStr);
                log.info("Using configured timezone: {}", timezoneStr);
            }
        } catch (Exception e) {
            configuredTimezone = ZoneId.of("GMT");
            log.warn(
                    "Invalid timezone '{}' in configuration, falling back to GMT", config.getTimezone(), e);
        }
        this.timezone = configuredTimezone;
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("Cluster scheduling is disabled");
            return;
        }

        // Initialize execution times
        for (ScheduleConfiguration.ClusterSchedule schedule : config.getSchedules()) {
            try {
                Cron cron = cronParser.parse(schedule.getCronExpression());
                executionTimes.put(schedule.getClusterName(), ExecutionTime.forCron(cron));
                log.info(
                        "Scheduled cluster {} with cron expression: {}, activeDuringCron: {}",
                        schedule.getClusterName(),
                        schedule.getCronExpression(),
                        schedule.isActiveDuringCron());
            } catch (Exception e) {
                log.error(
                        "Failed to parse cron expression for cluster {}: {}",
                        schedule.getClusterName(),
                        schedule.getCronExpression(),
                        e);
            }
        }

        // Schedule the task
        scheduler.scheduleWithFixedDelay(
                this::checkAndUpdateClusterStatus,
                0,
                (long) config.getCheckInterval().toMillis(),
                TimeUnit.MILLISECONDS);

        log.info(
                "Started cluster scheduler with check interval: {} (using {} timezone)",
                config.getCheckInterval(),
                timezone);
    }

    @VisibleForTesting
    void checkAndUpdateClusterStatus() {
        try {
            ZonedDateTime now = ZonedDateTime.now(timezone);
            log.debug("Checking cluster status at: {} ({})", now, timezone);

            for (Map.Entry<String, ExecutionTime> entry : executionTimes.entrySet()) {
                String clusterName = entry.getKey();
                ExecutionTime executionTime = entry.getValue();

                // Find the schedule for this cluster
                Optional<ScheduleConfiguration.ClusterSchedule> scheduleOpt =
                        config.getSchedules().stream()
                                .filter(s -> s.getClusterName().equals(clusterName))
                                .findFirst();

                if (scheduleOpt.isEmpty()) {
                    log.warn("No schedule configuration found for cluster: {}", clusterName);
                    continue;
                }

                ScheduleConfiguration.ClusterSchedule schedule = scheduleOpt.get();
                boolean cronMatches = executionTime.isMatch(now);
                boolean shouldBeActive = cronMatches == schedule.isActiveDuringCron();

                log.info(
                        "Cluster: {}, cronMatches: {}, activeDuringCron: {}, shouldBeActive: {}",
                        clusterName,
                        cronMatches,
                        schedule.isActiveDuringCron(),
                        shouldBeActive);

                // Update cluster status if needed
                Optional<ProxyBackendConfiguration> clusterOpt =
                        backendManager.getBackendByName(clusterName);
                if (clusterOpt.isPresent()) {
                    ProxyBackendConfiguration cluster = clusterOpt.get();
                    boolean currentlyActive = cluster.isActive();

                    log.debug(
                            "Cluster: {}, currentlyActive: {}, shouldBeActive: {}",
                            clusterName,
                            currentlyActive,
                            shouldBeActive);

                    if (currentlyActive != shouldBeActive) {
                        if (shouldBeActive) {
                            backendManager.activateBackend(clusterName);
                            log.info(
                                    "Activated cluster {} based on schedule (cron match: {}, activeDuringCron: {})",
                                    clusterName,
                                    cronMatches,
                                    schedule.isActiveDuringCron());
                        } else {
                            backendManager.deactivateBackend(clusterName);
                            log.info(
                                    "Deactivated cluster {} based on schedule (cron match: {}, activeDuringCron: {})",
                                    clusterName,
                                    cronMatches,
                                    schedule.isActiveDuringCron());
                        }
                    } else {
                        log.debug("Cluster {} status unchanged: active={}", clusterName, currentlyActive);
                    }
                } else {
                    log.warn("Cluster {} not found in backend manager", clusterName);
                }
            }
        } catch (Exception e) {
            log.error("Error in cluster scheduler task", e);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Cluster scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for scheduler to terminate", e);
        }
    }
}
