(function () {
    var usuario = Api.currentUser();
    if (!usuario || usuario.rol !== "ADMIN") return;

    var panel = document.getElementById("admin-mfa-settings");
    var panelCodigos = document.getElementById("mfa-settings-codes");
    var lista = document.getElementById("mfa-settings-codes-list");
    var regenerar = document.getElementById("regenerate-recovery-codes");
    var reemplazar = document.getElementById("replace-authenticator");
    var copiar = document.getElementById("copy-settings-codes");
    var error = document.getElementById("error-banner");
    var ok = document.getElementById("success-banner");
    var codigos = [];

    panel.classList.remove("hidden");

    regenerar.addEventListener("click", function () {
        regenerar.disabled = true;
        Api.regenerarCodigosMfa().then(function (respuesta) {
            codigos = respuesta.codigosRecuperacion || [];
            lista.innerHTML = "";
            codigos.forEach(function (codigo) {
                var elemento = document.createElement("code");
                elemento.textContent = codigo;
                lista.appendChild(elemento);
            });
            panelCodigos.classList.remove("hidden");
            ok.textContent = "New recovery codes generated. Previous codes were revoked.";
            ok.classList.remove("hidden");
        }).catch(function (ex) {
            mostrarError(error, ex);
        }).finally(function () {
            regenerar.disabled = false;
        });
    });

    copiar.addEventListener("click", function () {
        copiarTexto(codigos.join("\n")).then(function () {
            copiar.textContent = "Copied!";
            setTimeout(function () { copiar.textContent = "Copy all codes"; }, 1600);
        }).catch(function () {
            mostrarError(error, new Error("The recovery codes could not be copied."));
        });
    });

    reemplazar.addEventListener("click", function () {
        if (!window.confirm("Replace the current authenticator? Existing authenticator and recovery codes will stop working.")) return;
        reemplazar.disabled = true;
        Api.reiniciarMfa().then(function () {
            Api.forgetLocalSession();
            location.href = "mfa-setup.html";
        }).catch(function (ex) {
            mostrarError(error, ex);
            reemplazar.disabled = false;
        });
    });

    function copiarTexto(valor) {
        if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(valor);
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
})();
