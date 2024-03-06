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
package io.trino.gateway.ha.util;

public final class PageUtil
{
    private static final int FIRST_PAGE_NO = 1;

    private PageUtil() {}

    public static int getStart(int pageNo, int pageSize)
    {
        if (pageNo < FIRST_PAGE_NO) {
            pageNo = FIRST_PAGE_NO;
        }
        if (pageSize < 1) {
            pageSize = 0;
        }
        return (pageNo - FIRST_PAGE_NO) * pageSize;
    }
}
