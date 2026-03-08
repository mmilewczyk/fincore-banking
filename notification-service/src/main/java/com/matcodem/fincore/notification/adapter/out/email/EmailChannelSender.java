package com.matcodem.fincore.notification.adapter.out.email;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Email channel sender - renders Thymeleaf HTML templates and sends via SMTP/SendGrid.
 * <p>
 * Template selection:
 * NotificationType -> classpath:/templates/email/<type>.html
 * e.g. PAYMENT_COMPLETED -> templates/email/payment-completed.html
 * <p>
 * Thymeleaf context is populated with NotificationPayload.templateData entries.
 * All template variables are Strings - no arbitrary object injection.
 * <p>
 * HTML email only (no plain-text fallback in this implementation).
 * For accessibility compliance: add multipart/alternative with plain-text part.
 * <p>
 * Idempotency note:
 * SMTP does not have native idempotency. Re-sends may produce duplicates.
 * Acceptable for payment confirmations - duplicates are better than missing.
 * For fraud alerts: add SendGrid idempotency key header (custom header approach).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelSender implements com.matcodem.fincore.notification.domain.port.out.ChannelSender {

	private final JavaMailSender mailSender;
	private final TemplateEngine templateEngine;
	private final EmailProperties emailProperties;

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.EMAIL;
	}

	@Override
	public void send(Notification notification) throws com.matcodem.fincore.notification.domain.port.out.ChannelSendException {
		String recipientEmail = notification.getContact().email();
		String subject = notification.getPayload().title();
		String templateName = resolveTemplateName(notification);
		String htmlBody = renderTemplate(templateName, notification);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(emailProperties.getFromAddress(), emailProperties.getFromName());
			helper.setTo(recipientEmail);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);

			mailSender.send(message);
			log.debug("Email sent to {} for type={}, notificationId={}",
					recipientEmail, notification.getType(), notification.getId());

		} catch (MessagingException | MailException | UnsupportedEncodingException ex) {
			throw new com.matcodem.fincore.notification.domain.port.out.ChannelSendException(
					"Email send failed to %s: %s".formatted(recipientEmail, ex.getMessage()), ex);
		}
	}

	private String resolveTemplateName(Notification notification) {
		// PAYMENT_COMPLETED -> payment-completed
		return notification.getType().name().toLowerCase().replace('_', '-');
	}

	private String renderTemplate(String templateName, Notification notification) throws com.matcodem.fincore.notification.domain.port.out.ChannelSendException {
		try {
			Context ctx = new Context(Locale.ENGLISH);
			notification.getPayload().templateData().forEach(ctx::setVariable);
			ctx.setVariable("notificationId", notification.getId().toString());
			ctx.setVariable("recipientEmail", notification.getContact().email());
			return templateEngine.process("email/" + templateName, ctx);
		} catch (Exception ex) {
			throw new com.matcodem.fincore.notification.domain.port.out.ChannelSendException(
					"Template rendering failed for %s: %s".formatted(templateName, ex.getMessage()), ex);
		}
	}
}