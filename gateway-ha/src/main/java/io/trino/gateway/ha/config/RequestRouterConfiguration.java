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

import java.util.Objects;

public class RequestRouterConfiguration
{
    // Local gateway port
    private int port;

    // Name of the routing gateway name (for metrics purposes)
    private String name;

    // Use SSL?
    private boolean ssl;
    private String keystorePath;
    private String keystorePass;

    private int historySize = 2000;

    // Use the certificate between gateway and trino?
    private boolean forwardKeystore;

    // Set size for HttpConfiguration
    private int outputBufferSize = 32 * 1024;
    private int requestHeaderSize = 8 * 1024;
    private int responseHeaderSize = 8 * 1024;

    // Set size for HttpClient
    private int requestBufferSize = 4 * 1024;
    private int responseBufferSize = 16 * 1024;

    public RequestRouterConfiguration() {}

    public int getPort()
    {
        return this.port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public boolean isSsl()
    {
        return this.ssl;
    }

    public void setSsl(boolean ssl)
    {
        this.ssl = ssl;
    }

    public String getKeystorePath()
    {
        return this.keystorePath;
    }

    public void setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePass()
    {
        return this.keystorePass;
    }

    public void setKeystorePass(String keystorePass)
    {
        this.keystorePass = keystorePass;
    }

    public int getHistorySize()
    {
        return this.historySize;
    }

    public void setHistorySize(int historySize)
    {
        this.historySize = historySize;
    }

    public boolean isForwardKeystore()
    {
        return this.forwardKeystore;
    }

    public void setForwardKeystore(boolean forwardKeystore)
    {
        this.forwardKeystore = forwardKeystore;
    }

    public int getOutputBufferSize()
    {
        return this.outputBufferSize;
    }

    public void setOutputBufferSize(int outputBufferSize)
    {
        this.outputBufferSize = outputBufferSize;
    }

    public int getRequestHeaderSize()
    {
        return this.requestHeaderSize;
    }

    public void setRequestHeaderSize(int requestHeaderSize)
    {
        this.requestHeaderSize = requestHeaderSize;
    }

    public int getResponseHeaderSize()
    {
        return this.responseHeaderSize;
    }

    public void setResponseHeaderSize(int responseHeaderSize)
    {
        this.responseHeaderSize = responseHeaderSize;
    }

    public int getRequestBufferSize()
    {
        return this.requestBufferSize;
    }

    public void setRequestBufferSize(int requestBufferSize)
    {
        this.requestBufferSize = requestBufferSize;
    }

    public int getResponseBufferSize()
    {
        return this.responseBufferSize;
    }

    public void setResponseBufferSize(int responseBufferSize)
    {
        this.responseBufferSize = responseBufferSize;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RequestRouterConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.getPort() != other.getPort()) {
            return false;
        }
        final Object name = this.getName();
        final Object otherName = other.getName();
        if (!Objects.equals(name, otherName)) {
            return false;
        }
        if (this.isSsl() != other.isSsl()) {
            return false;
        }
        final Object keystorePath = this.getKeystorePath();
        final Object otherKeystorePath = other.getKeystorePath();
        if (!Objects.equals(keystorePath, otherKeystorePath)) {
            return false;
        }
        final Object keystorePass = this.getKeystorePass();
        final Object otherKeystorePass = other.getKeystorePass();
        if (!Objects.equals(keystorePass, otherKeystorePass)) {
            return false;
        }
        if (this.getHistorySize() != other.getHistorySize()) {
            return false;
        }
        if (this.isForwardKeystore() != other.isForwardKeystore()) {
            return false;
        }
        if (this.getOutputBufferSize() != other.getOutputBufferSize()) {
            return false;
        }
        if (this.getRequestHeaderSize() != other.getRequestHeaderSize()) {
            return false;
        }
        if (this.getResponseHeaderSize() != other.getResponseHeaderSize()) {
            return false;
        }
        if (this.getRequestBufferSize() != other.getRequestBufferSize()) {
            return false;
        }
        return this.getResponseBufferSize() == other.getResponseBufferSize();
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof RequestRouterConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        result = result * prime + this.getPort();
        final Object name = this.getName();
        result = result * prime + (name == null ? 43 : name.hashCode());
        result = result * prime + (this.isSsl() ? 79 : 97);
        final Object keystorePath = this.getKeystorePath();
        result = result * prime + (keystorePath == null ? 43 : keystorePath.hashCode());
        final Object keystorePass = this.getKeystorePass();
        result = result * prime + (keystorePass == null ? 43 : keystorePass.hashCode());
        result = result * prime + this.getHistorySize();
        result = result * prime + (this.isForwardKeystore() ? 79 : 97);
        result = result * prime + this.getOutputBufferSize();
        result = result * prime + this.getRequestHeaderSize();
        result = result * prime + this.getResponseHeaderSize();
        result = result * prime + this.getRequestBufferSize();
        result = result * prime + this.getResponseBufferSize();
        return result;
    }

    @Override
    public String toString()
    {
        return "RequestRouterConfiguration{" +
                "port=" + port +
                ", name='" + name + '\'' +
                ", ssl=" + ssl +
                ", keystorePath='" + keystorePath + '\'' +
                ", keystorePass='" + keystorePass + '\'' +
                ", historySize=" + historySize +
                ", forwardKeystore=" + forwardKeystore +
                ", outputBufferSize=" + outputBufferSize +
                ", requestHeaderSize=" + requestHeaderSize +
                ", responseHeaderSize=" + responseHeaderSize +
                ", requestBufferSize=" + requestBufferSize +
                ", responseBufferSize=" + responseBufferSize +
                '}';
    }
}
