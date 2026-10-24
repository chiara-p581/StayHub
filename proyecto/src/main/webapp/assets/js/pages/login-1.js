(function () {
    var form = document.getElementById("login-form");
    var errorBanner = document.getElementById("error-banner");
    var submitBtn = document.getElementById("submit-btn");

    // Si ya había una sesión activa, la mostramos como pre-cargada
    // (no auto-redirige: el usuario puede querer loguearse con otra cuenta).

    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        ocultarError(errorBanner);

        var email = document.getElementById("email").value.trim();
        var password = document.getElementById("password").value;

        submitBtn.disabled = true;
        submitBtn.textContent = "Ingresando...";

        Api.loginUsuario({ email: email, password: password })
            .then(function (usuario) {
                Api.setSession(usuario);

                var params = new URLSearchParams(location.search);
                var next = destinoLocalSeguro(params.get("next"));
                if (next) {
                    location.assign(next);
                } else if (usuario.rol === "ADMIN") {
                    location.href = "admin-dashboard.html";
                } else {
                    location.href = "index.html";
                }
            })
            .catch(function (err) {
                mostrarError(
                    errorBanner,
                    err.status === 401 || err.status === 404
                        ? new Error("Incorrect email or password.")
                        : err
                );
                submitBtn.disabled = false;
                submitBtn.textContent = "Sign in";
            });
    });

    function destinoLocalSeguro(valor) {
        var permitidos = new Set([
            "index.html", "settings.html", "cart.html", "checkout.html",
            "reservation-details.html", "admin-dashboard.html", "hotel-management.html",
            "inventory-rates.html", "channels-sync.html", "alerts.html", "payment.html"
        ]);
        if (!valor || valor.indexOf("\\") !== -1 || valor.indexOf("%") !== -1
                || valor.startsWith("/") || valor.indexOf(":") !== -1) return null;
        try {
            var destino = new URL(valor, location.origin + location.pathname);
            var archivo = destino.pathname.substring(destino.pathname.lastIndexOf("/") + 1);
            if (destino.origin !== location.origin || !permitidos.has(archivo)) return null;
            return archivo + destino.search + destino.hash;
        } catch (e) { return null; }
    }
})();
