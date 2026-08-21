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

import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import java.util.List;

public class AuthenticationConfiguration
{
    private static final Logger log = Logger.get(AuthenticationConfiguration.class);

    private List<String> defaultTypes;
    private OAuthConfiguration oauth;
    private FormAuthConfiguration form;
    private boolean showFirstTypeOnly;

    public AuthenticationConfiguration(List<String> defaultTypes, OAuthConfiguration oauth, FormAuthConfiguration form)
    {
        this.defaultTypes = defaultTypes;
        this.oauth = oauth;
        this.form = form;
    }

    public AuthenticationConfiguration() {}

    public List<String> getDefaultTypes()
    {
        return this.defaultTypes;
    }

    public void setDefaultTypes(List<String> defaultTypes)
    {
        this.defaultTypes = defaultTypes;
    }

    /**
     * @deprecated Use {@code defaultTypes} instead. This setter is retained so existing
     *         configurations that still use the scalar {@code defaultType} keep booting; it maps
     *         the single value into a one-element {@code defaultTypes} list. When both properties
     *         are set, {@code defaultTypes} takes precedence.
     */
    @Deprecated
    public void setDefaultType(String defaultType)
    {
        if (this.defaultTypes != null) {
            log.warn("Both the deprecated \"authentication.defaultType\" and \"authentication.defaultTypes\" are set; ignoring \"defaultType\" and using \"defaultTypes\"=%s", this.defaultTypes);
            return;
        }
        log.warn("Configuration property \"authentication.defaultType\" is deprecated; use the list property \"authentication.defaultTypes\" instead");
        this.defaultTypes = ImmutableList.of(defaultType);
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
     * Whether the login page should offer only the first configured authentication method
     * instead of every configured method. This affects the login page only: regardless of
     * this flag, the {@code ChainedAuthFilter} still accepts any configured method, so API
     * clients keep their multi-method fallback (for example, form/basic auth for automation
     * even when {@code oauth} is listed first). Defaults to {@code false}, so the login page
     * shows every configured method.
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
