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
    {
        return this.issuer;
    }

    public void setIssuer(String issuer)
    {
        this.issuer = issuer;
    }

    public String getClientId()
    {
        return this.clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getClientSecret()
    {
        return this.clientSecret;
    }

    public void setClientSecret(String clientSecret)
    {
        this.clientSecret = clientSecret;
    }

    public String getTokenEndpoint()
    {
        return this.tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint)
    {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getAuthorizationEndpoint()
    {
        return this.authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint)
    {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getJwkEndpoint()
    {
        return this.jwkEndpoint;
    }

    public void setJwkEndpoint(String jwkEndpoint)
    {
        this.jwkEndpoint = jwkEndpoint;
    }

    public List<String> getScopes()
    {
        return this.scopes;
    }

    public void setScopes(List<String> scopes)
    {
        this.scopes = scopes;
    }

    public String getRedirectUrl()
    {
        return this.redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl)
    {
        this.redirectUrl = redirectUrl;
    }

    public String getUserIdField()
    {
        return this.userIdField;
    }

    public void setUserIdField(String userIdField)
    {
        this.userIdField = userIdField;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OAuthConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object issuer = this.getIssuer();
        final Object otherIssuer = other.getIssuer();
        if (!Objects.equals(issuer, otherIssuer)) {
            return false;
        }
        final Object clientId = this.getClientId();
        final Object otherClientId = other.getClientId();
        if (!Objects.equals(clientId, otherClientId)) {
            return false;
        }
        final Object clientSecret = this.getClientSecret();
        final Object otherClientSecret = other.getClientSecret();
        if (!Objects.equals(clientSecret, otherClientSecret)) {
            return false;
        }
        final Object tokenEndpoint = this.getTokenEndpoint();
        final Object otherTokenEndpoint = other.getTokenEndpoint();
        if (!Objects.equals(tokenEndpoint, otherTokenEndpoint)) {
            return false;
        }
        final Object authorizationEndpoint = this.getAuthorizationEndpoint();
        final Object otherAuthorizationEndpoint = other.getAuthorizationEndpoint();
        if (!Objects.equals(authorizationEndpoint, otherAuthorizationEndpoint)) {
            return false;
        }
        final Object jwkEndpoint = this.getJwkEndpoint();
        final Object otherJwkEndpoint = other.getJwkEndpoint();
        if (!Objects.equals(jwkEndpoint, otherJwkEndpoint)) {
            return false;
        }
        final Object scopes = this.getScopes();
        final Object otherScopes = other.getScopes();
        if (!Objects.equals(scopes, otherScopes)) {
            return false;
        }
        final Object redirectUrl = this.getRedirectUrl();
        final Object otherRedirectUrl = other.getRedirectUrl();
        if (!Objects.equals(redirectUrl, otherRedirectUrl)) {
            return false;
        }
        final Object userIdField = this.getUserIdField();
        final Object otherUserIdField = other.getUserIdField();
        return Objects.equals(userIdField, otherUserIdField);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof OAuthConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object issuer = this.getIssuer();
        result = result * prime + (issuer == null ? 43 : issuer.hashCode());
        final Object clientId = this.getClientId();
        result = result * prime + (clientId == null ? 43 : clientId.hashCode());
        final Object clientSecret = this.getClientSecret();
        result = result * prime + (clientSecret == null ? 43 : clientSecret.hashCode());
        final Object tokenEndpoint = this.getTokenEndpoint();
        result = result * prime + (tokenEndpoint == null ? 43 : tokenEndpoint.hashCode());
        final Object authorizationEndpoint = this.getAuthorizationEndpoint();
        result = result * prime + (authorizationEndpoint == null ? 43 : authorizationEndpoint.hashCode());
        final Object jwkEndpoint = this.getJwkEndpoint();
        result = result * prime + (jwkEndpoint == null ? 43 : jwkEndpoint.hashCode());
        final Object scopes = this.getScopes();
        result = result * prime + (scopes == null ? 43 : scopes.hashCode());
        final Object redirectUrl = this.getRedirectUrl();
        result = result * prime + (redirectUrl == null ? 43 : redirectUrl.hashCode());
        final Object userIdField = this.getUserIdField();
        result = result * prime + (userIdField == null ? 43 : userIdField.hashCode());
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
