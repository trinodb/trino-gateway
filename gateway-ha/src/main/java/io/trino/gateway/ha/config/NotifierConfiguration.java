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

import java.util.List;

public class NotifierConfiguration
{
    private boolean startTlsEnabled;
    private boolean smtpAuthEnabled;
    private String smtpHost = "localhost";
    private int smtpPort = 587;
    private String smtpUser;
    private String smtpPassword;
    private String sender;
    private List<String> recipients;

    public NotifierConfiguration() {}

    public boolean isStartTlsEnabled()
    {
        return this.startTlsEnabled;
    }

    public void setStartTlsEnabled(boolean startTlsEnabled)
    {
        this.startTlsEnabled = startTlsEnabled;
    }

    public boolean isSmtpAuthEnabled()
    {
        return this.smtpAuthEnabled;
    }

    public void setSmtpAuthEnabled(boolean smtpAuthEnabled)
    {
        this.smtpAuthEnabled = smtpAuthEnabled;
    }

    public String getSmtpHost()
    {
        return this.smtpHost;
    }

    public void setSmtpHost(String smtpHost)
    {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort()
    {
        return this.smtpPort;
    }

    public void setSmtpPort(int smtpPort)
    {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUser()
    {
        return this.smtpUser;
    }

    public void setSmtpUser(String smtpUser)
    {
        this.smtpUser = smtpUser;
    }

    public String getSmtpPassword()
    {
        return this.smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword)
    {
        this.smtpPassword = smtpPassword;
    }

    public String getSender()
    {
        return this.sender;
    }

    public void setSender(String sender)
    {
        this.sender = sender;
    }

    public List<String> getRecipients()
    {
        return this.recipients;
    }

    public void setRecipients(List<String> recipients)
    {
        this.recipients = recipients;
    }
}
