(function () {
    var form = document.getElementById("recover-form");
    var aviso = document.getElementById("aviso-pendiente");
    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        // ServicioDeUsuarios todavía no expone un endpoint para recuperar
        // contraseña (solo registrar / login / consultar por id). Lo dejamos
        // visible y honesto en vez de simular un envío que no pasa nada.
        aviso.textContent = "This feature is not implemented in the backend yet. For now, ask an administrator to verify your account.";
        aviso.classList.remove("hidden");
        document.getElementById("submit-btn").disabled = true;
    });
})();
