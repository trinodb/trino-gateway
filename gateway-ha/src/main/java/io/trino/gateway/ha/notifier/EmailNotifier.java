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
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class EmailNotifier implements Notifier {
  private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
  private final NotifierConfiguration notifierConfiguration;
  private final Properties props;

  public EmailNotifier(NotifierConfiguration notifierConfiguration) {
    this.notifierConfiguration = notifierConfiguration;
    this.props = System.getProperties();
    this.props.put("mail.smtp.starttls.enable", notifierConfiguration.isStartTlsEnabled());
    this.props.put("mail.smtp.auth", notifierConfiguration.isSmtpAuthEnabled());
    this.props.put("mail.smtp.port", notifierConfiguration.getSmtpPort());

    if (notifierConfiguration.getSmtpHost() != null) {
      this.props.put("mail.smtp.host", notifierConfiguration.getSmtpHost());
    }
    if (notifierConfiguration.getSmtpUser() != null) {
      this.props.put("mail.smtp.user", notifierConfiguration.getSmtpUser());
    }
    if (notifierConfiguration.getSmtpPassword() != null) {
      this.props.put("mail.smtp.password", notifierConfiguration.getSmtpPassword());
    }
  }

  @Override
  public void sendNotification(String subject, String content) {
    sendNotification(
        notifierConfiguration.getSender(),
        notifierConfiguration.getRecipients(),
        "Trino Error: " + subject,
        content);
  }

  @Override
  public void sendNotification(
      String from, List<String> recipients, String subject, String content) {
    if (recipients.size() > 0) {
      Session session = Session.getDefaultInstance(props);
      MimeMessage message = new MimeMessage(session);
      try {
        message.setFrom(new InternetAddress(from));

        // To get the array of addresses
        recipients.forEach(
            r -> {
              try {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(r));
              } catch (Exception e) {
                log.error("Recipient email [" + e + "] could not be added", e);
              }
            });
        message.setSubject(subject);
        message.setText(content);
        try (Transport transport = session.getTransport("smtp")) {
          if (notifierConfiguration.isSmtpAuthEnabled()) {
            transport.connect(
                notifierConfiguration.getSmtpHost(),
                notifierConfiguration.getSmtpUser(),
                notifierConfiguration.getSmtpPassword());
          } else {
            transport.connect();
          }
          transport.sendMessage(message, message.getAllRecipients());
        } catch (Exception e) {
          log.error("Error creating email transport client", e);
        }
        log.debug("Sent message [{}] successfully.", content);
      } catch (Exception e) {
        log.error("Error sending alert", e);
      }
    } else {
      log.warn("No recipients configured to send app notification [{}]", content);
    }
  }
}
