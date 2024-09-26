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

import jakarta.validation.constraints.Max;

public class RequestAnalyzerConfig
{
    private int maxBodySize = 1_000_000;

    private boolean isClientsUseV2Format;
    private String tokenUserField = "email";
    private String oauthTokenInfoUrl;
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
