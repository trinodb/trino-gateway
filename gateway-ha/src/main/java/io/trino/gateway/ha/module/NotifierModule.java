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
package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.NotifierConfiguration;
import io.trino.gateway.ha.notifier.EmailNotifier;
import io.trino.gateway.ha.notifier.Notifier;

public class NotifierModule
        extends AppModule<HaGatewayConfiguration, Environment>
{
    public NotifierModule(HaGatewayConfiguration config, Environment env)
    {
        super(config, env);
    }

    @Provides
    @Singleton
    public Notifier provideNotifier()
    {
        NotifierConfiguration notifierConfiguration = getConfiguration().getNotifier();
        return new EmailNotifier(notifierConfiguration);
    }
}
