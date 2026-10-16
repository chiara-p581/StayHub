package com.stayhub.usuarios.repository;

import com.stayhub.usuarios.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class UsuarioRepositoryJpa implements UsuarioRepository {

    @PersistenceContext(unitName = "StayHubPU")
    private EntityManager em;

    @Override
    public Usuario guardar(Usuario usuario) {
        if (usuario.getId() == null) {
            em.persist(usuario);
            return usuario;
        }
        return em.merge(usuario);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Usuario.class, id));
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        try {
            Usuario u = em.createQuery(
                    "select u from Usuario u where u.email = :email", Usuario.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return Optional.of(u);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Bloquea la fila mientras dura la transacción: si alguien usa el mismo
     * link dos veces al mismo tiempo, el segundo espera y encuentra el token ya borrado.
     */
    @Override
    public Optional<Usuario> buscarPorResetTokenHash(String hash) {
        return em.createQuery("select u from Usuario u where u.resetTokenHash = :hash", Usuario.class)
                .setParameter("hash", hash)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }
}