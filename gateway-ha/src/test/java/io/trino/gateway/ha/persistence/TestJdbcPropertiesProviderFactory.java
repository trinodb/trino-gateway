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

import com.mysql.cj.conf.PropertyDefinitions.SslMode;
import com.mysql.cj.conf.PropertyKey;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.MySqlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

final class TestJdbcPropertiesProviderFactory
{
    private static JdbcPropertiesProviderFactory factoryFor()
    {
        List<JdbcPropertiesProvider> providers = Arrays.asList(
            new MySqlJdbcPropertiesProvider(),
            new DefaultJdbcPropertiesProvider());
        return new JdbcPropertiesProviderFactory(providers);
    }

    private DataStoreConfiguration makeMySqlConfig(SslMode mode)
    {
        DataStoreConfiguration db = new DataStoreConfiguration();
        db.setDriver("com.mysql.Driver");
        db.setJdbcUrl("jdbc:mysql://host/db");
        db.setUser("root");
        db.setPassword("root123");
        MySqlConfiguration mySqlConfiguration = db.getMySqlConfiguration();
        mySqlConfiguration.setSslMode(mode);
        return db;
    }

    private DataStoreConfiguration makeH2Config()
    {
        DataStoreConfiguration db = new DataStoreConfiguration();
        db.setDriver("org.h2.Driver");
        db.setJdbcUrl("jdbc:h2:mem:test");
        db.setUser("sa");
        db.setPassword("sa");
        return db;
    }

    @Test
    void testBasicProviderWhenH2()
    {
        DataStoreConfiguration cfg = makeH2Config();
        JdbcPropertiesProviderFactory factory = factoryFor();
        DefaultJdbcPropertiesProvider provider = (DefaultJdbcPropertiesProvider) factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(DefaultJdbcPropertiesProvider.class);

        Properties properties = provider.getProperties(cfg);
        assertThat(properties)
                .hasSize(2)
                .containsEntry("user", "sa")
                .containsEntry("password", "sa");
    }

    @Test
    void testMysqlProviderWhenSslDisabled()
    {
        DataStoreConfiguration cfg = makeMySqlConfig(SslMode.DISABLED);
        JdbcPropertiesProviderFactory factory = factoryFor();
        JdbcPropertiesProvider provider = factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(MySqlJdbcPropertiesProvider.class);

        Properties properties = provider.getProperties(cfg);
        assertThat(properties.getProperty("user")).isEqualTo("root");
        assertThat(properties.getProperty("password")).isEqualTo("root123");
        assertThat(properties.getProperty(PropertyKey.sslMode.getKeyName()))
                .isEqualTo("DISABLED");

        assertThat(properties).doesNotContainKeys(
                PropertyKey.clientCertificateKeyStoreUrl.getKeyName(),
                PropertyKey.clientCertificateKeyStorePassword.getKeyName(),
                PropertyKey.clientCertificateKeyStoreType.getKeyName(),
                PropertyKey.trustCertificateKeyStoreUrl.getKeyName(),
                PropertyKey.trustCertificateKeyStorePassword.getKeyName());
    }

    @Test
    void testMysqlProviderWhenVerifyCa()
    {
        DataStoreConfiguration cfg = makeMySqlConfig(SslMode.VERIFY_CA);
        JdbcPropertiesProviderFactory factory = factoryFor();
        MySqlConfiguration mysqlConfig = cfg.getMySqlConfiguration();
        mysqlConfig.setClientCertificateKeyStoreUrl("file:/tmp/cli.p12")
                .setClientCertificateKeyStorePassword("cpw")
                .setClientCertificateKeyStoreType("PKCS12")
                .setTrustCertificateKeyStoreUrl("file:/tmp/trs.p12")
                .setTrustCertificateKeyStorePassword("tpw");

        JdbcPropertiesProvider provider = factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(MySqlJdbcPropertiesProvider.class);

        Properties properties = provider.getProperties(cfg);
        assertThat(properties.getProperty("user")).isEqualTo("root");
        assertThat(properties.getProperty(PropertyKey.sslMode.getKeyName()))
                .isEqualTo("VERIFY_CA");
        assertThat(properties.getProperty(PropertyKey.clientCertificateKeyStoreUrl.getKeyName()))
                .isEqualTo("file:/tmp/cli.p12");
        assertThat(properties.getProperty(PropertyKey.trustCertificateKeyStoreUrl.getKeyName()))
                .isEqualTo("file:/tmp/trs.p12");
    }
}
