package com.stayhub.usuarios.dto;

public record LoginRequest(String email, String password, String codigoMfa) {
    public LoginRequest(String email, String password) { this(email, password, null); }
}
