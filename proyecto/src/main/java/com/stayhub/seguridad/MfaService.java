package com.stayhub.seguridad;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Stateless
public class MfaService {
    @PersistenceContext(unitName="StayHubPU") EntityManager em;
    public boolean habilitado(Long id){return Boolean.TRUE.equals(em.createNativeQuery("SELECT mfa_habilitado FROM usuario WHERE id=?1").setParameter(1,id).getSingleResult());}
    public boolean validar(Long id,String codigo){
        Object valor=em.createNativeQuery("SELECT mfa_secret_cifrado FROM usuario WHERE id=?1 AND mfa_habilitado=true").setParameter(1,id).getResultStream().findFirst().orElse(null);
        return valor instanceof String s && MfaTotp.validar(CifradoMfa.descifrar(s),codigo);
    }
    public boolean validarAcceso(Long id, String codigo) {
        if (validar(id, codigo)) return true;
        if (!CodigosRecuperacionMfa.tieneFormato(codigo)) return false;
        int usados = em.createNativeQuery("""
                UPDATE mfa_codigo_recuperacion
                   SET usado = true, usado_en = ?3
                 WHERE usuario_id = ?1 AND codigo_hash = ?2 AND usado = false
                """)
                .setParameter(1, id)
                .setParameter(2, CodigosRecuperacionMfa.hash(codigo))
                .setParameter(3, OffsetDateTime.now())
                .executeUpdate();
        return usados == 1;
    }
    public Map<String,Object> preparar(Long id,String email){
        String secreto=MfaTotp.secreto();
        int actualizados=em.createNativeQuery("UPDATE usuario SET mfa_secret_cifrado=?1 WHERE id=?2 AND rol='ADMIN' AND mfa_habilitado=false").setParameter(1,CifradoMfa.cifrar(secreto)).setParameter(2,id).executeUpdate();
        if(actualizados!=1)throw new SeguridadException(409,"MFA ya está activado");
        String uri="otpauth://totp/StayHub:"+URLEncoder.encode(email,StandardCharsets.UTF_8)+"?secret="+secreto+"&issuer=StayHub&digits=6&period=30";
        return Map.of("secreto",secreto,"uri",uri);
    }
    public List<String> activar(Long id,String codigo){
        Object valor=em.createNativeQuery("SELECT mfa_secret_cifrado FROM usuario WHERE id=?1").setParameter(1,id).getResultStream().findFirst().orElse(null);
        if(!(valor instanceof String s)||!MfaTotp.validar(CifradoMfa.descifrar(s),codigo))throw new SeguridadException(400,"Código MFA inválido");
        em.createNativeQuery("UPDATE usuario SET mfa_habilitado=true WHERE id=?1").setParameter(1,id).executeUpdate();
        return regenerarCodigos(id);
    }
    public List<String> regenerarCodigos(Long id) {
        if (!habilitado(id)) throw new SeguridadException(409,"MFA no está activado");
        List<String> codigos = CodigosRecuperacionMfa.generar();
        em.createNativeQuery("DELETE FROM mfa_codigo_recuperacion WHERE usuario_id=?1")
                .setParameter(1,id).executeUpdate();
        for (String recuperacion : codigos) {
            em.createNativeQuery("""
                    INSERT INTO mfa_codigo_recuperacion
                        (usuario_id, codigo_hash, usado, creado_en)
                    VALUES (?1, ?2, false, ?3)
                    """)
                    .setParameter(1,id)
                    .setParameter(2,CodigosRecuperacionMfa.hash(recuperacion))
                    .setParameter(3,OffsetDateTime.now())
                    .executeUpdate();
        }
        return codigos;
    }
    public void reiniciar(Long id) {
        em.createNativeQuery("DELETE FROM mfa_codigo_recuperacion WHERE usuario_id=?1")
                .setParameter(1,id).executeUpdate();
        int actualizados = em.createNativeQuery("""
                UPDATE usuario
                   SET mfa_habilitado=false, mfa_secret_cifrado=null
                 WHERE id=?1 AND rol='ADMIN'
                """).setParameter(1,id).executeUpdate();
        if (actualizados != 1) throw new SeguridadException(404,"Administrador no disponible");
    }
}
