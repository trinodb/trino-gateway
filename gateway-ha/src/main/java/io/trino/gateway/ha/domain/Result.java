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
import jakarta.ws.rs.core.Response;

import java.io.Serial;
import java.io.Serializable;

public class Result<T>
        implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;

    public static final int FAIL = 500;

    @JsonProperty
    private int code;

    @JsonProperty
    private String msg;

    @JsonProperty
    private T data;

    public static <T> Result<T> ok()
    {
        return restResult(null, SUCCESS, "Successful.");
    }

    public static <T> Result<T> ok(T data)
    {
        return restResult(data, SUCCESS, "Successful.");
    }

    public static <T> Result<T> ok(String msg)
    {
        return restResult(null, SUCCESS, msg);
    }

    public static <T> Result<T> ok(String msg, T data)
    {
        return restResult(data, SUCCESS, msg);
    }

    public static <T> Result<T> fail(String msg)
    {
        return restResult(null, FAIL, msg);
    }

    public static <T> Result<T> fail(Response.Status status)
    {
        return restResult(null, status.getStatusCode(), status.getReasonPhrase());
    }

    private static <T> Result<T> restResult(T data, int code, String msg)
    {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }

    public static <T> Boolean isSuccess(Result<T> ret)
    {
        return Result.SUCCESS == ret.getCode();
    }

    public Result() {}

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMsg()
    {
        return msg;
    }

    public void setMsg(String msg)
    {
        this.msg = msg;
    }

    public T getData()
    {
        return data;
    }

    public void setData(T data)
    {
        this.data = data;
    }
}
