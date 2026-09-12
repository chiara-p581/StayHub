/* ============================================================
   StayHub - api.js
   Capa compartida para hablar con el backend REST (Jakarta EE / WildFly)
   desde cualquier pantalla del front. Todas las páginas cargan este
   archivo antes que su propio <script> de página.

   El login crea una sesión HTTP en el backend. El navegador conserva la
   cookie JSESSIONID y sessionStorage guarda solamente los datos públicos
   del perfil para pintar la interfaz; la contraseña nunca se almacena.
   ============================================================ */

const Api = (() => {
    const BASE = "api/";
    const USER_KEY = "stayhub_user";
    const CACHE_PREFIX = "stayhub_cache_v1:";

    function setSession(usuario) {
        if (!usuario || typeof usuario !== "object" || !usuario.id || !usuario.rol) {
            sessionStorage.removeItem(USER_KEY);
            throw new Error("El servidor no devolvió una sesión de usuario válida.");
        }
        sessionStorage.setItem(USER_KEY, JSON.stringify(usuario));
    }

    function clearSession() {
        sessionStorage.removeItem(USER_KEY);
        fetch(BASE + "usuarios/sesion", {
            method: "DELETE",
            credentials: "same-origin",
            keepalive: true,
        }).catch(function () {});
    }

    function currentUser() {
        const raw = sessionStorage.getItem(USER_KEY);
        if (!raw || raw === "undefined" || raw === "null") {
            sessionStorage.removeItem(USER_KEY);
            return null;
        }
        try {
            const usuario = JSON.parse(raw);
            if (!usuario || typeof usuario !== "object" || !usuario.id || !usuario.rol) {
                sessionStorage.removeItem(USER_KEY);
                return null;
            }
            return usuario;
        } catch (e) {
            sessionStorage.removeItem(USER_KEY);
            return null;
        }
    }

    function isLoggedIn() {
        return currentUser() !== null;
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
            if (!isLoggedIn()) {
                const err = new Error("Tenés que iniciar sesión para hacer esto.");
                err.status = 401;
                throw err;
            }
        }

        let res;
        try {
            res = await fetch(url, {
                method,
                headers,
                credentials: "same-origin",
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
            if (res.status === 401) sessionStorage.removeItem(USER_KEY);
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

    function cacheKey(path, params) {
        return CACHE_PREFIX + buildUrl(path, params);
    }

    function invalidateCache(prefix = "") {
        Object.keys(localStorage).forEach(function (key) {
            if (key.startsWith(CACHE_PREFIX + prefix)) localStorage.removeItem(key);
        });
    }

    function cachedRequest(path, params, ttlMs) {
        const key = cacheKey(path, params);
        let cached = null;
        try { cached = JSON.parse(localStorage.getItem(key)); } catch (e) { localStorage.removeItem(key); }
        if (cached && Date.now() - cached.savedAt < ttlMs) {
            request(path, { params }).then(function (fresh) {
                try { localStorage.setItem(key, JSON.stringify({ savedAt: Date.now(), data: fresh })); } catch (e) {}
            }).catch(function () {});
            return Promise.resolve(cached.data);
        }
        return request(path, { params }).then(function (fresh) {
            try { localStorage.setItem(key, JSON.stringify({ savedAt: Date.now(), data: fresh })); } catch (e) {}
            return fresh;
        });
    }

    function safeJson(text) {
        try {
            return JSON.parse(text);
        } catch (e) {
            return text;
        }
    }

    function initShell() {
        if (!document.body || document.querySelector(".stayhub-global-header")) return;
        var page = location.pathname.split("/").pop() || "index.html";
        var usuario = currentUser();
        document.body.classList.add("stayhub-unified");
        if (page === "settings.html") document.body.classList.add("stayhub-profile-page");
        var header = document.createElement("header");
        header.className = "stayhub-global-header";
        header.innerHTML = '<div class="stayhub-header-inner">' +
            '<a class="stayhub-brand" href="index.html"><span>StayHub</span>' +
            (usuario && usuario.rol === "ADMIN" ? '<span class="stayhub-admin-badge">Admin</span>' : '') + '</a>' +
            '<nav class="stayhub-header-nav" aria-label="Navegación principal">' +
            '<a class="stayhub-header-link" href="index.html"><span class="material-symbols-outlined">home</span><span class="link-label">Inicio</span></a>' +
            '<a class="stayhub-header-link" href="index.html#hoteles"><span class="material-symbols-outlined">search</span><span class="link-label">Buscar</span></a>' +
            (usuario && usuario.rol === "ADMIN" ? '<a class="stayhub-header-link" href="admin-dashboard.html"><span class="material-symbols-outlined">dashboard</span><span class="link-label">Administración</span></a>' : '') +
            '<a class="stayhub-header-link" href="cart.html"><span class="material-symbols-outlined">shopping_bag</span><span class="link-label">Carrito</span><span id="global-cart-count" class="stayhub-cart-count">0</span></a>' +
            '<a class="stayhub-header-link" href="' + (usuario ? 'settings.html' : 'login.html') + '"><span class="material-symbols-outlined">person</span><span class="link-label">' + (usuario ? usuario.nombre : 'Ingresar') + '</span></a>' +
            '</nav></div>';
        document.body.insertBefore(header, document.body.firstChild);
        if (usuario) request("carrito", { auth: true }).then(function (c) {
            var badge = document.getElementById("global-cart-count");
            if (badge) badge.textContent = c && c.hotelId ? "1" : "0";
        }).catch(function () {});
    }

    return {
        // ---- sesión ----
        setSession,
        clearSession,
        currentUser,
        isLoggedIn,
        requireLogin,
        initShell,

        // ---- usuarios ----
        registrarUsuario: (dto) => request("usuarios", { method: "POST", body: dto }),
        loginUsuario: (dto) => request("usuarios/login", { method: "POST", body: dto }),
        consultarUsuario: (id) => request(`usuarios/${id}`, { auth: true }),

        // ---- hoteles ----
        listarHoteles: (incluirInactivos = false) => cachedRequest("hoteles", { incluirInactivos }, 300000),
        consultarHotel: (id) => cachedRequest(`hoteles/${id}`, null, 300000),
        crearHotel: (dto) => request("hoteles", { method: "POST", body: dto, auth: true }).then(r => (invalidateCache("hoteles"), r)),
        modificarHotel: (id, dto) => request(`hoteles/${id}`, { method: "PUT", body: dto, auth: true }).then(r => (invalidateCache("hoteles"), r)),
        eliminarHotel: (id) => request(`hoteles/${id}`, { method: "DELETE", auth: true }).then(r => (invalidateCache("hoteles"), r)),
        listarTiposHabitacion: (hotelId, incluirInactivos = false) =>
            cachedRequest(`hoteles/${hotelId}/tipos-habitacion`, { incluirInactivos }, 300000),
        crearTipoHabitacion: (hotelId, dto) =>
            request(`hoteles/${hotelId}/tipos-habitacion`, { method: "POST", body: dto, auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/tipos-habitacion`), r)),
        modificarTipoHabitacion: (hotelId, tipoId, dto) =>
            request(`hoteles/${hotelId}/tipos-habitacion/${tipoId}`, { method: "PUT", body: dto, auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/tipos-habitacion`), r)),
        eliminarTipoHabitacion: (hotelId, tipoId) =>
            request(`hoteles/${hotelId}/tipos-habitacion/${tipoId}`, { method: "DELETE", auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/tipos-habitacion`), r)),
        listarHabitaciones: (hotelId, incluirInactivas = false) =>
            cachedRequest(`hoteles/${hotelId}/habitaciones`, { incluirInactivas }, 300000),
        crearHabitacion: (hotelId, dto) =>
            request(`hoteles/${hotelId}/habitaciones`, { method: "POST", body: dto, auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/habitaciones`), r)),
        modificarHabitacion: (hotelId, habitacionId, dto) =>
            request(`hoteles/${hotelId}/habitaciones/${habitacionId}`, { method: "PUT", body: dto, auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/habitaciones`), r)),
        eliminarHabitacion: (hotelId, habitacionId) =>
            request(`hoteles/${hotelId}/habitaciones/${habitacionId}`, { method: "DELETE", auth: true })
                .then(r => (invalidateCache(`hoteles/${hotelId}/habitaciones`), r)),

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

        // ---- datos demostrativos (solo ADMIN; persiste mediante APIs reales) ----
        cargarDatosDemo: () => request("demo/cargar", { method: "POST", auth: true }).then(r => (invalidateCache(), r)),

        // ---- inventario y tarifas ----
        consultarDisponibilidad: (hotelId, desde, hasta) =>
            cachedRequest("inventario-tarifas/disponibilidad", { hotelId, desde, hasta }, 20000),
        consultarTarifas: (hotelId, desde, hasta) =>
            cachedRequest("inventario-tarifas/tarifas", { hotelId, desde, hasta }, 60000),
        cargarInventario: (dto) =>
            request("inventario-tarifas/cargas", { method: "POST", body: dto, auth: true })
                .then(r => (invalidateCache("inventario-tarifas/"), r)),

        // ---- canales externos ----
        disponibilidadCanales: (hotelId, desde, hasta) =>
            request("canales-externos/disponibilidad", { params: { hotelId, desde, hasta }, auth: true }),
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

        // ---- operaciones ----
        enviarNotificacion: (dto) =>
            request("notificaciones/enviar", { method: "POST", body: dto, auth: true }),
        resolverOverbooking: (dto) =>
            request("overbooking/resolver", { method: "POST", body: dto, auth: true }),
    };
})();

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", Api.initShell);
else Api.initShell();

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
