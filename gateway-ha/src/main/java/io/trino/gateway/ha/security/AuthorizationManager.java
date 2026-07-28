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
package io.trino.gateway.ha.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;

import java.util.Map;
import java.util.Optional;

public class AuthorizationManager
{
    // Sentinel value for defaultPrivilege that explicitly denies all access to users who aren't a
    // preset user, resolved via LDAP, or matched by an OAuth claim, even when enableDefaultPrivilege
    // is set. Lets operators state the deny-by-default intent explicitly alongside the enable flag.
    public static final String DENY_ALL_PRIVILEGE = "NONE";

    private final Map<String, UserConfiguration> presetUsers;
    private final LbLdapClient lbLdapClient;
    private final Optional<String> defaultPrivilege;

    @Inject
    public AuthorizationManager(HaGatewayConfiguration config)
    {
        AuthorizationConfiguration authorizationConfig = config.getAuthorization();
        this.presetUsers = config.getPresetUsers();
        this.defaultPrivilege = resolveDefaultPrivilege(authorizationConfig);
        if (authorizationConfig != null && authorizationConfig.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(authorizationConfig.getLdapConfigPath()));
        }
        else {
            lbLdapClient = null;
        }
    }

    @VisibleForTesting
    AuthorizationManager(Map<String, UserConfiguration> presetUsers, LbLdapClient lbLdapClient, Optional<String> defaultPrivilege)
    {
        this.presetUsers = presetUsers;
        this.lbLdapClient = lbLdapClient;
        this.defaultPrivilege = defaultPrivilege;
    }

    public Optional<String> getPrivileges(String username)
    {
        UserConfiguration user = presetUsers.get(username);
        if (user != null) {
            // A preset user without configured privileges falls back to the default so operators
            // can grant a baseline without spelling out privileges on every individual user.
            String privileges = user.privileges();
            return isNullOrBlank(privileges) ? defaultPrivilege : Optional.of(privileges);
        }
        if (lbLdapClient != null) {
            // LDAP is authoritative for group membership when it is configured: an empty search
            // result means "this user has no groups", not "grant the baseline privilege". Falling
            // back to defaultPrivilege here would let a misconfigured search (wrong base DN, member
            // attribute, ...) silently widen access, so an empty result denies access instead.
            String privileges = lbLdapClient.getMemberOf(username);
            return isNullOrBlank(privileges) ? Optional.empty() : Optional.of(privileges);
        }
        // Neither a preset user nor LDAP resolved this user: they authenticated via OAuth/SSO (or
        // a passwordless setup) and aren't individually preset, so fall back to the default.
        return defaultPrivilege;
    }

    private static boolean isNullOrBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static Optional<String> resolveDefaultPrivilege(AuthorizationConfiguration authorizationConfiguration)
    {
        // The default-privilege fallback is opt-in: unless it is explicitly enabled, any user who
        // isn't a preset user or resolved via LDAP is denied (resolves to no privileges).
        if (authorizationConfiguration == null || !authorizationConfiguration.isEnableDefaultPrivilege()) {
            return Optional.empty();
        }
        String defaultPrivilege = authorizationConfiguration.getDefaultPrivilege();
        // Even when enabled, a missing, empty, or "NONE" (case-insensitive) value denies all access
        // to users who aren't a preset user, resolved via LDAP, or matched by an OAuth claim, so the
        // deny-by-default intent can still be stated explicitly alongside the enable flag.
        if (isNullOrBlank(defaultPrivilege) || defaultPrivilege.trim().equalsIgnoreCase(DENY_ALL_PRIVILEGE)) {
            return Optional.empty();
        }
        return Optional.of(defaultPrivilege);
    }
}
