package com.piggymetrics.auth;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a single shared MongoDB Testcontainer for the whole test run and
 * injects its connection string as {@code spring.data.mongodb.uri}. Replaces the
 * dead flapdoodle embedded-mongo download. Registered for every test context via
 * {@code src/test/resources/META-INF/spring.factories} (test scope only).
 */
public class MongoContainerInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final MongoDBContainer MONGO =
			new MongoDBContainer(DockerImageName.parse("mongo:3.6.23"));

	static {
		MONGO.start();
	}

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		TestPropertyValues
				.of("spring.data.mongodb.uri=" + MONGO.getReplicaSetUrl("piggymetrics"))
				.applyTo(context);
	}
}
