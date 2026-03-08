package com.matcodem.fincore.notification.domain.port.out;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;

public interface ChannelSender {
	NotificationChannel channel();

	void send(Notification notification) throws ChannelSendException;
}