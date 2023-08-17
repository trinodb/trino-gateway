package com.lyft.data.gateway.ha.security;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

import com.lyft.data.gateway.ha.config.LdapConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Slf4j
public class LbLdapClientTest extends junit.framework.TestCase {
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

  @Mock
  LdapConnectionTemplate ldapConnectionTemplate;

  @Spy
  LdapConfiguration ldapConfig;
  @InjectMocks
  LbLdapClient lbLdapClient;

  LbLdapClientTest() {

  }

  @BeforeMethod
  public void initMocks() {
    log.info("initializing test");
    ldapConfig = LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml");
    org.mockito.MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
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
      Assert.assertTrue(lbLdapClient.authenticate(user, password));

      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenReturn(new LbLdapClientTest.DummyPasswordWarning());

      //Warning case
      Assert.assertTrue(lbLdapClient.authenticate(user, password));


      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenThrow(PasswordException.class);

      //failure case
      Assert.assertFalse(lbLdapClient.authenticate(user, password));

      Mockito
          .when(ldapConnectionTemplate.authenticate(ldapConfig.getLdapUserBaseDn(),
              filter,
              SearchScope.SUBTREE,
              password.toCharArray()))
          .thenReturn(null);


    } catch (PasswordException ex) {
      log.error("This should not fail");
      //Force the test to fail
      Assert.assertFalse(false);
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
    Assert.assertTrue(ret.equals("Admin,User"));

    org.mockito.Mockito
        .when(ldapConnectionTemplate.search(eq(ldapConfig.getLdapUserBaseDn()),
            eq(filter),
            eq(SearchScope.SUBTREE),
            eq(attributes),
            any(LbLdapClient.UserEntryMapper.class)))
        .thenReturn(null);

    //failure case
    Assert.assertFalse(lbLdapClient.getMemberOf(user).equals("Admin,User"));

  }
}