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
import java.util.Objects;

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

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NotifierConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.isStartTlsEnabled() != other.isStartTlsEnabled()) {
            return false;
        }
        if (this.isSmtpAuthEnabled() != other.isSmtpAuthEnabled()) {
            return false;
        }
        final Object smtpHost = this.getSmtpHost();
        final Object otherSmtpHost = other.getSmtpHost();
        if (!Objects.equals(smtpHost, otherSmtpHost)) {
            return false;
        }
        if (this.getSmtpPort() != other.getSmtpPort()) {
            return false;
        }
        final Object smtpUser = this.getSmtpUser();
        final Object otherSmtpUser = other.getSmtpUser();
        if (!Objects.equals(smtpUser, otherSmtpUser)) {
            return false;
        }
        final Object smtpPassword = this.getSmtpPassword();
        final Object otherSmtpPassword = other.getSmtpPassword();
        if (!Objects.equals(smtpPassword, otherSmtpPassword)) {
            return false;
        }
        final Object sender = this.getSender();
        final Object otherSender = other.getSender();
        if (!Objects.equals(sender, otherSender)) {
            return false;
        }
        final Object recipients = this.getRecipients();
        final Object otherRecipients = other.getRecipients();
        return Objects.equals(recipients, otherRecipients);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof NotifierConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        result = result * prime + (this.isStartTlsEnabled() ? 79 : 97);
        result = result * prime + (this.isSmtpAuthEnabled() ? 79 : 97);
        final Object smtpHost = this.getSmtpHost();
        result = result * prime + (smtpHost == null ? 43 : smtpHost.hashCode());
        result = result * prime + this.getSmtpPort();
        final Object smtpUser = this.getSmtpUser();
        result = result * prime + (smtpUser == null ? 43 : smtpUser.hashCode());
        final Object smtpPassword = this.getSmtpPassword();
        result = result * prime + (smtpPassword == null ? 43 : smtpPassword.hashCode());
        final Object sender = this.getSender();
        result = result * prime + (sender == null ? 43 : sender.hashCode());
        final Object recipients = this.getRecipients();
        result = result * prime + (recipients == null ? 43 : recipients.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "NotifierConfiguration{" +
                "startTlsEnabled=" + startTlsEnabled +
                ", smtpAuthEnabled=" + smtpAuthEnabled +
                ", smtpHost='" + smtpHost + '\'' +
                ", smtpPort=" + smtpPort +
                ", smtpUser='" + smtpUser + '\'' +
                ", smtpPassword='" + smtpPassword + '\'' +
                ", sender='" + sender + '\'' +
                ", recipients=" + recipients +
                '}';
    }
}
