package io.trino.gateway.ha.security;

import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class TestLbTokenUtil {
  private final String idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0Iiwic3ViIjoidGVzdCIsImF1ZCI6InRlc3QuY29tIn0.ez05b6FtTKn91rHCuuCwogdepFz5UWgOfW0VtAuScdDYwuKcsZSTCT8vg8rV-Q8CyyGy7u3nySYUKmcLOj9IJ_SaSyTz3-uzXb1qzHH2JH53v3q0-jI9f8HLKbJ1f_sC4N_v4r-KBcBuxUL5zNeR3yCFAtDLDo7wSEIQaGRRsKgTbjd16ZTtN2n8t8LBVdpgll7XFWTPxdpoS7YD08FhayA6bHL-S3go79SdELvBnr1D7zAYdqvovZUHsAYZcm4sqb9HEfGWxOkmu5BVswElKcqoIuv9jXeVIyxmiqOj018KlojEh_uix5ySnStrLYsEJj5M8JvPXSrC3aLMCiJe-A";
  private final String rsaPrivateKey = "test-private-key.pem";
  private final String rsaPublicKey = "test-public-key.pem";
  private LbKeyProvider lbKeyProvider;
  private DecodedJWT jwt;

  @BeforeAll
  public void setup(){
    lbKeyProvider = new LbKeyProvider(new SelfSignKeyPairConfiguration(
        requireNonNull(getClass().getClassLoader().getResource(rsaPrivateKey)).getFile(),
        requireNonNull(getClass().getClassLoader().getResource(rsaPublicKey)).getFile()
    ));
    jwt = JWT.decode(idToken);
  }
  @Test
  public void testAudiencesShouldPassIfNoAudiencesAreRequired(){
    assertTrue(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.empty()));
  }

  @Test
  public void testAudiencesShouldPassIfAnAudienceIsRequired(){
    assertTrue(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("test.com"))));
  }

  @Test
  public void testAudiencesShouldFailIfAudienceDoesNotMatch(){
    assertFalse(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("no_match.com"))));
  }

  @Test
  public void testAudiencesShouldPassIfAnyAudienceIsMatched(){
    assertTrue(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("test.com", "test1.com"))));
  }
}