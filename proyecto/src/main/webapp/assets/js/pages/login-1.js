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
        if (!valor || valor.indexOf("\\") !== -1 || valor.startsWith("//")) return null;
        try {
            var destino = new URL(valor, location.origin + location.pathname);
            if (destino.origin !== location.origin || !destino.pathname.endsWith(".html")) return null;
            return destino.pathname + destino.search + destino.hash;
        } catch (e) {
            return null;
        }
    }
})();
