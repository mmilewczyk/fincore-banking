package com.matcodem.fincore.fraud.infrastructure.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

import com.matcodem.fincore.fraud.domain.rule.FraudRule;
import com.matcodem.fincore.fraud.domain.service.FraudRuleEngine;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

/**
 * Kafka configuration for fraud-detection-service - Avro edition.
 * <p>
 * Consumer: reads PaymentInitiatedEvent (Avro) from payment-service
 * - KafkaAvroDeserializer + specific.avro.reader=true
 * - Wrapped in ErrorHandlingDeserializer to route bad messages to DLT
 * <p>
 * Producer: writes FraudCase* events (Avro) consumed by payment-service
 * - KafkaAvroSerializer
 * - Separate avroProducerFactory to avoid conflict with DLT producer
 * (DLT publisher uses String serializer, fraud events use Avro)
 * <p>
 * TWO PRODUCER FACTORIES:
 * fraudDltProducerFactory -> KafkaTemplate<String, String> -> DLT publishing (raw bytes for poison pills)
 * avroProducerFactory     -> KafkaTemplate<String, Object> -> fraud event publishing (Avro)
 * <p>
 * The DLT KafkaTemplate must use String serializer because ErrorHandlingDeserializer
 * may forward raw bytes when deserialization fails - Avro serializer can't handle raw bytes.
 */
@Configuration
@EnableKafka
public class FraudKafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${kafka.schema-registry.url:http://localhost:8081}")
	private String schemaRegistryUrl;

	@Bean
	public ConsumerFactory<String, Object> fraudConsumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "fraud-detection-service");
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
		props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000);

		// Key: String (paymentId)
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

		// Value: Avro - wrapped for poison pill protection
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
		props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
		// specific.avro.reader=true: return PaymentInitiatedEvent, not GenericRecord
		props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object> fraudKafkaListenerContainerFactory(
			ConsumerFactory<String, Object> fraudConsumerFactory,
			KafkaTemplate<String, String> fraudDltKafkaTemplate) {

		var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
		factory.setConsumerFactory(fraudConsumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

		// DLT recoverer uses String KafkaTemplate (not Avro) - DLT messages are raw bytes
		var recoverer = new DeadLetterPublishingRecoverer(fraudDltKafkaTemplate);
		var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
		errorHandler.addNotRetryableExceptions(ClassCastException.class); // schema mismatch -> DLT immediately
		factory.setCommonErrorHandler(errorHandler);

		return factory;
	}

	@Bean
	public ProducerFactory<String, String> fraudDltProducerFactory() {
		return new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.ACKS_CONFIG, "all",
				ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
		));
	}

	@Bean
	public KafkaTemplate<String, String> fraudDltKafkaTemplate(
			ProducerFactory<String, String> fraudDltProducerFactory) {
		return new KafkaTemplate<>(fraudDltProducerFactory);
	}

	@Bean
	public ProducerFactory<String, Object> avroProducerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
		props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
		props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, autoRegisterSchemas());
		props.put(ProducerConfig.ACKS_CONFIG, "all");
		props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		props.put(ProducerConfig.RETRIES_CONFIG, 3);
		props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaTemplate<String, Object> avroKafkaTemplate(
			ProducerFactory<String, Object> avroProducerFactory) {
		return new KafkaTemplate<>(avroProducerFactory);
	}

	@Bean
	public FraudRuleEngine fraudRuleEngine(List<FraudRule> rules) {
		return new FraudRuleEngine(rules);
	}

	private boolean autoRegisterSchemas() {
		String profiles = System.getProperty("spring.profiles.active", "local");
		return !profiles.contains("prod");
	}
}