package com.microservices.demo.kafka.admin.client;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.config.RetryConfigData;
import com.microservices.demo.kafka.admin.exception.KafkaClientException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class KafkaAdminClient {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    private final KafkaConfigData kafkaConfigData;

    private final RetryConfigData retryConfigData;

    private final AdminClient adminClient;

    private final RetryTemplate retryTemplate;

    private final WebClient webClient;

    public KafkaAdminClient(KafkaConfigData kafkaConfigData,
                            RetryConfigData retryConfigData,
                            AdminClient adminClient,
                            RetryTemplate retryTemplate,
                            WebClient webClient) {
        this.kafkaConfigData = kafkaConfigData;
        this.retryConfigData = retryConfigData;
        this.adminClient = adminClient;
        this.retryTemplate = retryTemplate;
        this.webClient = webClient;
    }

    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
        } catch (Throwable t) {
            throw new KafkaClientException("Reached max number of retries for creating topic(s)!", t);
        }
        checkTopicsCreated();
    }

    private void checkTopicsCreated() {
        Collection<TopicListing> topics = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        Integer multiplier = retryConfigData.getMultiplier();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for(String topic: kafkaConfigData.getTopicNamesToCreate()) {
           while(!isTopicCreated(topic, topics)) {
               checkMaxRetry(retryCount++, maxRetry);
               sleep(sleepTimeMs);
               sleepTimeMs += multiplier;
               topics = getTopics();
           }
        }
    }

    private void sleep(Long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            throw new KafkaClientException("Error while sleeping for waiting new created topic(s)!");
        }
    }

    private void checkMaxRetry(int retry, Integer maxRetry) {
        if(retry > maxRetry) {
            throw new KafkaClientException("Reached max no of retries for reading kafka topic(s)!");
        }
    }

    private boolean isTopicCreated(String topicName, Collection<TopicListing> topics) {
        if(topics == null) {
            return false;
        }
        return topics.stream().anyMatch(topic -> topic.name().equals(topicName));
    }

    private CreateTopicsResult doCreateTopics(RetryContext retryContext) {
        List<String> topicNames = kafkaConfigData.getTopicNamesToCreate();
        LOG.info("Creating {} topic(s), attempt {}", topicNames.size(), retryContext.getRetryCount());

        List<NewTopic> kafkaTopics = topicNames.stream()
                .map(topic -> new NewTopic(
                        topic.trim(),
                        kafkaConfigData.getNumberOfPartitions(),
                        kafkaConfigData.getReplicationFactor()
                )).collect(Collectors.toList());

        return adminClient.createTopics(kafkaTopics);
    }

    private Collection<TopicListing> getTopics() {
        Collection<TopicListing> topics;
        try {
            topics = retryTemplate.execute(this::doGetTopics);
        } catch (Throwable t) {
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!", t);
        }
        return topics;
    }

    private Collection<TopicListing> doGetTopics(RetryContext retryContext)
            throws ExecutionException, InterruptedException {
        LOG.info("Read kafka topic {}, attempt {}",
                kafkaConfigData.getTopicNamesToCreate().toArray());
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        if(topics != null) {
            topics.forEach(topic -> LOG.debug("Topic with name {}", topic.name()));
        }
        return topics;
    }

    public void checkSchemaRegistry() {
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        Integer multiplier = retryConfigData.getMultiplier();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();

        while(!getSchemaRegistryStatus().is2xxSuccessful()) {
             checkMaxRetry(retryCount++, maxRetry);
             sleep(sleepTimeMs);
             sleepTimeMs += multiplier;
        }
    }

    private HttpStatus getSchemaRegistryStatus() {
        try {
            return webClient.
                    method(HttpMethod.GET)
                    .uri(kafkaConfigData.getSchemaRegistryUrl())
                    .exchange()
                    .map(ClientResponse::statusCode)
                    .block();
        } catch (Exception e) {
            LOG.error("An exception occured while connecting to schema registry", e);
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
    }
}
