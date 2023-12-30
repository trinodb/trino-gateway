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

public class OAuthConfiguration
{
    private String issuer;
    private String clientId;
    private String clientSecret;
    private String tokenEndpoint;
    private String authorizationEndpoint;
    private String jwkEndpoint;
    private List<String> scopes;
    private String redirectUrl;
    private String userIdField;

    public OAuthConfiguration(String issuer, String clientId, String clientSecret, String tokenEndpoint, String authorizationEndpoint, String jwkEndpoint, List<String> scopes, String redirectUrl, String userIdField)
    {
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenEndpoint = tokenEndpoint;
        this.authorizationEndpoint = authorizationEndpoint;
        this.jwkEndpoint = jwkEndpoint;
        this.scopes = scopes;
        this.redirectUrl = redirectUrl;
        this.userIdField = userIdField;
    }

    public OAuthConfiguration() {}

    public String getIssuer()
    {return this.issuer;}

    public void setIssuer(String issuer)
    {this.issuer = issuer;}

    public String getClientId()
    {return this.clientId;}

    public void setClientId(String clientId)
    {this.clientId = clientId;}

    public String getClientSecret()
    {return this.clientSecret;}

    public void setClientSecret(String clientSecret)
    {this.clientSecret = clientSecret;}

    public String getTokenEndpoint()
    {return this.tokenEndpoint;}

    public void setTokenEndpoint(String tokenEndpoint)
    {this.tokenEndpoint = tokenEndpoint;}

    public String getAuthorizationEndpoint()
    {return this.authorizationEndpoint;}

    public void setAuthorizationEndpoint(String authorizationEndpoint)
    {this.authorizationEndpoint = authorizationEndpoint;}

    public String getJwkEndpoint()
    {return this.jwkEndpoint;}

    public void setJwkEndpoint(String jwkEndpoint)
    {this.jwkEndpoint = jwkEndpoint;}

    public List<String> getScopes()
    {return this.scopes;}

    public void setScopes(List<String> scopes)
    {this.scopes = scopes;}

    public String getRedirectUrl()
    {return this.redirectUrl;}

    public void setRedirectUrl(String redirectUrl)
    {this.redirectUrl = redirectUrl;}

    public String getUserIdField()
    {return this.userIdField;}

    public void setUserIdField(String userIdField)
    {this.userIdField = userIdField;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OAuthConfiguration)) {
            return false;
        }
        final OAuthConfiguration other = (OAuthConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$issuer = this.getIssuer();
        final Object other$issuer = other.getIssuer();
        if (this$issuer == null ? other$issuer != null : !this$issuer.equals(other$issuer)) {
            return false;
        }
        final Object this$clientId = this.getClientId();
        final Object other$clientId = other.getClientId();
        if (this$clientId == null ? other$clientId != null : !this$clientId.equals(other$clientId)) {
            return false;
        }
        final Object this$clientSecret = this.getClientSecret();
        final Object other$clientSecret = other.getClientSecret();
        if (this$clientSecret == null ? other$clientSecret != null : !this$clientSecret.equals(other$clientSecret)) {
            return false;
        }
        final Object this$tokenEndpoint = this.getTokenEndpoint();
        final Object other$tokenEndpoint = other.getTokenEndpoint();
        if (this$tokenEndpoint == null ? other$tokenEndpoint != null : !this$tokenEndpoint.equals(other$tokenEndpoint)) {
            return false;
        }
        final Object this$authorizationEndpoint = this.getAuthorizationEndpoint();
        final Object other$authorizationEndpoint = other.getAuthorizationEndpoint();
        if (this$authorizationEndpoint == null ? other$authorizationEndpoint != null : !this$authorizationEndpoint.equals(other$authorizationEndpoint)) {
            return false;
        }
        final Object this$jwkEndpoint = this.getJwkEndpoint();
        final Object other$jwkEndpoint = other.getJwkEndpoint();
        if (this$jwkEndpoint == null ? other$jwkEndpoint != null : !this$jwkEndpoint.equals(other$jwkEndpoint)) {
            return false;
        }
        final Object this$scopes = this.getScopes();
        final Object other$scopes = other.getScopes();
        if (this$scopes == null ? other$scopes != null : !this$scopes.equals(other$scopes)) {
            return false;
        }
        final Object this$redirectUrl = this.getRedirectUrl();
        final Object other$redirectUrl = other.getRedirectUrl();
        if (this$redirectUrl == null ? other$redirectUrl != null : !this$redirectUrl.equals(other$redirectUrl)) {
            return false;
        }
        final Object this$userIdField = this.getUserIdField();
        final Object other$userIdField = other.getUserIdField();
        if (this$userIdField == null ? other$userIdField != null : !this$userIdField.equals(other$userIdField)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof OAuthConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $issuer = this.getIssuer();
        result = result * PRIME + ($issuer == null ? 43 : $issuer.hashCode());
        final Object $clientId = this.getClientId();
        result = result * PRIME + ($clientId == null ? 43 : $clientId.hashCode());
        final Object $clientSecret = this.getClientSecret();
        result = result * PRIME + ($clientSecret == null ? 43 : $clientSecret.hashCode());
        final Object $tokenEndpoint = this.getTokenEndpoint();
        result = result * PRIME + ($tokenEndpoint == null ? 43 : $tokenEndpoint.hashCode());
        final Object $authorizationEndpoint = this.getAuthorizationEndpoint();
        result = result * PRIME + ($authorizationEndpoint == null ? 43 : $authorizationEndpoint.hashCode());
        final Object $jwkEndpoint = this.getJwkEndpoint();
        result = result * PRIME + ($jwkEndpoint == null ? 43 : $jwkEndpoint.hashCode());
        final Object $scopes = this.getScopes();
        result = result * PRIME + ($scopes == null ? 43 : $scopes.hashCode());
        final Object $redirectUrl = this.getRedirectUrl();
        result = result * PRIME + ($redirectUrl == null ? 43 : $redirectUrl.hashCode());
        final Object $userIdField = this.getUserIdField();
        result = result * PRIME + ($userIdField == null ? 43 : $userIdField.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "OAuthConfiguration{" +
                "issuer='" + issuer + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", authorizationEndpoint='" + authorizationEndpoint + '\'' +
                ", jwkEndpoint='" + jwkEndpoint + '\'' +
                ", scopes=" + scopes +
                ", redirectUrl='" + redirectUrl + '\'' +
                ", userIdField='" + userIdField + '\'' +
                '}';
    }
}
