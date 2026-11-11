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
            aviso.textContent = "If the account exists, you will receive a password reset link. Please also check your spam folder.";
        } catch (e) {
            aviso.textContent = e.message;
        } finally {
            btn.disabled = false;
        }
    });
})();
