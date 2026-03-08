package com.matcodem.fincore.notification.adapter.out.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.email")
public class EmailProperties {
	private String fromAddress = "noreply@fincore.com";
	private String fromName = "FinCore Banking";
}
