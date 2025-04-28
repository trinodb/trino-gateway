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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mysql.cj.conf.PropertyDefinitions.SslMode;
import com.mysql.cj.conf.PropertyKey;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.MysqlConfiguration;
import io.trino.gateway.ha.module.RouterBaseModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class TestJdbcPropertiesProviderFactory
{
    private JdbcPropertiesProviderFactory factory;

    @BeforeEach
    void setUp()
    {
        DataStoreConfiguration db = new DataStoreConfiguration("jdbc:h2:mem:test", "sa",
                "sa", "org.h2.Driver", 4, false);
        HaGatewayConfiguration haConfiguration = new HaGatewayConfiguration();
        haConfiguration.setDataStore(db);

        Injector injector = Guice.createInjector(new RouterBaseModule(haConfiguration));
        factory = injector.getInstance(JdbcPropertiesProviderFactory.class);
    }

    private DataStoreConfiguration makeMysqlConfig(SslMode mode)
    {
        DataStoreConfiguration db = new DataStoreConfiguration();
        db.setDriver("com.mysql.Driver");
        db.setJdbcUrl("jdbc:mysql://host/db");
        db.setUser("root");
        db.setPassword("root123");
        MysqlConfiguration mysqlConfig = db.getMysqlConfiguration();
        mysqlConfig.setSslMode(mode);
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
        var cfg = makeH2Config();
        var provider = factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(BasicJdbcPropertiesProvider.class);

        Properties props = provider.getProperties(cfg);
        assertThat(props)
                .hasSize(2)
                .containsEntry("user", "sa")
                .containsEntry("password", "sa");
    }

    @Test
    void testMysqlProviderWhenSslDisabled()
    {
        var cfg = makeMysqlConfig(SslMode.DISABLED);

        var provider = factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(MySqlJdbcPropertiesProvider.class);

        Properties props = provider.getProperties(cfg);
        assertThat(props.getProperty("user")).isEqualTo("root");
        assertThat(props.getProperty("password")).isEqualTo("root123");
        assertThat(props.getProperty(PropertyKey.sslMode.getKeyName()))
                .isEqualTo("DISABLED");

        assertThat(props).doesNotContainKeys(
                PropertyKey.clientCertificateKeyStoreUrl.getKeyName(),
                PropertyKey.clientCertificateKeyStorePassword.getKeyName(),
                PropertyKey.clientCertificateKeyStoreType.getKeyName(),
                PropertyKey.trustCertificateKeyStoreUrl.getKeyName(),
                PropertyKey.trustCertificateKeyStorePassword.getKeyName());
    }

    @Test
    void testMysqlProviderWhenVerifyCa()
    {
        var cfg = makeMysqlConfig(SslMode.VERIFY_CA);
        var mysqlConfig = cfg.getMysqlConfiguration();
        mysqlConfig.setClientCertificateKeyStoreUrl("file:/tmp/cli.p12")
                .setClientCertificateKeyStorePassword("cpw")
                .setClientCertificateKeyStoreType("PKCS12")
                .setTrustCertificateKeyStoreUrl("file:/tmp/trs.p12")
                .setTrustCertificateKeyStorePassword("tpw");

        var provider = factory.forConfig(cfg);
        assertThat(provider).isInstanceOf(MySqlJdbcPropertiesProvider.class);

        var props = provider.getProperties(cfg);
        assertThat(props.getProperty("user")).isEqualTo("root");
        assertThat(props.getProperty(PropertyKey.sslMode.getKeyName()))
                .isEqualTo("VERIFY_CA");
        assertThat(props.getProperty(PropertyKey.clientCertificateKeyStoreUrl.getKeyName()))
                .isEqualTo("file:/tmp/cli.p12");
        assertThat(props.getProperty(PropertyKey.trustCertificateKeyStoreUrl.getKeyName()))
                .isEqualTo("file:/tmp/trs.p12");
    }
}
