import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/** Utilidad exclusiva del entorno Docker local para completar el MFA demo. */
public final class MfaTotpCode {
    private MfaTotpCode() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--derive-key".equals(args[0])) {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(args[1].getBytes(StandardCharsets.UTF_8));
            System.out.println(Base64.getEncoder().encodeToString(key));
            return;
        }
        if (args.length != 1) throw new IllegalArgumentException("Uso: MfaTotpCode <secreto-base32>");
        System.out.println(generar(args[0], System.currentTimeMillis() / 1000 / 30));
    }

    static String generar(String secreto, long paso) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(decodificar(secreto), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
        int offset = hash[hash.length - 1] & 15;
        int numero = ((hash[offset] & 127) << 24)
                | ((hash[offset + 1] & 255) << 16)
                | ((hash[offset + 2] & 255) << 8)
                | (hash[offset + 3] & 255);
        return String.format("%06d", numero % 1_000_000);
    }

    private static byte[] decodificar(String valor) {
        byte[] salida = new byte[valor.length() * 5 / 8];
        int buffer = 0, bits = 0, posicion = 0;
        for (char caracter : valor.toUpperCase().toCharArray()) {
            int indice = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(caracter);
            if (indice < 0) throw new IllegalArgumentException("Secreto Base32 inválido");
            buffer = (buffer << 5) | indice;
            bits += 5;
            if (bits >= 8) salida[posicion++] = (byte) (buffer >> (bits -= 8));
        }
        return Arrays.copyOf(salida, posicion);
    }
}
