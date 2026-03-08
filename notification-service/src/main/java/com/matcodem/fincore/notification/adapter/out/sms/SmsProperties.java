package com.matcodem.fincore.notification.adapter.out.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.sms")
public class SmsProperties {
	private String accountSid;
	private String authToken;
	private String fromNumber;
}