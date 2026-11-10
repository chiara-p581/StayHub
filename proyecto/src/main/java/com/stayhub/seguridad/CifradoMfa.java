package com.stayhub.seguridad;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

final class CifradoMfa {
    private static final SecureRandom ALEATORIO_SEGURO = new SecureRandom();
    private CifradoMfa() { }
    static String cifrar(String texto) { return transformar(Cipher.ENCRYPT_MODE, texto, null); }
    static String descifrar(String valor) {
        byte[] todo=Base64.getDecoder().decode(valor); byte[] iv=java.util.Arrays.copyOfRange(todo,0,12);
        return transformar(Cipher.DECRYPT_MODE, null, new Object[]{iv,java.util.Arrays.copyOfRange(todo,12,todo.length)});
    }
    private static String transformar(int modo,String texto,Object[] entrada){
        try{
            String k=SeguridadHttpFilter.config("stayhub.mfa.master-key");
            byte[] key=k==null?new byte[0]:Base64.getDecoder().decode(k);
            if(key.length!=32)throw new SeguridadException(503,"MFA no está configurado en el servidor");
            byte[] iv,datos;if(modo==Cipher.ENCRYPT_MODE){iv=new byte[12];ALEATORIO_SEGURO.nextBytes(iv);datos=texto.getBytes(java.nio.charset.StandardCharsets.UTF_8);}else{iv=(byte[])entrada[0];datos=(byte[])entrada[1];}
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(modo,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[] resultado=c.doFinal(datos);
            if(modo==Cipher.DECRYPT_MODE)return new String(resultado,java.nio.charset.StandardCharsets.UTF_8);
            byte[] todo=new byte[iv.length+resultado.length];System.arraycopy(iv,0,todo,0,iv.length);System.arraycopy(resultado,0,todo,iv.length,resultado.length);return Base64.getEncoder().encodeToString(todo);
        }catch(SeguridadException e){throw e;}catch(Exception e){throw new SeguridadException(503,"MFA no está disponible");}
    }
}
