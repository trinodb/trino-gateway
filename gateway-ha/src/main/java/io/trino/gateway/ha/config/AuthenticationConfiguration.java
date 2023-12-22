package io.trino.gateway.ha.config;

import java.util.Objects;

public class AuthenticationConfiguration
{
    private String defaultType;
    private OAuthConfiguration oauth;
    private FormAuthConfiguration form;

    public AuthenticationConfiguration(String defaultType, OAuthConfiguration oauth, FormAuthConfiguration form)
    {
        this.defaultType = defaultType;
        this.oauth = oauth;
        this.form = form;
    }

    public AuthenticationConfiguration() {}

    public String getDefaultType()
    {
        return this.defaultType;
    }

    public void setDefaultType(String defaultType)
    {
        this.defaultType = defaultType;
    }

    public OAuthConfiguration getOauth()
    {
        return this.oauth;
    }

    public void setOauth(OAuthConfiguration oauth)
    {
        this.oauth = oauth;
    }

    public FormAuthConfiguration getForm()
    {
        return this.form;
    }

    public void setForm(FormAuthConfiguration form)
    {
        this.form = form;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthenticationConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object defaultType = this.getDefaultType();
        final Object otherDefaultType = other.getDefaultType();
        if (!Objects.equals(defaultType, otherDefaultType)) {
            return false;
        }
        final Object oauth = this.getOauth();
        final Object otherOauth = other.getOauth();
        if (!Objects.equals(oauth, otherOauth)) {
            return false;
        }
        final Object form = this.getForm();
        final Object otherForm = other.getForm();
        return Objects.equals(form, otherForm);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof AuthenticationConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object defaultType = this.getDefaultType();
        result = result * prime + (defaultType == null ? 43 : defaultType.hashCode());
        final Object oauth = this.getOauth();
        result = result * prime + (oauth == null ? 43 : oauth.hashCode());
        final Object form = this.getForm();
        result = result * prime + (form == null ? 43 : form.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "AuthenticationConfiguration{" +
                "defaultType='" + defaultType + '\'' +
                ", oauth=" + oauth +
                ", form=" + form +
                '}';
    }
}
