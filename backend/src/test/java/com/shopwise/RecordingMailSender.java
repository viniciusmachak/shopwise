package com.shopwise;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Keeps sent e-mails in memory instead of talking to an SMTP server.
 */
public class RecordingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
        sent.addAll(List.of(simpleMessages));
    }

    public List<SimpleMailMessage> sent() {
        return sent;
    }

    public List<SimpleMailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email)).toList();
    }
}
