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
        Properties properties = new Properties();
        properties.setProperty("user", configuration.getUser());

        MySqlConfiguration mySqlConfiguration = configuration.getMySqlConfiguration();
        properties.setProperty(PropertyKey.sslMode.getKeyName(), mySqlConfiguration.getSslMode().toString());

        if (SslMode.VERIFY_CA.equals(mySqlConfiguration.getSslMode())) {
            requireNonNull(mySqlConfiguration.getClientCertificateKeyStoreUrl(),
                    "clientCertificateKeyStoreUrl must be set when sslMode=VERIFY_CA");
            requireNonNull(mySqlConfiguration.getClientCertificateKeyStorePassword(),
                    "clientCertificateKeyStorePassword must be set when sslMode=VERIFY_CA");
            requireNonNull(mySqlConfiguration.getTrustCertificateKeyStoreUrl(),
                    "trustCertificateKeyStoreUrl must be set when sslMode=VERIFY_CA");
            requireNonNull(mySqlConfiguration.getTrustCertificateKeyStorePassword(),
                    "trustCertificateKeyStorePassword must be set when sslMode=VERIFY_CA");

            properties.setProperty(PropertyKey.clientCertificateKeyStoreUrl.getKeyName(),
                    mySqlConfiguration.getClientCertificateKeyStoreUrl());
            properties.setProperty(PropertyKey.clientCertificateKeyStorePassword.getKeyName(),
                    mySqlConfiguration.getClientCertificateKeyStorePassword());
            if (mySqlConfiguration.getClientCertificateKeyStoreType() != null) {
                properties.setProperty(
                        PropertyKey.clientCertificateKeyStoreType.getKeyName(),
                        mySqlConfiguration.getClientCertificateKeyStoreType());
            }
            properties.setProperty(PropertyKey.trustCertificateKeyStoreUrl.getKeyName(),
                    mySqlConfiguration.getTrustCertificateKeyStoreUrl());
            properties.setProperty(PropertyKey.trustCertificateKeyStorePassword.getKeyName(),
                    mySqlConfiguration.getTrustCertificateKeyStorePassword());
        }
        else {
            properties.setProperty("password", configuration.getPassword());
        }
        return properties;
    }
}
