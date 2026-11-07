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
package io.trino.gateway;

import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.trino.gateway.ha.util.TestcontainersUtils.MYSQL_IMAGE;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static io.trino.gateway.ha.util.TestcontainersUtils.createTrinoContainer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

/// Variant of [TrinoGatewayRunner] that serves HTTPS and requires form authentication,
/// configured in `gateway-ha/config-secure.yaml`. Run it with the repository root as the
/// working directory.
///
/// The TLS certificate, the JWT signing key pair and the routing rules file are generated
/// on first use under `gateway-ha/target/secure-runner`, so no key material is committed
/// to the repository and nothing is written outside the build directory. Delete that
/// directory to start over.
public final class TrinoGatewaySecureRunner
{
    // Matches http-server.https.port in gateway-ha/config-secure.yaml
    private static final String GATEWAY_URL = "https://localhost:8443";
    // Matches the preset admin user in gateway-ha/config-secure.yaml
    private static final String ADMIN_CREDENTIALS = Credentials.basic("admin", "admin-Passw0rd");
    // Longer than monitor.taskDelay in gateway-ha/config-secure.yaml
    private static final Duration HEALTHY_TIMEOUT = Duration.ofMinutes(2);
    private static final JsonCodec<ClusterStats> CLUSTER_STATS_CODEC = JsonCodec.jsonCodec(ClusterStats.class);

    // Must match the paths in gateway-ha/config-secure.yaml
    private static final Path GENERATED_DIR = Path.of("gateway-ha/target/secure-runner");
    private static final Path KEYSTORE_PATH = GENERATED_DIR.resolve("keystore.p12");
    private static final Path JWT_PRIVATE_KEY_PATH = GENERATED_DIR.resolve("jwt-private.pem");
    private static final Path JWT_PUBLIC_KEY_PATH = GENERATED_DIR.resolve("jwt-public.pem");
    private static final Path ROUTING_RULES_PATH = GENERATED_DIR.resolve("routing-rules.yml");
    // Matches http-server.https.keystore.key in gateway-ha/config-secure.yaml
    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    private TrinoGatewaySecureRunner() {}

    public static void main(String[] args)
            throws Exception
    {
        Logging.initialize();
        Logger log = Logger.get(TrinoGatewaySecureRunner.class);

        generateMissingFiles(log);

        TrinoContainer trino1 = createTrinoContainer();
        trino1.setPortBindings(List.of("8081:8080"));
        trino1.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino1.start();
        TrinoContainer trino2 = createTrinoContainer();
        trino2.setPortBindings(List.of("8082:8080"));
        trino2.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino2.start();

        PostgreSQLContainer postgres = createPostgreSqlContainer();
        postgres.withUsername("trino_gateway_db_admin");
        postgres.withPassword("P0stG&es");
        postgres.withDatabaseName("trino_gateway_db");
        postgres.setPortBindings(List.of("5432:5432"));
        postgres.start();

        MySQLContainer mysql = new MySQLContainer(MYSQL_IMAGE);
        mysql.withUsername("root");
        mysql.withPassword("root123");
        mysql.withDatabaseName("trinogateway");
        mysql.setPortBindings(List.of("3306:3306"));
        mysql.start();

        OpenTracingCollector tracingCollector = new OpenTracingCollector();
        tracingCollector.start();

        HaGatewayLauncher.main(new String[] {"gateway-ha/config-secure.yaml"});

        OkHttpClient httpClient = createHttpClient();

        // Flyway creates the schema while the gateway starts, so the backends can only be added afterwards
        addBackend(httpClient, "trino-1", "http://localhost:8081");
        addBackend(httpClient, "trino-2", "http://localhost:8082");

        // New backends stay pending and receive no queries until the next run of the cluster monitor
        log.info("Waiting for the backends to become healthy");
        waitForHealthyBackend(httpClient, "trino-1");
        waitForHealthyBackend(httpClient, "trino-2");

        log.info("======== SERVER STARTED ========");
        log.info("Gateway UI: %s/trino-gateway", GATEWAY_URL);
        log.info("Tracing: http://localhost:16686");
    }

