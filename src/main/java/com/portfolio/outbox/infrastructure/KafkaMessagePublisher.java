package com.portfolio.outbox.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.outbox.application.port.EventPublisher;
import com.portfolio.outbox.domain.CommerceEvent;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.errors.TopicExistsException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class KafkaMessagePublisher implements EventPublisher {
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final JsonSchemaContract contract;

    public KafkaMessagePublisher(String bootstrapServers, String topic, ObjectMapper objectMapper, Duration maxBlock) {
        this.topic = topic;
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.contract = new JsonSchemaContract(Path.of("contracts", "commerce-event-v1.schema.json"));

        Properties properties = producerProperties(bootstrapServers, maxBlock);
        this.producer = new KafkaProducer<>(properties);
    }

    @Override
    public void publish(CommerceEvent event) throws Exception {
        JsonNode json = objectMapper.valueToTree(event);
        contract.validate(json);
        producer.send(new ProducerRecord<>(topic, event.aggregateId().toString(), objectMapper.writeValueAsString(json)))
                .get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(3));
    }

    public static void ensureTopic(String bootstrapServers, String topic) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("request.timeout.ms", "5000");
        try (AdminClient admin = AdminClient.create(properties)) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }
    }

    private static Properties producerProperties(String bootstrapServers, Duration maxBlock) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Long.toString(maxBlock.toMillis()));
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000");
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        return properties;
    }
}
