package io.trino.gateway.proxyserver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpHeaders;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;

/* Order of control => rewriteTarget, preConnectionHook, postConnectionHook. */
public class ProxyHandler
{
    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

    protected String rewriteTarget(HttpServletRequest request)
    {
        // Dont override this unless absolutely needed.
        return null;
    }

    /**
     * Request interceptor.
     */
    public void preConnectionHook(HttpServletRequest request, Request proxyRequest)
    {
        // you may override it.
    }

    /**
     * Response interceptor default.
     */
    protected void postConnectionHook(
            HttpServletRequest request,
            HttpServletResponse response,
            byte[] buffer,
            int offset,
            int length,
            Callback callback)
    {
        try {
            response.getOutputStream().write(buffer, offset, length);
            callback.succeeded();
        }
        catch (Throwable var9) {
            callback.failed(var9);
        }
    }

    protected void debugLogHeaders(HttpServletRequest request)
    {
        if (log.isDebugEnabled()) {
            log.debug("-------HttpServletRequest headers---------");
            Enumeration<String> headers = request.getHeaderNames();
            while (headers.hasMoreElements()) {
                String header = headers.nextElement();
                log.debug(header + "->" + request.getHeader(header));
            }
        }
    }

    protected void debugLogHeaders(HttpServletResponse response)
    {
        if (log.isDebugEnabled()) {
            log.debug("-------HttpServletResponse headers---------");
            Collection<String> headers = response.getHeaderNames();
            for (String header : headers) {
                log.debug(header + "->" + response.getHeader(header));
            }
        }
    }

    protected void debugLogHeaders(Request proxyRequest)
    {
        if (log.isDebugEnabled()) {
            log.debug("-------Request proxyRequest headers---------");
            HttpFields httpFields = proxyRequest.getHeaders();
            log.debug(httpFields.toString());
        }
    }

    protected boolean isGZipEncoding(HttpServletResponse response)
    {
        String contentEncoding = response.getHeader(HttpHeaders.CONTENT_ENCODING);
        return contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");
    }

    protected String plainTextFromGz(byte[] compressed)
            throws IOException
    {
        final StringBuilder outStr = new StringBuilder();
        if ((compressed == null) || (compressed.length == 0)) {
            return "";
        }
        if (isCompressed(compressed)) {
            final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
            final BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(gis, Charset.defaultCharset()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                outStr.append(line);
            }
            gis.close();
        }
        else {
            outStr.append(compressed);
        }
        return outStr.toString();
    }

    protected boolean isCompressed(final byte[] compressed)
    {
        return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC))
                && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }
}
