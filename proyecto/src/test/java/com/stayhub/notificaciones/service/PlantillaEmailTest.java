package com.stayhub.notificaciones.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlantillaEmailTest {
    @Test
    void aceptaSoloEnlacesHttpOHttpsSinCredenciales() {
        assertEquals("https://stayhub.example/reset?token=abc", PlantillaEmail.urlSegura("https://stayhub.example/reset?token=abc"));
        assertEquals("#", PlantillaEmail.urlSegura("javascript:alert(1)"));
        assertEquals("#", PlantillaEmail.urlSegura("https://usuario:clave@stayhub.example/reset"));
    }
}
