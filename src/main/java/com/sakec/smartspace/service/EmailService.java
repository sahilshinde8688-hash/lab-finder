package com.sakec.smartspace.service;

import com.sakec.smartspace.util.Config;
import com.sakec.smartspace.util.Log;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class EmailService {
    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return false;
        }
        if (!Config.getBool("mail.enabled", false)) {
            return false;
        }

        String host = Config.get("mail.smtp.host", "").trim();
        String from = Config.get("mail.smtp.from", "").trim();
        if (host.isBlank() || from.isBlank()) {
            Log.warn("SMTP email is not configured: mail.smtp.host and mail.smtp.from must be set.");
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", Config.get("mail.smtp.port", "587"));
            props.put("mail.smtp.starttls.enable", Config.get("mail.smtp.starttls", "true"));
            props.put("mail.smtp.auth", Config.get("mail.smtp.auth", "false"));
            props.put("mail.smtp.ssl.trust", host);

            String username = Config.get("mail.smtp.username", "").trim();
            String password = Config.get("mail.smtp.password", "").trim();
            Session session = Session.getInstance(props, username.isBlank() || password.isBlank()
                    ? null
                    : new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(body, StandardCharsets.UTF_8.name());
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            Log.error("Could not send email to " + to, e);
            return false;
        }
    }
}
