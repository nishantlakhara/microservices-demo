package com.microservices.demo.twitter.to.kafka.service;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.microservices.demo")
public class TwitterToKafkaServiceApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(TwitterToKafkaServiceApplication.class, args);
    }

    private static final Logger LOG = LoggerFactory.getLogger(TwitterToKafkaServiceApplication.class);

    //More robust and thread safe operations. Favors immutability and no reflection and no @Autowired
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    private final StreamRunner streamRunner;

    //Constructor injection. Better than field injection
    public TwitterToKafkaServiceApplication(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                            StreamRunner streamRunner) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.streamRunner = streamRunner;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("App starts...");
        LOG.info(twitterToKafkaServiceConfigData.getTwitterKeywords().toString());
        LOG.info(twitterToKafkaServiceConfigData.getWelcomeMessage());
        streamRunner.start();
    }
}
