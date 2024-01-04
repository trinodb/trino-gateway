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

import jakarta.ws.rs.core.Response;

import java.io.Serial;
import java.io.Serializable;

/**
 * Response
 *
 * @author Wei Peng
 */
public class R<T>
        implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * success
     */
    public static final int SUCCESS = 200;

    /**
     * fail
     */
    public static final int FAIL = 500;

    /**
     * warn
     */
    public static final int WARN = 601;

    private int code;

    private String msg;

    private T data;

    public static <T> R<T> ok()
    {
        return restResult(null, SUCCESS, "Successful.");
    }

    public static <T> R<T> ok(T data)
    {
        return restResult(data, SUCCESS, "Successful.");
    }

    public static <T> R<T> ok(String msg)
    {
        return restResult(null, SUCCESS, msg);
    }

    public static <T> R<T> ok(String msg, T data)
    {
        return restResult(data, SUCCESS, msg);
    }

    public static <T> R<T> fail()
    {
        return restResult(null, FAIL, "Failed.");
    }

    public static <T> R<T> fail(String msg)
    {
        return restResult(null, FAIL, msg);
    }

    public static <T> R<T> fail(T data)
    {
        return restResult(data, FAIL, "Failed.");
    }

    public static <T> R<T> fail(String msg, T data)
    {
        return restResult(data, FAIL, msg);
    }

    public static <T> R<T> fail(int code, String msg)
    {
        return restResult(null, code, msg);
    }

    public static <T> R<T> fail(Response.Status status)
    {
        return restResult(null, status.getStatusCode(), status.getReasonPhrase());
    }

    /**
     * return warn message
     *
     * @param msg 返回内容
     * @return R
     */
    public static <T> R<T> warn(String msg)
    {
        return restResult(null, WARN, msg);
    }

    /**
     * return warn message
     *
     * @param msg  return message
     * @param data return data
     * @return R
     */
    public static <T> R<T> warn(String msg, T data)
    {
        return restResult(data, WARN, msg);
    }

    private static <T> R<T> restResult(T data, int code, String msg)
    {
        R<T> r = new R<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }

    public static <T> Boolean isError(R<T> ret)
    {
        return !isSuccess(ret);
    }

    public static <T> Boolean isSuccess(R<T> ret)
    {
        return R.SUCCESS == ret.getCode();
    }

    public R()
    {
    }

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
