package io.trino.gateway.ha.security;

import io.trino.gateway.ha.config.AuthorizationConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.PatternSyntaxException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
public class TestLbAuthorizer {
    private static final String USER = "username";
    private static LbPrincipal principal;
    private static LbAuthorizer authorizer;
    private static AuthorizationConfiguration configuration;
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";
    private static final String API_ROLE = "API";
    private static final String UNKNOWN_ROLE = "UNKNOWN";

    private static final String PVFX_DATA_31 = "PVFX_DATA_31";

    @BeforeAll
    public static void setup() {
        configuration = new AuthorizationConfiguration();
        principal = new LbPrincipal(USER, Optional.of(PVFX_DATA_31));
    }

    static void configureRole(String regex, String role) {
        if (role.equalsIgnoreCase(ADMIN_ROLE)){
            configuration.setAdmin(regex);
            authorizer = new LbAuthorizer(configuration);
        }
        if (role.equalsIgnoreCase(USER_ROLE)) {
            configuration.setUser(regex);
            authorizer = new LbAuthorizer(configuration);
        }
        if (role.equalsIgnoreCase(API_ROLE)) {
            configuration.setApi(regex);
            authorizer = new LbAuthorizer(configuration);
        }
    }

    static void assertMatch(String role) {
        assertTrue(authorizer.authorize(principal, role));
    }

    static void assertNotMatch(String role) {
        assertFalse(authorizer.authorize(principal, role));
    }

    static void assertBadPattern(String role) {
        log.info("Configured bad regex pattern for role [{}]", role);
        try{
            assertNotMatch(role);
        } catch (PatternSyntaxException e) {
            log.info("Failed to compile ==> OKAY");
        }
    }

    @Test
    public void testBasic() {
        configureRole(PVFX_DATA_31, ADMIN_ROLE);
        assertMatch(ADMIN_ROLE);

        configureRole(PVFX_DATA_31, UNKNOWN_ROLE);
        assertNotMatch(UNKNOWN_ROLE); // UNKNOWN ROLE should always return FALSE

        configureRole("PVFX", USER_ROLE);
        assertNotMatch(USER_ROLE);

        configureRole("DATA", API_ROLE);
        assertNotMatch(API_ROLE);

        configureRole("31", ADMIN_ROLE);
        assertNotMatch(ADMIN_ROLE);
    }

    @Test
    public void testZeroOrMoreCharacters() {
        configureRole("PVFX(.*)", ADMIN_ROLE);
        assertMatch(ADMIN_ROLE);

        configureRole("(?i)pvfx(.*)", USER_ROLE);
        assertMatch(USER_ROLE);

        configureRole("(.*)", API_ROLE);
        assertMatch(API_ROLE);

        configureRole("PVFX_DATA_31(.*)", ADMIN_ROLE);
        assertMatch(ADMIN_ROLE);

        configureRole("(.*)_31", USER_ROLE);
        assertMatch(USER_ROLE);

        configureRole("(.*)DATA(.*)", API_ROLE);
        assertMatch(API_ROLE);

        configureRole("^.+$", ADMIN_ROLE);
        assertMatch(ADMIN_ROLE);

        configureRole("^.+$", UNKNOWN_ROLE);
        assertNotMatch(UNKNOWN_ROLE); // UNKNOWN ROLE should always return FALSE

        configureRole("(.*)DATA", USER_ROLE);
        assertNotMatch(USER_ROLE);

        configureRole("PVFX__(.*)", API_ROLE);
        assertNotMatch(API_ROLE);
    }

    @Test
    public void testBadPatterns() throws Exception {
        configureRole("^[a-zA--Z0-9_]+$", ADMIN_ROLE); // bad range
        assertBadPattern(ADMIN_ROLE);

        configureRole("^[a-zA-Z0-9_*$", USER_ROLE);    // missing ]
        assertBadPattern(USER_ROLE);

        configureRole("^[a-zA-Z0-9_]+$\\", API_ROLE);  // nothing to escape
        assertBadPattern(API_ROLE);
    }
}
