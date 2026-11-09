(function () {
    var form = document.getElementById("register-form");
    var errorBanner = document.getElementById("error-banner");
    var submitBtn = document.getElementById("submit-btn");

    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        ocultarError(errorBanner);

        var nombre = document.getElementById("nombre").value.trim();
        var apellido = document.getElementById("apellido").value.trim();
        var email = document.getElementById("email").value.trim();
        var password = document.getElementById("password").value;
        var confirmPassword = document.getElementById("confirmPassword").value;
        if (password !== confirmPassword) {
            mostrarError(errorBanner, new Error("Passwords do not match."));
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
                mostrarError(errorBanner, err);
                submitBtn.disabled = false;
                submitBtn.textContent = "Create Account";
            });
    });
})();
