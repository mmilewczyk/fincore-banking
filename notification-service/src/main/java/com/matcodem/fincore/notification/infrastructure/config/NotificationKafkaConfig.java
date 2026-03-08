package com.matcodem.fincore.notification.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

/**
 * Kafka consumer config for notification-service - Avro inbound, consumer-only.
 * <p>
 * notification-service is consumer-only: it reads payment and account events
 * but does not publish domain events to Kafka (notifications are persisted
 * to the local DB only, dispatched via SMTP/FCM/Twilio directly).
 * <p>
 * Single KafkaListenerContainerFactory for all consumers:
 * Both PaymentEventKafkaConsumer and AccountEventKafkaConsumer use the same factory.
 * This is fine because all consumed events are Avro SpecificRecords -
 * the KafkaAvroDeserializer selects the correct class via schema registry + class name.
 * <p>
 * DLT: poison pill messages (deserializable but processable) are forwarded to
 * <topic>.DLT via DeadLetterPublishingRecoverer after 3 retries.
 * The DLT producer uses StringSerializer - raw bytes for undeserializable messages.
 */
@Configuration
@EnableKafka
public class NotificationKafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${kafka.schema-registry.url:http://localhost:8081}")
	private String schemaRegistryUrl;

	@Bean
	public ConsumerFactory<String, Object> notificationConsumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 20);

		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
		props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
		props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object> notificationKafkaListenerContainerFactory(
			ConsumerFactory<String, Object> notificationConsumerFactory,
			KafkaTemplate<String, String> dltKafkaTemplate) {

		var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
		factory.setConsumerFactory(notificationConsumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

		var recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
		var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
		errorHandler.addNotRetryableExceptions(ClassCastException.class);
		factory.setCommonErrorHandler(errorHandler);

		return factory;
	}

	// DLT producer - String serializer for raw-byte forwarding of failed messages
	@Bean
	public ProducerFactory<String, String> dltProducerFactory() {
		return new DefaultKafkaProducerFactory<>(Map.of(
				org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
				org.apache.kafka.common.serialization.StringSerializer.class,
				org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
				org.apache.kafka.common.serialization.StringSerializer.class
		));
	}

	@Bean
	public KafkaTemplate<String, String> dltKafkaTemplate(
			ProducerFactory<String, String> dltProducerFactory) {
		return new KafkaTemplate<>(dltProducerFactory);
	}
}