    private static void generateMissingFiles(Logger log)
            throws IOException, GeneralSecurityException
    {
        Files.createDirectories(GENERATED_DIR);
        if (!Files.exists(KEYSTORE_PATH)) {
            generateKeystore();
            log.info("Generated a self-signed TLS certificate in %s", KEYSTORE_PATH);
        }
        if (!Files.exists(JWT_PRIVATE_KEY_PATH) || !Files.exists(JWT_PUBLIC_KEY_PATH)) {
            generateJwtKeyPair();
            log.info("Generated a JWT signing key pair in %s", GENERATED_DIR);
        }
        if (!Files.exists(ROUTING_RULES_PATH)) {
            try (InputStream rules = TrinoGatewaySecureRunner.class.getClassLoader().getResourceAsStream("rules/routing_rules_update.yml")) {
                checkState(rules != null, "Missing classpath resource rules/routing_rules_update.yml");
                Files.copy(rules, ROUTING_RULES_PATH);
            }
            log.info("Copied the example routing rules to %s", ROUTING_RULES_PATH);
        }
    }

    // The certificate covers host.docker.internal as well, so a client in a container can verify it
    private static void generateKeystore()
            throws IOException, GeneralSecurityException
    {
        KeyPair keyPair = generateRsaKeyPair();
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=localhost");
        GeneralNames alternativeNames = new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.dNSName, "host.docker.internal"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
        });

        X509Certificate certificate;
        try {
            JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now),
                    Date.from(now.plus(365, ChronoUnit.DAYS)),
                    subject,
                    keyPair.getPublic());
            certificateBuilder.addExtension(Extension.subjectAlternativeName, false, alternativeNames);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));
        }
        catch (CertIOException | OperatorCreationException e) {
            throw new GeneralSecurityException("Failed to generate a self-signed certificate", e);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("gateway", keyPair.getPrivate(), KEYSTORE_PASSWORD, new Certificate[] {certificate});
        try (OutputStream output = Files.newOutputStream(KEYSTORE_PATH)) {
            keyStore.store(output, KEYSTORE_PASSWORD);
        }
    }

    private static void generateJwtKeyPair()
            throws IOException, GeneralSecurityException
    {
        KeyPair keyPair = generateRsaKeyPair();
        writePem(JWT_PRIVATE_KEY_PATH, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(JWT_PUBLIC_KEY_PATH, "PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static KeyPair generateRsaKeyPair()
            throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void writePem(Path path, String type, byte[] encoded)
            throws IOException
    {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN %s-----\n%s\n-----END %s-----\n".formatted(type, body, type), UTF_8);
    }

    // The gateway uses a self-signed certificate, so trust exactly that certificate
    private static OkHttpClient createHttpClient()
            throws IOException, GeneralSecurityException
    {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(KEYSTORE_PATH)) {
            keyStore.load(input, KEYSTORE_PASSWORD);
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        X509TrustManager trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new X509TrustManager[] {trustManager}, null);
        return new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .build();
    }

    private static void addBackend(OkHttpClient httpClient, String name, String proxyTo)
            throws IOException
    {
        RequestBody requestBody = RequestBody.create(
                """
                {"name": "%s", "proxyTo": "%s", "externalUrl": "%s", "active": true, "routingGroup": "adhoc"}
                """.formatted(name, proxyTo, proxyTo),
                MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GATEWAY_URL + "/entity?entityType=GATEWAY_BACKEND")
                .header("Authorization", ADMIN_CREDENTIALS)
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            checkState(response.isSuccessful(), "Failed to add backend %s, received status code %s", name, response.code());
        }
    }

    private static void waitForHealthyBackend(OkHttpClient httpClient, String name)
            throws IOException
    {
        Request request = new Request.Builder()
                .url(GATEWAY_URL + "/api/public/backends/%s/state".formatted(name))
                .header("Authorization", ADMIN_CREDENTIALS)
                .get()
                .build();
        long deadline = System.nanoTime() + HEALTHY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try (Response response = httpClient.newCall(request).execute()) {
                checkState(response.isSuccessful(), "Failed to get state of backend %s, received status code %s", name, response.code());
                if (CLUSTER_STATS_CODEC.fromJson(response.body().string()).trinoStatus() == TrinoStatus.HEALTHY) {
                    return;
                }
            }
            sleepUninterruptibly(Duration.ofSeconds(1));
        }
        throw new IllegalStateException("Backend %s is not healthy after %s".formatted(name, HEALTHY_TIMEOUT));
    }
}
