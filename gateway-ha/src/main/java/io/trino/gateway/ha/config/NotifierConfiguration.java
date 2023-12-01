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
    {return this.startTlsEnabled;}

    public void setStartTlsEnabled(boolean startTlsEnabled)
    {this.startTlsEnabled = startTlsEnabled;}

    public boolean isSmtpAuthEnabled()
    {return this.smtpAuthEnabled;}

    public void setSmtpAuthEnabled(boolean smtpAuthEnabled)
    {this.smtpAuthEnabled = smtpAuthEnabled;}

    public String getSmtpHost()
    {return this.smtpHost;}

    public void setSmtpHost(String smtpHost)
    {this.smtpHost = smtpHost;}

    public int getSmtpPort()
    {return this.smtpPort;}

    public void setSmtpPort(int smtpPort)
    {this.smtpPort = smtpPort;}

    public String getSmtpUser()
    {return this.smtpUser;}

    public void setSmtpUser(String smtpUser)
    {this.smtpUser = smtpUser;}

    public String getSmtpPassword()
    {return this.smtpPassword;}

    public void setSmtpPassword(String smtpPassword)
    {this.smtpPassword = smtpPassword;}

    public String getSender()
    {return this.sender;}

    public void setSender(String sender)
    {this.sender = sender;}

    public List<String> getRecipients()
    {return this.recipients;}

    public void setRecipients(List<String> recipients)
    {this.recipients = recipients;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NotifierConfiguration)) {
            return false;
        }
        final NotifierConfiguration other = (NotifierConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isStartTlsEnabled() != other.isStartTlsEnabled()) {
            return false;
        }
        if (this.isSmtpAuthEnabled() != other.isSmtpAuthEnabled()) {
            return false;
        }
        final Object this$smtpHost = this.getSmtpHost();
        final Object other$smtpHost = other.getSmtpHost();
        if (this$smtpHost == null ? other$smtpHost != null : !this$smtpHost.equals(other$smtpHost)) {
            return false;
        }
        if (this.getSmtpPort() != other.getSmtpPort()) {
            return false;
        }
        final Object this$smtpUser = this.getSmtpUser();
        final Object other$smtpUser = other.getSmtpUser();
        if (this$smtpUser == null ? other$smtpUser != null : !this$smtpUser.equals(other$smtpUser)) {
            return false;
        }
        final Object this$smtpPassword = this.getSmtpPassword();
        final Object other$smtpPassword = other.getSmtpPassword();
        if (this$smtpPassword == null ? other$smtpPassword != null : !this$smtpPassword.equals(other$smtpPassword)) {
            return false;
        }
        final Object this$sender = this.getSender();
        final Object other$sender = other.getSender();
        if (this$sender == null ? other$sender != null : !this$sender.equals(other$sender)) {
            return false;
        }
        final Object this$recipients = this.getRecipients();
        final Object other$recipients = other.getRecipients();
        if (this$recipients == null ? other$recipients != null : !this$recipients.equals(other$recipients)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof NotifierConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isStartTlsEnabled() ? 79 : 97);
        result = result * PRIME + (this.isSmtpAuthEnabled() ? 79 : 97);
        final Object $smtpHost = this.getSmtpHost();
        result = result * PRIME + ($smtpHost == null ? 43 : $smtpHost.hashCode());
        result = result * PRIME + this.getSmtpPort();
        final Object $smtpUser = this.getSmtpUser();
        result = result * PRIME + ($smtpUser == null ? 43 : $smtpUser.hashCode());
        final Object $smtpPassword = this.getSmtpPassword();
        result = result * PRIME + ($smtpPassword == null ? 43 : $smtpPassword.hashCode());
        final Object $sender = this.getSender();
        result = result * PRIME + ($sender == null ? 43 : $sender.hashCode());
        final Object $recipients = this.getRecipients();
        result = result * PRIME + ($recipients == null ? 43 : $recipients.hashCode());
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
