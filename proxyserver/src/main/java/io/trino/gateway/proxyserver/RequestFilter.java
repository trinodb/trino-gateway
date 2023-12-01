package io.trino.gateway.proxyserver;

import io.trino.gateway.proxyserver.wrapper.MultiReadHttpServletRequest;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

public class RequestFilter implements Filter {
  private FilterConfig filterConfig = null;

  public void init(FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
  }

  public void destroy() {
    this.filterConfig = null;
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // We need to convert the ServletRequest to MultiReadRequest, so that we can intercept later
    MultiReadHttpServletRequest multiReadRequest =
        new MultiReadHttpServletRequest((HttpServletRequest) request);
    HttpServletResponseWrapper responseWrapper =
        new HttpServletResponseWrapper((HttpServletResponse) response);
    chain.doFilter(multiReadRequest, responseWrapper);
  }
}
