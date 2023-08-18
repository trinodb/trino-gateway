package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.NotifierConfiguration;
import io.trino.gateway.ha.notifier.EmailNotifier;
import io.trino.gateway.ha.notifier.Notifier;

public class NotifierModule extends AppModule<HaGatewayConfiguration, Environment> {

  public NotifierModule(HaGatewayConfiguration config, Environment env) {
    super(config, env);
  }

  @Provides
  @Singleton
  public Notifier provideNotifier() {
    NotifierConfiguration notifierConfiguration = getConfiguration().getNotifier();
    return new EmailNotifier(notifierConfiguration);
  }
}
