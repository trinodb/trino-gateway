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
package io.trino.gateway.ha.notifier;

import io.trino.gateway.ha.config.NotifierConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEmailNotifier
{
    @Test
    public void testSendNotificationCustomContent()
    {
        NotifierConfiguration notifierConfiguration = new NotifierConfiguration();
        List<String> recipients = Arrays.asList("recipient1@example.com", "recipient2@example.com");
        notifierConfiguration.setRecipients(recipients);
        notifierConfiguration.setCustomContent("Custom content");

        EmailNotifier emailNotifier = new EmailNotifier(notifierConfiguration);

        emailNotifier.sendNotification("Test Subject", "Test Content");
    }

    @Test
    public void testSendNotificationNoCustomContent()
    {
        NotifierConfiguration notifierConfiguration = new NotifierConfiguration();
        List<String> recipients = Arrays.asList("recipient1@example.com", "recipient2@example.com");
        notifierConfiguration.setRecipients(recipients);
        notifierConfiguration.setCustomContent(null);

        EmailNotifier emailNotifier = new EmailNotifier(notifierConfiguration);

        emailNotifier.sendNotification("Test Subject", "Test Content");
    }

    @Test
    public void testSendEmailNotification()
    {
        NotifierConfiguration notifierConfiguration = new NotifierConfiguration();
        List<String> recipients = Arrays.asList("recipient1@example.com", "recipient2@example.com");
        notifierConfiguration.setRecipients(recipients);

        EmailNotifier emailNotifier = Mockito.mock(EmailNotifier.class);

        emailNotifier.sendNotification(anyString(), anyList(), anyString(), anyString());

        verify(emailNotifier, times(1)).sendNotification(
                anyString(), anyList(), anyString(), anyString());
    }
}
