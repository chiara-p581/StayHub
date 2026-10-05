package com.stayhub.usuarios.dto;

public record ResetPasswordRequest(String token, String nuevaPassword) { }