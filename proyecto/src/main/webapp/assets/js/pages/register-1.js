(function () {
    var form = document.getElementById("register-form");
    var errorBanner = document.getElementById("error-banner");
    var submitBtn = document.getElementById("submit-btn");

    function mostrarErrorRegistro(err) {
        var duplicado = err && err.body && err.body.codigo === "EMAIL_YA_REGISTRADO";
        var contenido = document.createElement("div");
        contenido.className = "flex items-start gap-sm";
        var icono = document.createElement("span");
        icono.className = "material-symbols-outlined";
        icono.setAttribute("aria-hidden", "true");
        icono.textContent = "info";
        var texto = document.createElement("div");
        var titulo = document.createElement("p");
        titulo.className = "font-bold mb-xs";
        titulo.textContent = duplicado ? "Este correo ya está registrado" : "No pudimos crear tu cuenta";
        var detalle = document.createElement("p");
        detalle.textContent = duplicado ? "Podés iniciar sesión con tu cuenta o usar otro correo." :
            (err && err.message ? err.message : "Intentá nuevamente en unos minutos.");
        texto.append(titulo, detalle);
        if (duplicado) {
            var enlace = document.createElement("a");
            enlace.href = "login.html";
            enlace.className = "inline-block mt-sm font-bold underline";
            enlace.textContent = "Iniciar sesión";
            texto.append(enlace);
            document.getElementById("email").setAttribute("aria-invalid", "true");
        }
        contenido.append(icono, texto);
        errorBanner.replaceChildren(contenido);
        errorBanner.classList.remove("hidden");
        errorBanner.focus();
    }

    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        ocultarError(errorBanner);
        document.getElementById("email").removeAttribute("aria-invalid");

        var nombre = document.getElementById("nombre").value.trim();
        var apellido = document.getElementById("apellido").value.trim();
        var email = document.getElementById("email").value.trim();
        var password = document.getElementById("password").value;
        var confirmPassword = document.getElementById("confirmPassword").value;
        if (password !== confirmPassword) {
            mostrarErrorRegistro(new Error("Las contraseñas no coinciden."));
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = "Creating account...";

        Api.registrarUsuario({ email: email, password: password, nombre: nombre, apellido: apellido })
            .then(function () {
                // Auto-login: ya tenemos el email/contraseña recién creados.
                return Api.loginUsuario({ email: email, password: password });
            })
            .then(function (usuario) {
                Api.setSession(usuario);
                submitBtn.textContent = "Account created!";
                setTimeout(function () {
                    location.href = "index.html";
                }, 800);
            })
            .catch(function (err) {
                mostrarErrorRegistro(err);
                submitBtn.disabled = false;
                submitBtn.textContent = "Create Account";
            });
    });
})();
