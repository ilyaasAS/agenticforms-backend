package com.agenticform.security;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.repository.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Tests d'intégration sur la chaîne Spring Security réelle (filtres JWT, CSRF, rôles).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SecurityFilterChainIntegrationTest {

    private static final String TEST_PASSWORD = "ValidPass1!";

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongo.getConnectionString() + "/agenticform_test");
        registry.add("MONGO_URI", () -> mongo.getConnectionString() + "/agenticform_test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAll();
        saveVerifiedUser("user@test.com", Role.ROLE_USER);
        saveVerifiedUser("admin@test.com", Role.ROLE_ADMIN);
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login/local")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "WrongPass1!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("E-mail ou mot de passe invalide."));
    }

    @Test
    void adminRouteWithoutAdminRoleReturns403() throws Exception {
        SessionCookies session = login("user@test.com", TEST_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/forms")
                        .cookie(session.accessCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void protectedRouteWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentification requise."));
    }

    @Test
    void authenticatedPostWithoutCsrfReturns403() throws Exception {
        SessionCookies session = login("user@test.com", TEST_PASSWORD);

        mockMvc.perform(put("/api/auth/me")
                        .contentType(APPLICATION_JSON)
                        .cookie(session.accessCookie())
                        .content("""
                                {
                                  "firstName": "Test",
                                  "lastName": "User"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Jeton CSRF invalide. Rechargez la page et réessayez."));
    }

    private User saveVerifiedUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(role);
        user.setPasswordEnabled(true);
        user.setEmailVerified(true);
        user.setFullName("Test User");
        return userRepository.save(user);
    }

    private SessionCookies login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login/local")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessCookie = result.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        if (accessCookie == null) {
            throw new IllegalStateException("Cookie AF_ACCESS absent après login");
        }
        return new SessionCookies(accessCookie);
    }

    private record SessionCookies(Cookie accessCookie) {
    }
}
