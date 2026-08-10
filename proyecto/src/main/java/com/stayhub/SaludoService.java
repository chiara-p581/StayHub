package com.stayhub;

import jakarta.ejb.Stateless;

@Stateless
public class SaludoService {

    public String mensaje() {
        return "Hola desde el contenedor";
    }
}
