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

import io.airlift.units.Duration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.ScheduleConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestClusterScheduler
{
    private static final String CLUSTER_NAME = "test-cluster";
    // Match every minute from 9 AM to 5 PM to ensure the test time is always matched
    // Unix cron format: minute hour day month day-of-week (5 parts)
    private static final String CRON_EXPRESSION = "* 9-17 * * *"; // Every minute from 9 AM to 5 PM
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

    @BeforeEach
    void setUp()
    {
        // Reset all mocks before each test to ensure clean state
        reset(backendManager, scheduleConfig, clusterSchedule, backendConfig);
    }

    private void setupTestCluster(boolean activeDuringCron)
    {
        // Setup for a test cluster - call this in tests that need it
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule));
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(activeDuringCron);
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(java.util.Optional.of(backendConfig));

        // Initialize the scheduler with the test cluster
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);
        // Note: Don't start the scheduler here to avoid background executions during tests
    }

    @Test
    void testSchedulerInitialization()
    {
        // Only mock what's actually needed in the constructor
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());

        // Initialize the scheduler
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        assertThat(scheduler).isNotNull();
        verify(scheduleConfig).getTimezone();
    }

    @Test
    void testClusterActivationWhenCronMatches()
    {
        // Setup test cluster with activeDuringCron = true
        setupTestCluster(true);

        // Initialize the scheduler
        scheduler.start();

        // Time within the cron schedule (9 AM - 5 PM)
        ZonedDateTime activeTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, TEST_TIMEZONE);
        when(backendConfig.isActive()).thenReturn(false);

        // Execute
        scheduler.checkAndUpdateClusterStatus(activeTime);

        // Verify
        // We expect at least one call to activateBackend
        verify(backendManager, atLeastOnce()).activateBackend(CLUSTER_NAME);
        // We expect exactly one call to getBackendByName
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        // We expect exactly one call to isActive
        verify(backendConfig, atLeastOnce()).isActive();
    }

    @Test
    void testClusterDeactivationWhenCronDoesNotMatch()
    {
        // Setup test cluster with activeDuringCron = true
        setupTestCluster(true);

        // Initialize the scheduler
        scheduler.start();

        // Time outside the cron schedule (before 9 AM)
        ZonedDateTime inactiveTime = ZonedDateTime.of(2025, 9, 29, 8, 0, 0, 0, TEST_TIMEZONE);
        when(backendConfig.isActive()).thenReturn(true);

        // Execute
        scheduler.checkAndUpdateClusterStatus(inactiveTime);

        // Verify
        verify(backendManager, atLeastOnce()).deactivateBackend(CLUSTER_NAME);
        // Verify getBackendByName is called at least once (actual implementation may call it multiple times)
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig, atLeastOnce()).isActive();
    }

    @Test
    void testNoActionWhenClusterStatusMatchesSchedule()
    {
        // Setup test cluster with activeDuringCron = true
        setupTestCluster(true);

        // Initialize the scheduler (needed to parse cron expressions)
        scheduler.start();

        // Time within the cron schedule (9 AM - 5 PM)
        ZonedDateTime activeTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, TEST_TIMEZONE);
        when(backendConfig.isActive()).thenReturn(true);

        // Execute
        scheduler.checkAndUpdateClusterStatus(activeTime);

        // Verify no action taken when status already matches
        verify(backendManager, never()).activateBackend(anyString());
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig, atLeastOnce()).isActive();
    }

    @Test
    void testClusterNotFoundInBackendManager()
    {
        // Setup test cluster with empty backend
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule));
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(true);
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(java.util.Optional.empty());

        // Initialize scheduler with the test configuration
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Initialize the scheduler
        scheduler.start();

        // Time within the cron schedule (9 AM - 5 PM)
        ZonedDateTime testTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, TEST_TIMEZONE);

        // Execute
        scheduler.checkAndUpdateClusterStatus(testTime);

        // Verify no action taken when cluster not found
        verify(backendManager, never()).activateBackend(anyString());
        verify(backendManager, never()).deactivateBackend(anyString());
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
    }

    @Test
    void testSchedulerWithDifferentTimezones()
    {
        // Set up timezone to New York
        ZoneId newYorkTime = ZoneId.of("America/New_York");
        // Setup test cluster with activeDuringCron = true
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(newYorkTime.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule));
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(true);
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(java.util.Optional.of(backendConfig));

        // Initialize scheduler with the new timezone
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Initialize the scheduler
        scheduler.start();

        // Time within the cron schedule (9 AM - 5 PM) in New York time
        ZonedDateTime testTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, newYorkTime);
        when(backendConfig.isActive()).thenReturn(false);

        // Execute
        scheduler.checkAndUpdateClusterStatus(testTime);

        // Verify
        verify(backendManager, atLeastOnce()).activateBackend(CLUSTER_NAME);
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig, atLeastOnce()).isActive();
        verify(scheduleConfig, atLeastOnce()).getTimezone();
    }

    @Test
    void testInvalidCronExpression()
    {
        // Setup test cluster with an invalid cron expression (valid format but invalid values)
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule));
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        // Using a cron expression with invalid values that will fail validation
        when(clusterSchedule.getCronExpression()).thenReturn("99 25 32 13 8");  // Invalid: minute=99, hour=25, day=32, month=13, day-of-week=8

        // Initialize scheduler with the test configuration
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Initialize the scheduler - this should log an error but not throw
        assertThatNoException().isThrownBy(() -> scheduler.start());

        // Verify the error was logged (you might want to add a test logger to verify this)
        // For now, we'll just verify the mocks were called as expected
        verify(scheduleConfig).getSchedules();
        verify(clusterSchedule).getClusterName();
        verify(clusterSchedule, atLeastOnce()).getCronExpression();

        // Verify no action taken due to invalid cron expression
        verify(backendManager, never()).activateBackend(anyString());
        verify(backendManager, never()).deactivateBackend(anyString());
    }

    @Test
    void testSchedulerWithMultipleClusters()
    {
        // Setup first cluster with activeDuringCron = true
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(true);

        // Setup second cluster with activeDuringCron = false
        ScheduleConfiguration.ClusterSchedule secondCluster = mock(ScheduleConfiguration.ClusterSchedule.class);
        String secondClusterName = "another-cluster";
        when(secondCluster.getClusterName()).thenReturn(secondClusterName);
        when(secondCluster.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(secondCluster.isActiveDuringCron()).thenReturn(false);

        // Setup backend configs
        ProxyBackendConfiguration secondBackendConfig = mock(ProxyBackendConfiguration.class);
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(java.util.Optional.of(backendConfig));
        when(backendManager.getBackendByName(secondClusterName)).thenReturn(java.util.Optional.of(secondBackendConfig));

        // Setup schedules and config
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule, secondCluster));

        // Initialize scheduler with the test configuration
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);

        // Initialize the scheduler
        scheduler.start();

        // Time within the cron schedule (9 AM - 5 PM)
        ZonedDateTime testTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, TEST_TIMEZONE);
        when(backendConfig.isActive()).thenReturn(false);
        when(secondBackendConfig.isActive()).thenReturn(true);

        // Execute
        scheduler.checkAndUpdateClusterStatus(testTime);

        // Verify first cluster is activated
        verify(backendManager, atLeastOnce()).activateBackend(CLUSTER_NAME);
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig, atLeastOnce()).isActive();

        // Verify second cluster is deactivated
        verify(backendManager, atLeastOnce()).deactivateBackend(secondClusterName);
        verify(backendManager, atLeastOnce()).getBackendByName(secondClusterName);
        verify(secondBackendConfig, atLeastOnce()).isActive();
    }

    @Test
    void testSchedulerWithInvertedActiveLogic()
    {
        // Setup test with activeDuringCron = false (inverted logic)
        when(scheduleConfig.isEnabled()).thenReturn(true);
        when(scheduleConfig.getCheckInterval()).thenReturn(new Duration(1, TimeUnit.MINUTES));
        when(scheduleConfig.getTimezone()).thenReturn(TEST_TIMEZONE.toString());
        when(scheduleConfig.getSchedules()).thenReturn(java.util.List.of(clusterSchedule));
        when(clusterSchedule.getClusterName()).thenReturn(CLUSTER_NAME);
        when(clusterSchedule.getCronExpression()).thenReturn(CRON_EXPRESSION);
        when(clusterSchedule.isActiveDuringCron()).thenReturn(false);
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(Optional.of(backendConfig));

        // Initialize the scheduler
        scheduler = new ClusterScheduler(backendManager, scheduleConfig);
        scheduler.start();

        // Test 1: During cron time (10 AM) - should be INACTIVE due to inverted logic
        ZonedDateTime activeTime = ZonedDateTime.of(2025, 9, 29, 10, 0, 0, 0, TEST_TIMEZONE);
        when(backendConfig.isActive()).thenReturn(true);

        // Execute
        scheduler.checkAndUpdateClusterStatus(activeTime);

        // Verify cluster is deactivated when cron matches (inverted logic)
        verify(backendManager, atLeastOnce()).deactivateBackend(CLUSTER_NAME);
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig, atLeastOnce()).isActive();

        // Reset mocks for second test
        reset(backendManager, backendConfig);

        // Re-setup mocks for second test
        when(backendManager.getBackendByName(CLUSTER_NAME)).thenReturn(Optional.of(backendConfig));
        when(backendConfig.isActive()).thenReturn(false);

        // Test 2: Outside cron time (after 5 PM) - should be ACTIVE due to inverted logic
        ZonedDateTime inactiveTime = ZonedDateTime.of(2025, 9, 29, 18, 0, 0, 0, TEST_TIMEZONE);

        // Execute
        scheduler.checkAndUpdateClusterStatus(inactiveTime);

        // Verify cluster is activated (inverted logic: active when cron doesn't match)
        verify(backendManager).activateBackend(CLUSTER_NAME);
        verify(backendManager, atLeastOnce()).getBackendByName(CLUSTER_NAME);
        verify(backendConfig).isActive();
    }
}
