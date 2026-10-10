package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests {
    @Value("${local.server.port}")
    private int port;

    @Test
    void loginSessionAndLogout() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        assertEquals(401, get(client, "/api/members/me").statusCode());
        assertEquals(403, post(client, "/api/auth/login", "email=test%40naver.com&password=testtest", null).statusCode());
        assertEquals(401, post(client, "/api/auth/login", "email=test%40naver.com&password=wrong", csrf(client)).statusCode());
        assertEquals(204, post(client, "/api/auth/login", "email=test%40naver.com&password=testtest", csrf(client)).statusCode());
        HttpResponse<String> member = get(client, "/api/members/me");
        assertEquals(200, member.statusCode());
        assertTrue(member.body().contains("test@naver.com"));
        assertFalse(member.body().contains("password"));
        assertEquals(200, get(client, "/api/members/me").statusCode());
        assertEquals(204, post(client, "/api/auth/logout", "", csrf(client)).statusCode());
        assertEquals(401, get(client, "/api/members/me").statusCode());
    }

    private String csrf(HttpClient client) throws Exception {
        HttpResponse<String> response = get(client, "/api/auth/csrf");
        assertEquals(200, response.statusCode());
        var matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String body, String csrf) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
