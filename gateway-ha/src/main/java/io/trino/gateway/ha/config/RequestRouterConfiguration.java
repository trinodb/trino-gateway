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
}
