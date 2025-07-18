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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestLbAuthorizer
{
    private static final String USER = "username";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";
    private static final String API_ROLE = "API";
    private static final String UNKNOWN_ROLE = "UNKNOWN";
    private static LbAuthorizer authorizer;

    @BeforeAll
    public static void setup()
    {
        authorizer = new LbAuthorizer();
    }

    static void assertMatch(LbPrincipal principal, String role)
    {
        assertThat(authorizer.authorize(principal, role, null)).isTrue();
    }

    static void assertNotMatch(LbPrincipal principal, String role)
    {
        assertThat(authorizer.authorize(principal, role, null)).isFalse();
    }

    @Test
    public void testBasic()
    {
        LbPrincipal principal = new LbPrincipal(USER, "ADMIN_USER_API");
        assertMatch(principal, ADMIN_ROLE);
        assertMatch(principal, USER_ROLE);
        assertMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE); // UNKNOWN ROLE should always return FALSE
    }

    @Test
    public void testMultiplePrivileges()
    {
        LbPrincipal principal = new LbPrincipal(USER, "ADMIN_USER");
        assertMatch(principal, ADMIN_ROLE);
        assertMatch(principal, USER_ROLE);
        assertNotMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }

    @Test
    public void testUserApiPrivileges()
    {
        LbPrincipal principal = new LbPrincipal(USER, "USER_API");
        assertNotMatch(principal, ADMIN_ROLE);
        assertMatch(principal, USER_ROLE);
        assertMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }

    @Test
    public void testAdminOnlyPrivilege()
    {
        LbPrincipal principal = new LbPrincipal(USER, "ADMIN");
        assertMatch(principal, ADMIN_ROLE);
        assertNotMatch(principal, USER_ROLE);
        assertNotMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }

    @Test
    public void testUserOnlyPrivilege()
    {
        LbPrincipal principal = new LbPrincipal(USER, "USER");
        assertNotMatch(principal, ADMIN_ROLE);
        assertMatch(principal, USER_ROLE);
        assertNotMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }

    @Test
    public void testApiOnlyPrivilege()
    {
        LbPrincipal principal = new LbPrincipal(USER, "API");
        assertNotMatch(principal, ADMIN_ROLE);
        assertNotMatch(principal, USER_ROLE);
        assertMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }

    @Test
    public void testNoPrivileges()
    {
        LbPrincipal principal = new LbPrincipal(USER, "");
        assertNotMatch(principal, ADMIN_ROLE);
        assertNotMatch(principal, USER_ROLE);
        assertNotMatch(principal, API_ROLE);
        assertNotMatch(principal, UNKNOWN_ROLE);
    }
}
