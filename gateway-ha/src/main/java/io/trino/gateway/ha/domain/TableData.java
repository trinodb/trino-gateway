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
package io.trino.gateway.ha.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class TableData<T>
        implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Total number of table data
     */
    private long total;

    /**
     * page data
     */
    private List<T> rows;

    public TableData(List<T> list, long total)
    {
        this.rows = list;
        this.total = total;
    }

    public static <T> TableData<T> build(List<T> list, long total)
    {
        return new TableData<>(list, total);
    }

    public TableData() {}

    @JsonProperty
    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    @JsonProperty
    public List<T> getRows()
    {
        return rows;
    }

    public void setRows(List<T> rows)
    {
        this.rows = rows;
    }
}
