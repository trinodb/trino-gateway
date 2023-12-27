package io.trino.gateway.ha.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.interfaces.RSAPublicKey;

public final class LbTokenUtil
{
    private static final Logger log = LoggerFactory.getLogger(LbTokenUtil.class);

    /**
     * Cookie key to pass the token.
     */

    private LbTokenUtil()
    {
    }

    public static boolean validateToken(String idToken, RSAPublicKey publicKey, String issuer)
    {
        try {
            if (log.isDebugEnabled()) {
                DecodedJWT jwt = JWT.decode(idToken);
                jwt.getClaims().forEach(
                        (key, value) -> log.debug("JWT {} : {}", key, value));
            }

            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWT.require(algorithm)
                    .withIssuer(issuer)
                    .acceptLeeway(60 * 60) // Expired tokens are valid for an hour
                    .build()
                    .verify(idToken);
        }
        catch (Exception exc) {
            log.error("Could not validate token.", exc);
            return false;
        }
        return true;
    }
}
