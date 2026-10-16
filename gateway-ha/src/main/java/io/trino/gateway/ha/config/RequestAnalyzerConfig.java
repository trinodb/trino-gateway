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

import io.airlift.units.Duration;
import jakarta.validation.constraints.Max;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RequestAnalyzerConfig
{
    private int maxBodySize = 1_000_000;

    private boolean isClientsUseV2Format;
    private String clientCertificateIdentityField;
    private String clientCertificateUserMappingPattern;
    private String clientCertificateUserMappingFile;
    private String tokenUserField = "email";
    private String oauthTokenInfoUrl;
    private Duration oauthRequestTimeout = new Duration(5, SECONDS);
    private String oauthTokenInfoUserField = "sub";
    private boolean isAnalyzeRequest;

    public RequestAnalyzerConfig() {}

    public int getMaxBodySize()
    {
        return maxBodySize;
    }

    @Max(Integer.MAX_VALUE)
    public void setMaxBodySize(int maxBodySize)
    {
        this.maxBodySize = maxBodySize;
    }

    public String getTokenUserField()
    {
        return tokenUserField;
    }

    public String getClientCertificateIdentityField()
    {
        return clientCertificateIdentityField;
    }

    public void setClientCertificateIdentityField(String clientCertificateIdentityField)
    {
        this.clientCertificateIdentityField = clientCertificateIdentityField;
    }

    public String getClientCertificateUserMappingPattern()
    {
        return clientCertificateUserMappingPattern;
    }

    public void setClientCertificateUserMappingPattern(String clientCertificateUserMappingPattern)
    {
        this.clientCertificateUserMappingPattern = clientCertificateUserMappingPattern;
    }

    public String getClientCertificateUserMappingFile()
    {
        return clientCertificateUserMappingFile;
    }

    public void setClientCertificateUserMappingFile(String clientCertificateUserMappingFile)
    {
        this.clientCertificateUserMappingFile = clientCertificateUserMappingFile;
    }

    public void setTokenUserField(String tokenUserField)
    {
        this.tokenUserField = tokenUserField;
    }

    public String getOauthTokenInfoUrl()
    {
        return oauthTokenInfoUrl;
    }

    public void setOauthTokenInfoUrl(String oauthTokenInfoUrl)
    {
        this.oauthTokenInfoUrl = oauthTokenInfoUrl;
    }

    public Duration getOauthRequestTimeout()
    {
        return oauthRequestTimeout;
    }

    public void setOauthRequestTimeout(Duration oauthRequestTimeout)
    {
        requireNonNull(oauthRequestTimeout, "oauthRequestTimeout is null");
        checkArgument(oauthRequestTimeout.toMillis() > 0, "oauthRequestTimeout must be positive");
        checkArgument(oauthRequestTimeout.toMillis() <= Integer.MAX_VALUE, "oauthRequestTimeout must be at most %s ms", Integer.MAX_VALUE);
        this.oauthRequestTimeout = oauthRequestTimeout;
    }

    public String getOauthTokenInfoUserField()
    {
        return oauthTokenInfoUserField;
    }

    public void setOauthTokenInfoUserField(String oauthTokenInfoUserField)
    {
        requireNonNull(oauthTokenInfoUserField, "oauthTokenInfoUserField is null");
        checkArgument(!oauthTokenInfoUserField.isBlank(), "oauthTokenInfoUserField is blank");
        this.oauthTokenInfoUserField = oauthTokenInfoUserField;
    }

    public boolean isClientsUseV2Format()
    {
        return isClientsUseV2Format;
    }

    public void setClientsUseV2Format(boolean clientsUseV2Format)
    {
        isClientsUseV2Format = clientsUseV2Format;
    }

    public boolean isAnalyzeRequest()
    {
        return isAnalyzeRequest;
    }

    public void setAnalyzeRequest(boolean analyzeRequest)
    {
        isAnalyzeRequest = analyzeRequest;
    }
}
