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

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class OAuthConfiguration
{
    private String issuer;
    private String clientId;
    private String clientSecret;
    private URI tokenEndpoint;
    private URI authorizationEndpoint;
    private URI jwkEndpoint;
    private List<String> scopes;
    private URI redirectUrl;
    private String userIdField;
    private List<String> audiences;
    private URI redirectWebUrl;

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

    public URI getTokenEndpoint()
    {
        return this.tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint)
    {
        this.tokenEndpoint = URI.create(tokenEndpoint);
    }

    public URI getAuthorizationEndpoint()
    {
        return this.authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint)
    {
        this.authorizationEndpoint = URI.create(authorizationEndpoint);
    }

    public URI getJwkEndpoint()
    {
        return this.jwkEndpoint;
    }

    public void setJwkEndpoint(String jwkEndpoint)
    {
        this.jwkEndpoint = URI.create(jwkEndpoint);
    }

    public List<String> getScopes()
    {
        return this.scopes;
    }

    public void setScopes(List<String> scopes)
    {
        this.scopes = scopes;
    }

    public URI getRedirectUrl()
    {
        return this.redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl)
    {
        this.redirectUrl = URI.create(redirectUrl);
    }

    public String getUserIdField()
    {
        return this.userIdField;
    }

    public void setUserIdField(String userIdField)
    {
        this.userIdField = userIdField;
    }

    public List<String> getAudiences()
    {
        return this.audiences;
    }

    public void setAudiences(List<String> audiences)
    {
        this.audiences = audiences;
    }

    public Optional<URI> getRedirectWebUrl()
    {
        return Optional.ofNullable(this.redirectWebUrl);
    }

    public void setRedirectWebUrl(String redirectWebUrl)
    {
        this.redirectWebUrl = URI.create(redirectWebUrl);
    }
}
