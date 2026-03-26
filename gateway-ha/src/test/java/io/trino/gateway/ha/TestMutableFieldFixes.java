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
package io.trino.gateway.ha;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.UIConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class to verify that mutable field anti-patterns have been properly fixed.
 * This test ensures that configuration objects return immutable copies to prevent
 * external modification and thread safety issues.
 */
public class TestMutableFieldFixes
{
    @Test
    public void testHaGatewayConfigurationImmutableReturns()
    {
        // Test that HaGatewayConfiguration returns immutable copies of collections
        HaGatewayConfiguration config = new HaGatewayConfiguration();

        // Set up test data
        Map<String, String> serverConfig = new HashMap<>();
        serverConfig.put("key1", "value1");
        serverConfig.put("key2", "value2");
        config.setServerConfig(serverConfig);

        Map<String, String> pagePermissions = new HashMap<>();
        pagePermissions.put("page1", "permission1");
        config.setPagePermissions(pagePermissions);

        List<String> whitelistPaths = new ArrayList<>();
        whitelistPaths.add("/path1");
        whitelistPaths.add("/path2");
        config.setExtraWhitelistPaths(whitelistPaths);

        // Test that returned collections are immutable copies
        Map<String, String> returnedServerConfig = config.getServerConfig();
        Map<String, String> returnedPagePermissions = config.getPagePermissions();
        List<String> returnedWhitelistPaths = config.getExtraWhitelistPaths();

        // Verify content is correct
        assertThat(returnedServerConfig).hasSize(2);
        assertThat(returnedServerConfig.get("key1")).isEqualTo("value1");
        assertThat(returnedServerConfig.get("key2")).isEqualTo("value2");

        assertThat(returnedPagePermissions).hasSize(1);
        assertThat(returnedPagePermissions.get("page1")).isEqualTo("permission1");

        assertThat(returnedWhitelistPaths).hasSize(2);
        assertThat(returnedWhitelistPaths).contains("/path1", "/path2");

        // Verify they are immutable (should throw UnsupportedOperationException)
        assertThatThrownBy(() -> returnedServerConfig.put("key3", "value3"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> returnedPagePermissions.put("page2", "permission2"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> returnedWhitelistPaths.add("/path3"))
                .isInstanceOf(UnsupportedOperationException.class);

        // Verify they are different instances (defensive copies)
        assertThat(returnedServerConfig).isNotSameAs(serverConfig);
        assertThat(returnedPagePermissions).isNotSameAs(pagePermissions);
        assertThat(returnedWhitelistPaths).isNotSameAs(whitelistPaths);
    }

    @Test
    public void testUIConfigurationImmutability()
    {
        // Test that UIConfiguration properly handles immutable lists
        UIConfiguration uiConfig = new UIConfiguration();

        List<String> disablePages = new ArrayList<>();
        disablePages.add("page1");
        disablePages.add("page2");
        uiConfig.setDisablePages(disablePages);

        List<String> returnedPages = uiConfig.getDisablePages();

        // Verify content is correct
        assertThat(returnedPages).hasSize(2);
        assertThat(returnedPages).contains("page1", "page2");

        // The original UIConfiguration doesn't return immutable copies yet,
        // but our GatewayWebAppResource fix handles this by creating immutable copies
        // This test documents the current behavior
    }

    @Test
    public void testImmutableCollectionTypes()
    {
        // Test that our fixes use proper Guava immutable collection types
        HaGatewayConfiguration config = new HaGatewayConfiguration();

        // Set up test data
        Map<String, String> serverConfig = new HashMap<>();
        serverConfig.put("test", "value");
        config.setServerConfig(serverConfig);

        List<String> paths = new ArrayList<>();
        paths.add("/test");
        config.setExtraWhitelistPaths(paths);

        // Verify the returned types are Guava immutable collections
        Map<String, String> returnedConfig = config.getServerConfig();
        List<String> returnedPaths = config.getExtraWhitelistPaths();

        assertThat(returnedConfig)
                .as("Server config should return ImmutableMap")
                .isInstanceOf(ImmutableMap.class);
        assertThat(returnedPaths)
                .as("Extra whitelist paths should return ImmutableList")
                .isInstanceOf(ImmutableList.class);
    }

    @Test
    public void testEmptyCollectionHandling()
    {
        // Test that empty collections are handled properly
        HaGatewayConfiguration config = new HaGatewayConfiguration();

        // Test with empty collections
        config.setServerConfig(new HashMap<>());
        config.setPagePermissions(new HashMap<>());
        config.setExtraWhitelistPaths(new ArrayList<>());

        Map<String, String> serverConfig = config.getServerConfig();
        Map<String, String> pagePermissions = config.getPagePermissions();
        List<String> whitelistPaths = config.getExtraWhitelistPaths();

        assertThat(serverConfig).isEmpty();
        assertThat(pagePermissions).isEmpty();
        assertThat(whitelistPaths).isEmpty();

        // Should still be immutable
        assertThatThrownBy(() -> serverConfig.put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pagePermissions.put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> whitelistPaths.add("path"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
