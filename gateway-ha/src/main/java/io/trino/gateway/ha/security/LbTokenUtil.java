package io.trino.gateway.ha.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LbTokenUtil {

  /**
   * Cookie key to pass the token.
   */


  public LbTokenUtil() {
  }

  public static boolean validateToken(String idToken, RSAPublicKey publicKey, String issuer, Optional<List<String>> audiences) {
    try {

      if (log.isDebugEnabled()) {
        DecodedJWT jwt = JWT.decode(idToken);
        jwt.getClaims().forEach(
            (key, value) -> log.debug("JWT {} : {}", key, value)
        );
      }

      Algorithm algorithm = Algorithm.RSA256(publicKey, null);
      Verification verification =
          JWT.require(algorithm)
          .withIssuer(issuer)
          .acceptLeeway(60 * 60); // Expired tokens are valid for an hour

      if(audiences.isPresent()){
        verification.withAnyOfAudience(audiences.get().toArray(new String[0]));
      }

      verification.build().verify(idToken);
    } catch (Exception exc) {
      log.error("Could not validate token.", exc);
      return false;
    }
    return true;
  }

}
