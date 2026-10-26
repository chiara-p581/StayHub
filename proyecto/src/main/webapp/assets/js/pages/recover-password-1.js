(function () {
    var form = document.getElementById("recover-form");
    var aviso = document.getElementById("aviso-pendiente");
    var btn = document.getElementById("submit-btn");
    form.addEventListener("submit", async function (ev) {
        ev.preventDefault();
        btn.disabled = true;
        aviso.classList.remove("hidden");
        try {
            await Api.solicitarRecuperacionPassword(document.getElementById("email").value);
            aviso.textContent = "Si existe la cuenta, recibirás un enlace de recuperación. Revisá también Spam.";
        } catch (e) {
            aviso.textContent = e.message;
        } finally {
            btn.disabled = false;
        }
    });
})();
