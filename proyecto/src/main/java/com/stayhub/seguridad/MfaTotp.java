package com.stayhub.seguridad;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

public final class MfaTotp {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private MfaTotp() { }
    public static String secreto() {
        byte[] b = new byte[20]; new SecureRandom().nextBytes(b); return codificar(b);
    }
    public static boolean validar(String secreto, String codigo) {
        if (codigo == null || !codigo.matches("\\d{6}")) return false;
        long paso = Instant.now().getEpochSecond() / 30;
        for (long i = -1; i <= 1; i++) if (generar(secreto, paso + i).equals(codigo)) return true;
        return false;
    }
    static String generar(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(secreto), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 15;
            int n = ((h[o] & 127) << 24) | ((h[o+1] & 255) << 16) | ((h[o+2] & 255) << 8) | (h[o+3] & 255);
            return String.format("%06d", n % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String codificar(byte[] datos) {
        StringBuilder s = new StringBuilder(); int buffer=0,bits=0;
        for(byte b:datos){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){s.append(BASE32[(buffer>>(bits-=5))&31]);}}
        if(bits>0)s.append(BASE32[(buffer<<(5-bits))&31]); return s.toString();
    }
    private static byte[] decodificar(String s) {
        byte[] out=new byte[s.length()*5/8];int buffer=0,bits=0,p=0;
        for(char c:s.toUpperCase().toCharArray()){int v="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);if(v<0)throw new IllegalArgumentException();buffer=(buffer<<5)|v;bits+=5;if(bits>=8)out[p++]=(byte)(buffer>>(bits-=8));}
        return out;
    }
}
