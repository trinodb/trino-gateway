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

public class AuthenticationConfiguration
{
    private String defaultType;
    private OAuthConfiguration oauth;
    private FormAuthConfiguration form;
    private JwtConfiguration jwt;

    public AuthenticationConfiguration(String defaultType, OAuthConfiguration oauth, FormAuthConfiguration form, JwtConfiguration jwt)
    {
        this.defaultType = defaultType;
        this.oauth = oauth;
        this.form = form;
        this.jwt = jwt;
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

    public JwtConfiguration getJwt()
    {
        return this.jwt;
    }

    public void setJwt(JwtConfiguration jwt)
    {
        this.jwt = jwt;
    }
}
