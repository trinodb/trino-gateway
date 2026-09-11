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

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public class AuthenticationConfiguration
{
    /**
     * The authentication methods in priority order: this sets the order the
     * {@code ChainedAuthFilter} tries them and which method(s) the login page shows. Accepts
     * either a single scalar (for example {@code defaultType: "form"}) or a list
     * ({@code defaultType: ["oauth", "form"]}); a scalar is bound as a one-element list, so
     * pre-existing single-value configurations keep working unchanged.
     *
     * <p>This property never decides which methods are accepted: any configured
     * {@code oauth}/{@code form} block is always accepted (see {@code AuthenticationTypeResolver}),
     * and a listed method with no configured block is skipped.
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> defaultType;
    private OAuthConfiguration oauth;
    private FormAuthConfiguration form;
    private boolean showFirstTypeOnly;

    public AuthenticationConfiguration(List<String> defaultType, OAuthConfiguration oauth, FormAuthConfiguration form)
    {
        this.defaultType = defaultType;
        this.oauth = oauth;
        this.form = form;
    }

    public AuthenticationConfiguration() {}

    public List<String> getDefaultType()
    {
        return this.defaultType;
    }

    public void setDefaultType(List<String> defaultType)
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

    /**
     * Whether the login page should offer only the first available authentication method
     * (the first {@code defaultType} entry that has a configured manager) instead of every
     * available method. This affects the login page only: regardless of
     * this flag, the {@code ChainedAuthFilter} still accepts any configured method, so API
     * clients keep their multi-method fallback (for example, form/basic auth for automation
     * even when {@code oauth} is listed first). Defaults to {@code false}, so the login page
     * shows every available method.
     */
    public boolean isShowFirstTypeOnly()
    {
        return this.showFirstTypeOnly;
    }

    public void setShowFirstTypeOnly(boolean showFirstTypeOnly)
    {
        this.showFirstTypeOnly = showFirstTypeOnly;
    }
}
