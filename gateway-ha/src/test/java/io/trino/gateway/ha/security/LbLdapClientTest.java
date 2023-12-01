package io.trino.gateway.ha.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

import io.trino.gateway.ha.config.LdapConfiguration;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public class LbLdapClientTest {
  class DummyPasswordWarning implements org.apache.directory.ldap.client.template.PasswordWarning {
    @Override
    public int getTimeBeforeExpiration() {
      return 0;
    }

    @Override
    public int getGraceAuthNsRemaining() {
      return 0;
    }

    @Override
    public boolean isChangeAfterReset() {
      return false;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(LbLdapClientTest.class);

  @Mock
  LdapConnectionTemplate ldapConnectionTemplate;

  @Spy
  LdapConfiguration ldapConfig =
      LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml");
  @InjectMocks
  LbLdapClient lbLdapClient =
          new LbLdapClient(LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml"));

  LbLdapClientTest() {

  }

  @BeforeEach
  public void initMocks() {
    log.info("initializing test");
    org.mockito.MockitoAnnotations.initMocks(this);
  }

  @AfterEach
  public void resetMocks() {
    log.info("resetting mocks");
    Mockito.reset(ldapConnectionTemplate);
    Mockito.reset(ldapConfig);
  }

  @Test
  public void testAuthenticate() {
    String user = "user1";
    String password = "pass1";

    try {
      String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenReturn(null);

      //Success case
      assertTrue(lbLdapClient.authenticate(user, password));

      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenReturn(new LbLdapClientTest.DummyPasswordWarning());

      //Warning case
      assertTrue(lbLdapClient.authenticate(user, password));


      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenThrow(PasswordException.class);

      //failure case
      assertFalse(lbLdapClient.authenticate(user, password));

      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenReturn(null);


    } catch (PasswordException ex) {
      log.error("This should not fail");
      //Force the test to fail
      assertFalse(false);
    }
  }

  @Test
  public void testMemberof() {
    String user = "user1";
    String[] attributes = new String[]{"memberOf"};
    String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

    java.util.ArrayList users = new java.util.ArrayList();
    users.add(new LbLdapClient.UserRecord("Admin,User"));

    Mockito
        .when(ldapConnectionTemplate.search(eq(ldapConfig.getLdapUserBaseDn()),
            eq(filter),
            eq(SearchScope.SUBTREE),
            eq(attributes),
            any(LbLdapClient.UserEntryMapper.class)))
        .thenReturn(users);

    //Success case
    String ret = lbLdapClient.getMemberOf(user);

    log.info("ret is {}", ret);
    assertTrue(ret.equals("Admin,User"));

    org.mockito.Mockito
        .when(ldapConnectionTemplate.search(eq(ldapConfig.getLdapUserBaseDn()),
            eq(filter),
            eq(SearchScope.SUBTREE),
            eq(attributes),
            any(LbLdapClient.UserEntryMapper.class)))
        .thenReturn(null);

    //failure case
    assertFalse(lbLdapClient.getMemberOf(user).equals("Admin,User"));

  }
}