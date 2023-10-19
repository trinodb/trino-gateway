package io.trino.gateway.baseapp;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.common.filter.FilterFactory;

@JsonTypeName("Log-filter-factory")
public class LogFilterFactory implements FilterFactory<ILoggingEvent> {
  @Override
  public Filter<ILoggingEvent> build() {
    return new Filter<ILoggingEvent>() {
      @Override
      public FilterReply decide(ILoggingEvent event) {
        //Don't leak the authentication information in the debug/trace mode
        if (event.getMessage() != null && event.getMessage().contains("Authorization")) {
          return FilterReply.DENY;
        } else {
          return FilterReply.NEUTRAL;
        }
      }
    };
  }
}
