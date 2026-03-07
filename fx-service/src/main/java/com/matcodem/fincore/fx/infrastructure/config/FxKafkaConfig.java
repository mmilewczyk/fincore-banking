package com.matcodem.fincore.fx.infrastructure.config;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class FxKafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public ProducerFactory<String, String> fxProducerFactory() {
		return new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.ACKS_CONFIG, "all",
				ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
				ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1
		));
	}

	@Bean
	public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> fxProducerFactory) {
		return new KafkaTemplate<>(fxProducerFactory);
	}
}
