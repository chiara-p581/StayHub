/* ============================================================
   StayHub - api.js
   Capa compartida para hablar con el backend REST (Jakarta EE / WildFly)
   desde cualquier pantalla del front. Todas las páginas cargan este
   archivo antes que su propio <script> de página.

   Cómo maneja el login: el back tiene DOS sistemas de identidad hoy:
     1) El "usuario de negocio" (POST /api/usuarios y /api/usuarios/login,
        vive en la base de StayHub) — sirve para saber el nombre/rol
        de quien está usando la app.
     2) El usuario de seguridad de WildFly (ApplicationRealm, creado con
        add-user.sh) — es el que protege con HTTP Basic las operaciones
        sensibles (cancelar una reserva), vía @RolesAllowed/web.xml.
   login.html pide un solo email+contraseña y los usa para las dos cosas:
   valida contra /api/usuarios/login (para mostrar nombre/rol en la UI)
   y guarda esas mismas credenciales como header "Authorization: Basic"
   para las llamadas que WildFly protege. Para que ambas coincidan, el
   usuario de WildFly tiene que existir con el mismo email/contraseña.
   ============================================================ */

const Api = (() => {
    const BASE = "api/";
    const AUTH_KEY = "stayhub_auth";
    const USER_KEY = "stayhub_user";

    function authHeaderValue() {
        const raw = sessionStorage.getItem(AUTH_KEY);
        return raw ? "Basic " + raw : null;
    }

    function setSession(email, password, usuario) {
        sessionStorage.setItem(AUTH_KEY, btoa(email + ":" + password));
        sessionStorage.setItem(USER_KEY, JSON.stringify(usuario));
    }

    function clearSession() {
        sessionStorage.removeItem(AUTH_KEY);
        sessionStorage.removeItem(USER_KEY);
    }

    function currentUser() {
        const raw = sessionStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    }

    function isLoggedIn() {
        return !!sessionStorage.getItem(AUTH_KEY);
    }

    /**
     * Redirige a login.html si no hay sesión, guardando la página actual
     * para volver después de loguearse. Se llama al principio de las
     * páginas que necesitan un usuario logueado.
     */
    function requireLogin() {
        if (!isLoggedIn()) {
            const volver = encodeURIComponent(location.pathname.split("/").pop() + location.search);
            location.href = "login.html?next=" + volver;
            return false;
        }
        return true;
    }

    function buildUrl(path, params) {
        let url = BASE + path;
        if (params) {
            const entries = Object.entries(params).filter(
                ([, v]) => v !== undefined && v !== null && v !== ""
            );
            if (entries.length) {
                url += "?" + new URLSearchParams(entries).toString();
            }
        }
        return url;
    }

    async function request(path, { method = "GET", body, auth = false, params } = {}) {
        const url = buildUrl(path, params);
        const headers = { Accept: "application/json" };
        if (body !== undefined) headers["Content-Type"] = "application/json";
        if (auth) {
            const authValue = authHeaderValue();
            if (!authValue) {
                const err = new Error("Tenés que iniciar sesión para hacer esto.");
                err.status = 401;
                throw err;
            }
            headers["Authorization"] = authValue;
        }

        let res;
        try {
            res = await fetch(url, {
                method,
                headers,
                body: body !== undefined ? JSON.stringify(body) : undefined,
            });
        } catch (networkErr) {
            const err = new Error(
                "No se pudo conectar con el servidor. ¿WildFly está corriendo en localhost:8080?"
            );
            err.cause = networkErr;
            throw err;
        }

        if (res.status === 204) return null;

        const contentType = res.headers.get("content-type") || "";
        const isJson = contentType.includes("application/json");
        const raw = await res.text();
        const data = raw ? (isJson ? safeJson(raw) : raw) : null;

        if (!res.ok) {
            const msg =
                (data && (data.mensaje || data.message || data.error)) ||
                (typeof data === "string" && data) ||
                `Error ${res.status}`;
            const err = new Error(msg);
            err.status = res.status;
            err.body = data;
            throw err;
        }
        return data;
    }

    function safeJson(text) {
        try {
            return JSON.parse(text);
        } catch (e) {
            return text;
        }
    }

    return {
        // ---- sesión ----
        setSession,
        clearSession,
        currentUser,
        isLoggedIn,
        requireLogin,

        // ---- usuarios ----
        registrarUsuario: (dto) => request("usuarios", { method: "POST", body: dto }),
        loginUsuario: (dto) => request("usuarios/login", { method: "POST", body: dto }),
        consultarUsuario: (id) => request(`usuarios/${id}`, { auth: true }),

        // ---- hoteles ----
        listarHoteles: (incluirInactivos = false) =>
            request("hoteles", { params: { incluirInactivos } }),
        consultarHotel: (id) => request(`hoteles/${id}`),
        listarTiposHabitacion: (hotelId) => request(`hoteles/${hotelId}/tipos-habitacion`),

        // ---- carrito de reserva (EJB @Stateful, sesión HTTP) ----
        carritoSeleccionarHotel: (dto) =>
            request("carrito/hotel", { method: "POST", body: dto, auth: true }),
        carritoSeleccionarFechas: (dto) =>
            request("carrito/fechas", { method: "POST", body: dto, auth: true }),
        carritoSeleccionarHuesped: (dto) =>
            request("carrito/huesped", { method: "POST", body: dto, auth: true }),
        carritoResumen: () => request("carrito", { auth: true }),
        carritoConfirmar: () => request("carrito/confirmar", { method: "POST", auth: true }),
        carritoVaciar: () => request("carrito", { method: "DELETE", auth: true }),

        // ---- reservas ----
        crearReserva: (dto) => request("reservas", { method: "POST", body: dto, auth: true }),
        consultarReserva: (id) => request(`reservas/${id}`, { auth: true }),
        confirmarReserva: (id) =>
            request(`reservas/${id}/confirmacion`, { method: "POST", auth: true }),
        cancelarReserva: (id) => request(`reservas/${id}`, { method: "DELETE", auth: true }),
        listarReservasPorHotel: (hotelId) =>
            request("reservas", { params: { hotelId }, auth: true }),

        // ---- inventario y tarifas ----
        consultarDisponibilidad: (hotelId, desde, hasta) =>
            request("inventario-tarifas/disponibilidad", { params: { hotelId, desde, hasta } }),
        consultarTarifas: (hotelId, desde, hasta) =>
            request("inventario-tarifas/tarifas", { params: { hotelId, desde, hasta } }),
        cargarInventario: (dto) =>
            request("inventario-tarifas/cargas", { method: "POST", body: dto, auth: true }),

        // ---- canales externos ----
        disponibilidadCanales: (hotelId, desde, hasta) =>
            request("canales-externos/disponibilidad", { params: { hotelId, desde, hasta } }),
        sincronizarOta: (canal, hotelId, desde, hasta) =>
            request(`canales-externos/otas/${canal}/sincronizaciones`, {
                method: "POST",
                params: { hotelId, desde, hasta },
                auth: true,
            }),
        sincronizarPms: (hotelId, desde, hasta) =>
            request("canales-externos/pms/sincronizaciones", {
                method: "POST",
                params: { hotelId, desde, hasta },
                auth: true,
            }),

        // ---- pagos ----
        procesarPago: (dto) => request("pagos", { method: "POST", body: dto, auth: true }),
        consultarPago: (id) => request(`pagos/${id}`, { auth: true }),
    };
})();

/**
 * Helper genérico para mostrar errores de la API en un elemento de la
 * página (una notificación / banner). Todas las pantallas lo usan igual.
 */
function mostrarError(el, err) {
    if (!el) return;
    el.textContent = err && err.message ? err.message : "Ocurrió un error inesperado.";
    el.classList.remove("hidden");
}

function ocultarError(el) {
    if (!el) return;
    el.classList.add("hidden");
    el.textContent = "";
}
