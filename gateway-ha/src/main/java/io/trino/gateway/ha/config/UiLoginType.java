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
package io.trino.gateway.ha.config;

/**
 * How the UI_API cluster stats monitor authenticates against a backend Trino cluster.
 * Trino 483 replaced the previous Web UI with the preview Web UI, which uses a different
 * login resource and expects a JSON request body.
 */
public enum UiLoginType
{
    /**
     * Post a JSON body to {@code /ui/auth/login}, the login resource of the Web UI in
     * Trino 483 and later.
     */
    AUTH_API,
    /**
     * Post a form encoded body to {@code /ui/login}, the login resource of the Web UI in
     * Trino 482 and earlier.
     */
    FORM,
}
