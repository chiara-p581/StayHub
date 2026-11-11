package com.stayhub.usuarios.dto;

import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginJsonTest {
    @Test void loginInicialSoloRequiereEmailYPassword() throws Exception {
        try (var jsonb = JsonbBuilder.create()) {
            LoginRequest login = jsonb.fromJson("{\"email\":\"admin@stayhub.test\",\"password\":\"secreto\"}", LoginRequest.class);
            assertEquals("admin@stayhub.test", login.email());
            assertEquals("secreto", login.password());
        }
    }
}
