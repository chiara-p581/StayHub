import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Ejecuta la migración y devuelve solamente IDs de administradores. */
public class AplicarControlesSeguridad {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Falta el archivo SQL");
        String url = requerido("STAYHUB_SETUP_DB_URL");
        String usuario = requerido("STAYHUB_SETUP_DB_USER");
        String password = requerido("STAYHUB_SETUP_DB_PASSWORD");
        String sql = Files.readString(Path.of(args[0])).replaceAll("(?m)^\\s*--.*$", "");

        try (Connection conexion = DriverManager.getConnection(url, usuario, password)) {
            conexion.setAutoCommit(false);
            try (Statement sentencia = conexion.createStatement()) {
                for (String parte : sql.split(";")) {
                    String instruccion = parte.trim();
                    if (instruccion.isEmpty() || instruccion.equalsIgnoreCase("BEGIN")
                            || instruccion.equalsIgnoreCase("COMMIT")) continue;
                    sentencia.execute(instruccion);
                }
                conexion.commit();
            } catch (Exception ex) {
                conexion.rollback();
                throw ex;
            }

            try (Statement verificacion = conexion.createStatement();
                 ResultSet tablas = verificacion.executeQuery("""
                         SELECT COUNT(*) FROM information_schema.tables
                          WHERE table_schema = current_schema()
                            AND table_name IN ('seguridad_contador', 'seguridad_auditoria')
                         """)) {
                tablas.next();
                if (tablas.getInt(1) != 2) throw new IllegalStateException("La migración no creó ambas tablas");
            }
            try (Statement consulta = conexion.createStatement();
                 ResultSet administradores = consulta.executeQuery(
                         "SELECT id, email FROM usuario WHERE rol = 'ADMIN' ORDER BY id")) {
                int cantidad = 0;
                while (administradores.next()) {
                    System.out.println("ADMIN\t" + administradores.getLong(1) + "\t" + administradores.getString(2));
                    cantidad++;
                }
                System.out.println("ADMIN_COUNT\t" + cantidad);
            }
        }
    }

    private static String requerido(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Falta " + nombre);
        return valor;
    }
}
