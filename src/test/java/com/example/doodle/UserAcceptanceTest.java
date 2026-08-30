package com.example.doodle;

import com.example.doodle.common.exception.ErrorResponse;
import com.example.doodle.user.api.CreateUserRequest;
import com.example.doodle.user.application.UserDto;
import com.example.doodle.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAcceptanceTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /users creates a new user and returns 201 Created")
    void createUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest("Alice Smith", "alice@example.com");

        ResponseEntity<UserDto> response = restTemplate.postForEntity("/users", request, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Alice Smith");
        assertThat(response.getBody().email()).isEqualTo("alice@example.com");
        assertThat(response.getBody().createdAt()).isNotNull();
        assertThat(response.getBody().updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /users with duplicate email returns 409 Conflict")
    void createDuplicateUserReturnsConflict() {
        CreateUserRequest request = new CreateUserRequest("Alice", "alice@example.com");
        restTemplate.postForEntity("/users", request, UserDto.class);

        CreateUserRequest duplicateRequest = new CreateUserRequest("Alice 2", "alice@example.com");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/users", duplicateRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("POST /users with blank name or email returns 400 Bad Request")
    void createUserWithBlankFieldsReturnsBadRequest() {
        CreateUserRequest invalidRequest = new CreateUserRequest("", "invalid-email");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/users", invalidRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /users/{userId} returns 200 OK for existing user and 404 for non-existing")
    void getExistingAndNonExistingUser() {
        CreateUserRequest request = new CreateUserRequest("Bob", "bob@example.com");
        ResponseEntity<UserDto> created = restTemplate.postForEntity("/users", request, UserDto.class);
        UUID userId = created.getBody().id();

        ResponseEntity<UserDto> getResponse = restTemplate.getForEntity("/users/" + userId, UserDto.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id()).isEqualTo(userId);
        assertThat(getResponse.getBody().name()).isEqualTo("Bob");

        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<ErrorResponse> notFoundResponse = restTemplate.getForEntity("/users/" + nonExistentId, ErrorResponse.class);
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundResponse.getBody()).isNotNull();
        assertThat(notFoundResponse.getBody().code()).isEqualTo("USER_NOT_FOUND");
    }
}
