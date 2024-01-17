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
package io.trino.gateway.ha.clustermonitor;

/**
 * Healthy state is defined as backends are healthy and ready to be served
 * Pending state is defined as when the backend is switched from inactive to active. It will wait until healthcheck returns success before turning backend to healthy
 * Unhealthy state is defined as backends are returning error or not responding to healthcheck.
 */
public enum BackendStatus {
  HEALTHY,
  PENDING,
  UNHEALTHY
}
