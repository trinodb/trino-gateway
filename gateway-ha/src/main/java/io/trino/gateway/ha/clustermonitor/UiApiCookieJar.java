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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class UiApiCookieJar
        implements CookieJar
{
    private final Multimap<String, Cookie> cookieStore = ArrayListMultimap.create();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies)
    {
        String addr = url.host() + ":" + url.port();
        cookieStore.putAll(addr, cookies);
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url)
    {
        String addr = url.host() + ":" + url.port();
        return cookieStore.get(addr).stream().collect(toImmutableList());
    }
}
