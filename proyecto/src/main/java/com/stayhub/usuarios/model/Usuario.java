package com.stayhub.usuarios.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolUsuario rol;

    /** Hash SHA-256 del token de recuperación (nunca el token en sí). */
    @Column(name = "reset_token_hash", length = 64, unique = true)
    private String resetTokenHash;

    @Column(name = "reset_token_vencimiento")
    private LocalDateTime resetTokenVencimiento;

    @Version
    private Long version;

    protected Usuario() {
        // requerido por JPA
    }

    public Usuario(String email, String passwordHash, String nombre, String apellido, RolUsuario rol) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.apellido = apellido;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public RolUsuario getRol() { return rol; }

    public void actualizarPerfil(String email, String nombre, String apellido, String nuevoPasswordHash) {
        this.email = email;
        this.nombre = nombre;
        this.apellido = apellido;
        if (nuevoPasswordHash != null) this.passwordHash = nuevoPasswordHash;
    }

    // ---- Recuperación de contraseña ----

    public void generarTokenRecuperacion(String hash, LocalDateTime vencimiento) {
        this.resetTokenHash = hash;
        this.resetTokenVencimiento = vencimiento;
    }

    /** true si se pidió un link hace menos de 1 minuto (evita que spameen pedidos). */
    public boolean recuperacionReciente() {
        return resetTokenVencimiento != null
                && resetTokenVencimiento.isAfter(LocalDateTime.now().plusMinutes(59));
    }

    public boolean tokenVigente() {
        return resetTokenHash != null && resetTokenVencimiento != null
                && resetTokenVencimiento.isAfter(LocalDateTime.now());
    }

    public void limpiarTokenRecuperacion() {
        this.resetTokenHash = null;
        this.resetTokenVencimiento = null;
    }
}