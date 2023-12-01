package io.trino.gateway.ha.config;

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
    {return this.defaultType;}

    public void setDefaultType(String defaultType)
    {this.defaultType = defaultType;}

    public OAuthConfiguration getOauth()
    {return this.oauth;}

    public void setOauth(OAuthConfiguration oauth)
    {this.oauth = oauth;}

    public FormAuthConfiguration getForm()
    {return this.form;}

    public void setForm(FormAuthConfiguration form)
    {this.form = form;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthenticationConfiguration)) {
            return false;
        }
        final AuthenticationConfiguration other = (AuthenticationConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$defaultType = this.getDefaultType();
        final Object other$defaultType = other.getDefaultType();
        if (this$defaultType == null ? other$defaultType != null : !this$defaultType.equals(other$defaultType)) {
            return false;
        }
        final Object this$oauth = this.getOauth();
        final Object other$oauth = other.getOauth();
        if (this$oauth == null ? other$oauth != null : !this$oauth.equals(other$oauth)) {
            return false;
        }
        final Object this$form = this.getForm();
        final Object other$form = other.getForm();
        if (this$form == null ? other$form != null : !this$form.equals(other$form)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof AuthenticationConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $defaultType = this.getDefaultType();
        result = result * PRIME + ($defaultType == null ? 43 : $defaultType.hashCode());
        final Object $oauth = this.getOauth();
        result = result * PRIME + ($oauth == null ? 43 : $oauth.hashCode());
        final Object $form = this.getForm();
        result = result * PRIME + ($form == null ? 43 : $form.hashCode());
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
