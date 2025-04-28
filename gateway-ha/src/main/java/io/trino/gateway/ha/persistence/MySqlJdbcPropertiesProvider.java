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
import io.trino.gateway.ha.config.MysqlConfiguration;

import java.util.Locale;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public class MySqlJdbcPropertiesProvider
        implements JdbcPropertiesProvider
{
    @Override
    public boolean supports(DataStoreConfiguration configuration)
    {
        return configuration.getDriver() != null
                && configuration.getJdbcUrl() != null
                && configuration.getJdbcUrl().toLowerCase(Locale.ROOT).startsWith("jdbc:mysql");
    }

    @Override
    public Properties getProperties(DataStoreConfiguration configuration)
    {
        Properties props = new Properties();
        props.setProperty("user", configuration.getUser());

        MysqlConfiguration mysqlConfiguration = configuration.getMysqlConfiguration();
        props.setProperty(PropertyKey.sslMode.getKeyName(), mysqlConfiguration.getSslMode().toString());

        if (SslMode.VERIFY_CA.equals(mysqlConfiguration.getSslMode())) {
            requireNonNull(mysqlConfiguration.getClientCertificateKeyStoreUrl(),
                    "clientCertificateKeyStoreUrl must be set when sslMode=VERIFY_CA");
            requireNonNull(mysqlConfiguration.getClientCertificateKeyStorePassword(),
                    "clientCertificateKeyStorePassword must be set when sslMode=VERIFY_CA");
            requireNonNull(mysqlConfiguration.getTrustCertificateKeyStoreUrl(),
                    "trustCertificateKeyStoreUrl must be set when sslMode=VERIFY_CA");
            requireNonNull(mysqlConfiguration.getTrustCertificateKeyStorePassword(),
                    "trustCertificateKeyStorePassword must be set when sslMode=VERIFY_CA");

            props.setProperty(PropertyKey.clientCertificateKeyStoreUrl.getKeyName(),
                    mysqlConfiguration.getClientCertificateKeyStoreUrl());
            props.setProperty(PropertyKey.clientCertificateKeyStorePassword.getKeyName(),
                    mysqlConfiguration.getClientCertificateKeyStorePassword());
            if (mysqlConfiguration.getClientCertificateKeyStoreType() != null) {
                props.setProperty(
                        PropertyKey.clientCertificateKeyStoreType.getKeyName(),
                        mysqlConfiguration.getClientCertificateKeyStoreType());
            }
            props.setProperty(PropertyKey.trustCertificateKeyStoreUrl.getKeyName(),
                    mysqlConfiguration.getTrustCertificateKeyStoreUrl());
            props.setProperty(PropertyKey.trustCertificateKeyStorePassword.getKeyName(),
                    mysqlConfiguration.getTrustCertificateKeyStorePassword());
        }
        else {
            props.setProperty("password", configuration.getPassword());
        }
        return props;
    }
}
