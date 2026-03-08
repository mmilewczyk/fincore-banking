package com.matcodem.fincore.payment.infrastructure.config;

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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

	@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
	private String bootstrapServers;

	@Value("${kafka.schema-registry.url:http://localhost:8081}")
	private String schemaRegistryUrl;

	@Bean
	public ConsumerFactory<String, Object> paymentConsumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service");
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
		props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);

		// Key: plain String (paymentId)
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

		// Value: Avro - wrapped in ErrorHandlingDeserializer for poison pill protection.
		// If a message cannot be deserialized (corrupted bytes, wrong schema, unknown schema ID),
		// ErrorHandlingDeserializer catches the exception and the DefaultErrorHandler routes to DLT.
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);

		// Schema Registry connection - required by KafkaAvroDeserializer
		props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

		// specific.avro.reader=true: return generated SpecificRecord subclass (type-safe).
		// false would return GenericRecord (Map-like, stringly typed).
		props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object> paymentKafkaListenerContainerFactory(
			ConsumerFactory<String, Object> paymentConsumerFactory) {

		var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
		factory.setConsumerFactory(paymentConsumerFactory);
		factory.setConcurrency(3);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

		// Retry 3 times with 1s delay, then DLT
		var errorHandler = new DefaultErrorHandler(new FixedBackOff(1_000L, 3L));

		// Non-retriable: programming/data errors that won't resolve on retry
		errorHandler.addNotRetryableExceptions(
				IllegalArgumentException.class,
				IllegalStateException.class,
				// ClassCastException = wrong Avro type delivered on wrong topic -> poison pill -> DLT
				ClassCastException.class
		);

		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}