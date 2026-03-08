package com.matcodem.fincore.fx.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

/**
 * Kafka producer configuration for FX service — Avro edition.
 * <p>
 * FX service is producer-only (no @KafkaListener).
 * Payment service pushes FX requests via REST (FxServiceWebClient), not Kafka.
 * FX results are pushed back to payment-service via REST response (synchronous).
 * These Avro events are for analytics / audit / rate-caching consumers.
 */
@Configuration
public class FxKafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${kafka.schema-registry.url:http://localhost:8081}")
	private String schemaRegistryUrl;

	@Bean
	public ProducerFactory<String, Object> fxProducerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
		props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
		props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, autoRegisterSchemas());
		props.put(ProducerConfig.ACKS_CONFIG, "all");
		props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaTemplate<String, Object> avroKafkaTemplate(ProducerFactory<String, Object> fxProducerFactory) {
		return new KafkaTemplate<>(fxProducerFactory);
	}

	private boolean autoRegisterSchemas() {
		String profiles = System.getProperty("spring.profiles.active", "local");
		return !profiles.contains("prod");
	}
}