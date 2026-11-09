(function () {
    Api.forgetLocalSession();

    var secretoReal = "";
    var secretoVisible = false;
    var secreto = document.getElementById("secret");
    var mostrar = document.getElementById("toggle-secret");
    var copiar = document.getElementById("copy-secret");
    var form = document.getElementById("mfa-form");
    var error = document.getElementById("error-banner");
    var boton = document.getElementById("submit-btn");
    var panelActivacion = document.getElementById("mfa-activation-panel");
    var panelRecuperacion = document.getElementById("mfa-recovery-panel");
    var contenedorCodigos = document.getElementById("recovery-codes");
    var copiarCodigos = document.getElementById("copy-recovery-codes");
    var confirmacionCodigos = document.getElementById("recovery-saved");
    var continuar = document.getElementById("continue-dashboard");
    var usuarioActivado = null;
    var codigosRecuperacion = [];

    Api.prepararMfa().then(function (respuesta) {
        secretoReal = respuesta.secreto;
        pintarSecreto();
        mostrar.disabled = false;
        copiar.disabled = false;
    }).catch(fallo);

    mostrar.addEventListener("click", function () {
        secretoVisible = !secretoVisible;
        mostrar.textContent = secretoVisible ? "Hide" : "Show";
        mostrar.setAttribute("aria-pressed", String(secretoVisible));
        pintarSecreto();
    });

    copiar.addEventListener("click", function () {
        if (!secretoReal) return;
        copiarSecreto(secretoReal).then(function () {
            copiar.textContent = "Copied!";
            setTimeout(function () { copiar.textContent = "Copy"; }, 1600);
        }).catch(function () {
            fallo(new Error("The secret could not be copied. Please reveal it and copy it manually."));
        });
    });

    form.addEventListener("submit", function (e) {
        e.preventDefault();
        boton.disabled = true;
        Api.activarMfa(document.getElementById("codigo").value.trim()).then(function (respuesta) {
            usuarioActivado = respuesta.usuario;
            codigosRecuperacion = respuesta.codigosRecuperacion || [];
            contenedorCodigos.innerHTML = "";
            codigosRecuperacion.forEach(function (codigo) {
                var elemento = document.createElement("code");
                elemento.textContent = codigo;
                contenedorCodigos.appendChild(elemento);
            });
            panelActivacion.classList.add("hidden");
            panelRecuperacion.classList.remove("hidden");
        }).catch(function (e) {
            fallo(e);
            boton.disabled = false;
        });
    });

    copiarCodigos.addEventListener("click", function () {
        copiarSecreto(codigosRecuperacion.join("\n")).then(function () {
            copiarCodigos.textContent = "Copied!";
            setTimeout(function () { copiarCodigos.textContent = "Copy all codes"; }, 1600);
        }).catch(function () {
            fallo(new Error("The recovery codes could not be copied."));
        });
    });

    confirmacionCodigos.addEventListener("change", function () {
        continuar.disabled = !confirmacionCodigos.checked;
    });

    continuar.addEventListener("click", function () {
        if (!usuarioActivado || !confirmacionCodigos.checked) return;
        Api.setSession(usuarioActivado);
        location.href = "admin-dashboard.html";
    });

    function pintarSecreto() {
        secreto.textContent = secretoVisible ? secretoReal : "*".repeat(secretoReal.length);
    }

    function copiarSecreto(valor) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(valor);
        }
        return new Promise(function (resolve, reject) {
            var temporal = document.createElement("textarea");
            temporal.value = valor;
            temporal.setAttribute("readonly", "");
            temporal.style.position = "fixed";
            temporal.style.opacity = "0";
            document.body.appendChild(temporal);
            temporal.select();
            var copiado = document.execCommand("copy");
            temporal.remove();
            copiado ? resolve() : reject(new Error("Copy failed"));
        });
    }

    function fallo(e) {
        error.textContent = e && e.message ? e.message : "MFA setup failed";
        error.classList.remove("hidden");
    }
})();
