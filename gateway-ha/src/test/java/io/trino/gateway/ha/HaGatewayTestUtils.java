package io.trino.gateway.ha;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class HaGatewayTestUtils
{
    private static final Logger log = LoggerFactory.getLogger(HaGatewayTestUtils.class);
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Random RANDOM = new Random();

    private HaGatewayTestUtils() {}

    public static void seedRequiredData(TestConfig testConfig)
    {
        String jdbcUrl = "jdbc:h2:" + testConfig.getH2DbFilePath();
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(db);
        connectionManager.open();
        Base.exec(HaGatewayTestUtils.getResourceFileContent("gateway-ha-persistence.sql"));
        connectionManager.close();
    }

    public static void prepareMockBackend(
            WireMockServer backend, String endPoint, String expectedResonse)
    {
        backend.start();
        backend.stubFor(
                WireMock.post(endPoint)
                        .willReturn(
                                WireMock.aResponse()
                                        .withBody(expectedResonse)
                                        .withHeader("Content-Encoding", "plain")
                                        .withStatus(200)));
    }

    public static TestConfig buildGatewayConfigAndSeedDb(int routerPort, String configFile)
            throws IOException
    {
        TestConfig testConfig = new TestConfig();
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempH2DbDir = new File(baseDir, "h2db-" + RANDOM.nextInt() + System.currentTimeMillis());
        tempH2DbDir.deleteOnExit();
        testConfig.setH2DbFilePath(tempH2DbDir.getAbsolutePath());

        String configStr =
                getResourceFileContent(configFile)
                        .replace("REQUEST_ROUTER_PORT", String.valueOf(routerPort))
                        .replace("DB_FILE_PATH", tempH2DbDir.getAbsolutePath())
                        .replace(
                                "APPLICATION_CONNECTOR_PORT", String.valueOf(30000 + (int) (Math.random() * 1000)))
                        .replace("ADMIN_CONNECTOR_PORT", String.valueOf(31000 + (int) (Math.random() * 1000)));

        File target = File.createTempFile("config-" + System.currentTimeMillis(), "config.yaml");

        FileWriter fw = new FileWriter(target);
        fw.append(configStr);
        fw.flush();
        log.info("Test Gateway Config \n[{}]", configStr);
        testConfig.setConfigFilePath(target.getAbsolutePath());
        seedRequiredData(testConfig);
        return testConfig;
    }

    public static String getResourceFileContent(String fileName)
    {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream =
                HaGatewayTestUtils.class.getClassLoader().getResourceAsStream(fileName);
        Scanner scn = new Scanner(inputStream);
        while (scn.hasNextLine()) {
            sb.append(scn.nextLine()).append("\n");
        }
        return sb.toString();
    }

    public static void setUpBackend(
            String name,
            String proxyTo,
            String externalUrl,
            boolean active,
            String routingGroup,
            int routerPort)
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        "{ \"name\": \""
                                + name
                                + "\",\"proxyTo\": \""
                                + proxyTo
                                + "\",\"externalUrl\": \""
                                + externalUrl
                                + "\",\"active\": "
                                + active
                                + ",\"routingGroup\": \""
                                + routingGroup
                                + "\"}");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/entity?entityType=GATEWAY_BACKEND")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertTrue(response.isSuccessful());
    }

    public static class TestConfig
    {
        private String configFilePath;
        private String h2DbFilePath;

        public TestConfig(String configFilePath, String h2DbFilePath)
        {
            this.configFilePath = configFilePath;
            this.h2DbFilePath = h2DbFilePath;
        }

        public TestConfig()
        { }

        public String getConfigFilePath()
        {
            return this.configFilePath;
        }

        public void setConfigFilePath(String configFilePath)
        {
            this.configFilePath = configFilePath;
        }

        public String getH2DbFilePath()
        {
            return this.h2DbFilePath;
        }

        public void setH2DbFilePath(String h2DbFilePath)
        {
            this.h2DbFilePath = h2DbFilePath;
        }

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof TestConfig other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            final Object configFilePath = this.getConfigFilePath();
            final Object otherConfigFilePath = other.getConfigFilePath();
            if (!Objects.equals(configFilePath, otherConfigFilePath)) {
                return false;
            }
            final Object h2DbFilePath = this.getH2DbFilePath();
            final Object otherH2DbFilePath = other.getH2DbFilePath();
            return Objects.equals(h2DbFilePath, otherH2DbFilePath);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof TestConfig;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final Object configFilePath = this.getConfigFilePath();
            result = result * prime + (configFilePath == null ? 43 : configFilePath.hashCode());
            final Object h2DbFilePath = this.getH2DbFilePath();
            result = result * prime + (h2DbFilePath == null ? 43 : h2DbFilePath.hashCode());
            return result;
        }

        public String toString()
        {
            return "HaGatewayTestUtils.TestConfig(configFilePath=" + this.getConfigFilePath() +
                    ", h2DbFilePath=" + this.getH2DbFilePath() + ")";
        }
    }
}
