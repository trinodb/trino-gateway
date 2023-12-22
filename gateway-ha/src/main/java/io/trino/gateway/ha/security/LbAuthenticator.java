package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;

import java.util.Optional;

public class LbAuthenticator
        implements Authenticator<String, LbPrincipal>
{
    private final LbOAuthManager oauthManager;
    private final AuthorizationManager authorizationManager;

    public LbAuthenticator(LbOAuthManager oauthManager,
            AuthorizationManager authorizationManager)
    {
        this.oauthManager = oauthManager;
        this.authorizationManager = authorizationManager;
    }

    /**
     * If the idToken is valid and has the right claims, it returns the principal,
     * otherwise is returns an empty optional.
     *
     * @param idToken idToken from authorization server
     * @return an optional principal
     */
    @Override
    public Optional<LbPrincipal> authenticate(String idToken)
            throws AuthenticationException
    {
        String userIdField = oauthManager.getUserIdField();
        return oauthManager
                .getClaimsFromIdToken(idToken)
                .map(c -> c.get(userIdField))
                .map(Object::toString)
                .map(s -> s.replace("\"", ""))
                .map(sub -> new LbPrincipal(sub, authorizationManager.getPrivileges(sub)));
    }
}
