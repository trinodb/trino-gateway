package io.trino.gateway.ha.clustermonitor;

import com.google.common.util.concurrent.FakeTimeLimiter;
import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaRoutingManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@Slf4j
@Test
public class TestClusterStatsJdbcMonitor {
    RoutingManager haRoutingManager;
    GatewayBackendManager backendManager;
    QueryHistoryManager historyManager;

    ClusterStatsJdbcMonitor clusterStatsJdbcMonitor;
    @Mock
    private Connection con;
    @Mock
    private PreparedStatement stmt;
    @Mock
    private ResultSet rs;
    @Mock
    private FakeTimeLimiter fakeTimeLimiter;

    @BeforeEach
    public void initMocks() {
        log.info("initializing test");
        org.mockito.MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    public void resetMocks() {
        log.info("resetting mocks");
        Mockito.reset(con);
        Mockito.reset(stmt);
        Mockito.reset(rs);
    }

    @BeforeClass(alwaysRun = true)
    public void setUp() throws SQLException {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempH2DbDir = new File(baseDir, "h2db-" + System.currentTimeMillis());
        tempH2DbDir.deleteOnExit();
        String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath();
        HaGatewayTestUtils.seedRequiredData(
                new HaGatewayTestUtils.TestConfig("", tempH2DbDir.getAbsolutePath()));
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(db);
        backendManager = new HaGatewayManager(connectionManager);
        historyManager = new HaQueryHistoryManager(connectionManager);
        haRoutingManager = new HaRoutingManager(backendManager, historyManager);

        setMockBackends();

        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("Trino");
        clusterStatsJdbcMonitor = new ClusterStatsJdbcMonitor(backendStateConfiguration);
    }

    private void setMockBackends() {
        List<String> schemeList = List.of("https", "http");
        List<Integer> portList = List.of(-1, 8080, 8081);

        String groupName = "test-group-";
        String backend;
        ProxyBackendConfiguration proxyBackend;
        String scheme;
        for (int i = 0; i < schemeList.size(); i++) {
            scheme = schemeList.get(i);
            for (int j = 0; j < portList.size(); j++) {
                backend = groupName + i + j;
                proxyBackend = new ProxyBackendConfiguration();
                proxyBackend.setActive(true);
                proxyBackend.setRoutingGroup(groupName);
                proxyBackend.setName(backend);
                proxyBackend.setProxyTo(String
                        .format("%s://%s.trino.example.com%s",
                        scheme, backend,
                        portList.get(j) > 0 ? ":" + portList.get(j) : ""
                        )
                );
                proxyBackend.setExternalUrl("trino.example.com");
                backendManager.addBackend(proxyBackend);
                //set backend as healthy to start with
                haRoutingManager.upateBackEndHealth(backend, true);
            }
        }
    }

    @Test
    private void testProtocol() {
        try {
//            MockedStatic<DriverManager> driverManager = Mockito.mockStatic(DriverManager.class
//            Mockito.when(DriverManager.getConnection(Mockito.anyString())).thenReturn(con);
            Mockito.when(fakeTimeLimiter.callWithTimeout(any(Callable.class), eq(10), eq(TimeUnit.SECONDS))).thenReturn(stmt);
//            Mockito.when(con.prepareStatement(any())).thenReturn(stmt);
            Mockito.when(stmt.executeQuery()).thenReturn(rs);

            List<ProxyBackendConfiguration> backendConfList = backendManager.getAllActiveBackends();
            ClusterStats clusterStats;
            for (ProxyBackendConfiguration backendConf : backendConfList) {
                clusterStats = clusterStatsJdbcMonitor.monitor(backendConf);
                // All backends should be healthy
                assert (clusterStats.isHealthy());
            }
        } catch (Exception e) {
            log.error("This should not fail {}", e.toString());
            //Force the test to fail
            assert(false);
        }
    }
}

