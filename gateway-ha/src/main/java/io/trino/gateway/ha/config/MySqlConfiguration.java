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
package io.trino.gateway.ha.config;

import com.mysql.cj.conf.PropertyDefinitions.SslMode;

/**
 * Configuration for MySQL SSL (client cert and truststore settings).
 */
public class MySqlConfiguration
{
    private SslMode sslMode = SslMode.DISABLED;
    private String clientCertificateKeyStoreUrl;
    private String clientCertificateKeyStorePassword;
    private String clientCertificateKeyStoreType;
    private String trustCertificateKeyStoreUrl;
    private String trustCertificateKeyStorePassword;

    public SslMode getSslMode()
    {
        return sslMode;
    }

    public MySqlConfiguration setSslMode(SslMode sslMode)
    {
        this.sslMode = sslMode;
        return this;
    }

    public String getClientCertificateKeyStoreUrl()
    {
        return clientCertificateKeyStoreUrl;
    }

    public MySqlConfiguration setClientCertificateKeyStoreUrl(String url)
    {
        this.clientCertificateKeyStoreUrl = url;
        return this;
    }

    public String getClientCertificateKeyStorePassword()
    {
        return clientCertificateKeyStorePassword;
    }

    public MySqlConfiguration setClientCertificateKeyStorePassword(String password)
    {
        this.clientCertificateKeyStorePassword = password;
        return this;
    }

    public String getClientCertificateKeyStoreType()
    {
        return clientCertificateKeyStoreType;
    }

    public MySqlConfiguration setClientCertificateKeyStoreType(String type)
    {
        this.clientCertificateKeyStoreType = type;
        return this;
    }

    public String getTrustCertificateKeyStoreUrl()
    {
        return trustCertificateKeyStoreUrl;
    }

    public MySqlConfiguration setTrustCertificateKeyStoreUrl(String url)
    {
        this.trustCertificateKeyStoreUrl = url;
        return this;
    }

    public String getTrustCertificateKeyStorePassword()
    {
        return trustCertificateKeyStorePassword;
    }

    public MySqlConfiguration setTrustCertificateKeyStorePassword(String password)
    {
        this.trustCertificateKeyStorePassword = password;
        return this;
    }
}
