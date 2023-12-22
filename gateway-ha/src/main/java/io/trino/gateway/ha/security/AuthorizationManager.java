package io.trino.gateway.ha.security;

import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;

import java.util.Map;
import java.util.Optional;

public class AuthorizationManager
{
    private final AuthorizationConfiguration configuration;
    private final Map<String, UserConfiguration> presetUsers;
    private final LbLdapClient lbLdapClient;

    public AuthorizationManager(AuthorizationConfiguration configuration,
            Map<String, UserConfiguration> presetUsers)
    {
        this.configuration = configuration;
        this.presetUsers = presetUsers;
        if (configuration != null && configuration.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(configuration.getLdapConfigPath()));
        }
        else {
            lbLdapClient = null;
        }
    }

    /**
     * Searches in LDAP for what groups a user is member of.
     *
     * @param sub claim
     * @return an optional membersOf for the input user
     */
    public Optional<String> searchMemberOf(String sub)
    {
        return Optional.empty();
    }

    public Optional<String> getPrivileges(String username)
    {
        //check the preset users
        String privs = "";

        UserConfiguration user = presetUsers.get(username);
        if (user != null) {
            privs = user.getPrivileges();
        }
        else if (lbLdapClient != null) {
            privs = lbLdapClient.getMemberOf(username);
        }
        return Optional.ofNullable(privs);
    }
}
