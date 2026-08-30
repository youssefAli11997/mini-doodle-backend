package com.example.doodle;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @PostConstruct
    void setupRestTemplate() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
