package com.matcodem.fincore.account.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Bean
	public NewTopic accountCreatedTopic() {
		return TopicBuilder.name("fincore.accounts.account-created")
				.partitions(3)
				.replicas(1)
				.build();
	}

	@Bean
	public NewTopic accountCreditedTopic() {
		return TopicBuilder.name("fincore.accounts.account-credited")
				.partitions(3)
				.replicas(1)
				.build();
	}

	@Bean
	public NewTopic accountDebitedTopic() {
		return TopicBuilder.name("fincore.accounts.account-debited")
				.partitions(3)
				.replicas(1)
				.build();
	}

	@Bean
	public NewTopic accountFrozenTopic() {
		return TopicBuilder.name("fincore.accounts.account-frozen")
				.partitions(3)
				.replicas(1)
				.build();
	}

	@Bean
	public ProducerFactory<String, Object> producerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		props.put(ProducerConfig.ACKS_CONFIG, "all");                  // Wait for all replicas
		props.put(ProducerConfig.RETRIES_CONFIG, 3);
		props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);     // Exactly-once producer
		props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaTemplate<String, Object> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}
}
