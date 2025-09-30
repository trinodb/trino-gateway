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
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.ScheduleConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestClusterScheduler
{
    private static final String CLUSTER_NAME = "test-cluster";
    private static final String CRON_EXPRESSION = "0 0 9-17 * * ?"; // Every day from 9 AM to 5 PM
    private static final ZoneId TEST_TIMEZONE = ZoneId.of("America/Los_Angeles");

    @Mock
    private GatewayBackendManager backendManager;
    @Mock
    private ScheduleConfiguration scheduleConfig;
    @Mock
    private ScheduleConfiguration.ClusterSchedule clusterSchedule;
    @Mock
    private ProxyBackendConfiguration backendConfig;

    private ClusterScheduler scheduler;
    private ExecutionTime executionTime;

    @BeforeEach
    void setUp()
    {
        // Set up test data
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getSchedules()).thenReturn(List.of(clusterSchedule));

        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(true);

        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(Optional.of(backendConfig));

        // Initialize the scheduler
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Set up execution time for testing
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        CronParser parser = new CronParser(cronDefinition);
        Cron cron = parser.parse(CRON_EXPRESSION);
        executionTime = ExecutionTime.forCron(cron);
    }

    @Test
    void testSchedulerInitialization()
    {
        assertThat(scheduler).isNotNull();
        verify(scheduleConfig, times(1)).isEnabled();
        verify(scheduleConfig, times(1)).getSchedules();
    }

    @Test
    void testClusterActivationWhenCronMatches()
    {
        // Given: Time is within the active window (9 AM - 5 PM)
        ZonedDateTime activeTime = ZonedDateTime.now(TEST_TIMEZONE)
                .withHour(10)
                .withMinute(0)
                .withSecond(0);

        // When: Scheduler checks the status
        when(backendConfig.isActive()).thenReturn(false);
        // Set up the time to be used by the scheduler
        ZonedDateTime originalNow = ZonedDateTime.now(TEST_TIMEZONE);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(activeTime);
            scheduler.checkAndUpdateClusterStatus();
        }

        // Then: Cluster should be activated
        verify(backendManager).activateBackend(CLUSTER_NAME);
    }

    @Test
    void testClusterDeactivationWhenCronDoesNotMatch()
    {
        // Given: Time is outside the active window (before 9 AM)
        ZonedDateTime inactiveTime = ZonedDateTime.now(TEST_TIMEZONE)
                .withHour(8)
                .withMinute(0)
                .withSecond(0);

        // When: Scheduler checks the status
        when(backendConfig.isActive()).thenReturn(true);
        // Set up the time to be used by the scheduler
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(inactiveTime);
            scheduler.checkAndUpdateClusterStatus();
        }

        // Then: Cluster should be deactivated
        verify(backendManager).deactivateBackend(CLUSTER_NAME);
    }

    @Test
    void testNoActionWhenClusterStatusMatchesSchedule()
    {
        // Given: Time is within active window and cluster is already active
        ZonedDateTime activeTime = ZonedDateTime.now(TEST_TIMEZONE)
                .withHour(10)
                .withMinute(0)
                .withSecond(0);

        // When: Scheduler checks the status
        when(backendConfig.isActive()).thenReturn(true);
        // Set up the time to be used by the scheduler
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(activeTime);
            scheduler.checkAndUpdateClusterStatus();
        }

        // Then: No action should be taken
        verify(backendManager, never()).activateBackend(anyString());
        verify(backendManager, never()).deactivateBackend(anyString());
    }

    @Test
    void testClusterNotFoundInBackendManager()
    {
        // Given: Cluster is not found in backend manager
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(Optional.empty());

        // When: Scheduler checks the status
        ZonedDateTime now = ZonedDateTime.now(TEST_TIMEZONE);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(now);
            scheduler.checkAndUpdateClusterStatus();
        }

        // Then: No action should be taken
        verify(backendManager, never()).activateBackend(anyString());
        verify(backendManager, never()).deactivateBackend(anyString());
    }

    @Test
    void testSchedulerWithDifferentTimezones()
    {
        // Given: A different timezone
        ZoneId newYorkTime = ZoneId.of("America/New_York");
        when(scheduleConfig.getTimezone()).thenReturn(newYorkTime.toString());

        // When: Reinitialize scheduler with new timezone
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Then: Scheduler should be initialized with the new timezone
        ZonedDateTime testTime = ZonedDateTime.now(newYorkTime);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(newYorkTime)).thenReturn(testTime);
            scheduler.checkAndUpdateClusterStatus();
            // Just verify the method was called, detailed testing is done in other tests
            verify(backendManager).getBackendByName(CLUSTER_NAME);
        }
    }

    @Test
    void testInvalidCronExpression()
    {
        // Given: An invalid cron expression
        when(clusterSchedule.getCronExpression()).thenReturn("invalid-cron");

        // When: Scheduler is initialized
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Then: No action should be taken when checking status
        ZonedDateTime now = ZonedDateTime.now(TEST_TIMEZONE);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(now);
            scheduler.checkAndUpdateClusterStatus();
            verify(backendManager, never()).activateBackend(anyString());
            verify(backendManager, never()).deactivateBackend(anyString());
        }
    }

    @Test
    void testSchedulerWithMultipleClusters()
    {
        // Given: Multiple cluster schedules
        String secondCluster = "another-cluster";
        ScheduleConfiguration.ClusterSchedule secondSchedule = new ScheduleConfiguration.ClusterSchedule();
        secondSchedule.setClusterName(secondCluster);
        secondSchedule.setCronExpression("0 0 18-23 * * ?"); // 6 PM - 11 PM
        secondSchedule.setActiveDuringCron(true);

        when(scheduleConfig.getSchedules()).thenReturn(List.of(clusterSchedule, secondSchedule));
        when(backendManager.getBackendByName(secondCluster)).thenReturn(Optional.of(backendConfig));

        // When: Scheduler checks the status
        ZonedDateTime testTime = ZonedDateTime.now(TEST_TIMEZONE).withHour(20); // 8 PM
        when(backendConfig.isActive()).thenReturn(false);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(testTime);
            scheduler.checkAndUpdateClusterStatus();
        }

        // Then: Both clusters should be checked
        verify(backendManager).getBackendByName(CLUSTER_NAME);
        verify(backendManager).getBackendByName(secondCluster);
        // First cluster should be inactive at 8 PM, second should be active
        verify(backendManager).deactivateBackend(CLUSTER_NAME);
        verify(backendManager).activateBackend(secondCluster);
    }

    @Test
    void testSchedulerWithInvertedActiveLogic()
    {
        // Given: A schedule that's active when cron doesn't match
        when(clusterSchedule.isActiveDuringCron()).thenReturn(false);

        // When: Time is within the cron window (9 AM - 5 PM)
        ZonedDateTime activeTime = ZonedDateTime.now(TEST_TIMEZONE)
                .withHour(10)
                .withMinute(0)
                .withSecond(0);

        // Then: Cluster should be deactivated because activeDuringCron is false
        when(backendConfig.isActive()).thenReturn(true);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(activeTime);
            scheduler.checkAndUpdateClusterStatus();
            verify(backendManager).deactivateBackend(CLUSTER_NAME);
        }

        // When: Time is outside the cron window
        ZonedDateTime inactiveTime = ZonedDateTime.now(TEST_TIMEZONE)
                .withHour(18)
                .withMinute(0)
                .withSecond(0);

        // Then: Cluster should be activated because activeDuringCron is false
        when(backendConfig.isActive()).thenReturn(false);
        try (MockedStatic<ZonedDateTime> mocked = mockStatic(ZonedDateTime.class)) {
            mocked.when(() -> ZonedDateTime.now(TEST_TIMEZONE)).thenReturn(inactiveTime);
            scheduler.checkAndUpdateClusterStatus();
            verify(backendManager).activateBackend(CLUSTER_NAME);
        }
    }
}
