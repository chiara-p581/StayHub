(function () {
    // El token viene después del # en el link del mail: así nunca llega a los logs del servidor
    const params = new URLSearchParams(location.hash.slice(1));
    const token = params.get("token");
    history.replaceState(null, "", location.pathname); // lo saca de la barra de direcciones

    const form = document.getElementById("reset-form");
    const aviso = document.getElementById("aviso-pendiente");
    const btn = document.getElementById("submit-btn");

    if (!token) {
        aviso.textContent = "The reset token is missing. Please request a new password reset link.";
        aviso.classList.remove("hidden");
        btn.disabled = true;
    }

    form.addEventListener("submit", async function (e) {
        e.preventDefault();
        if (!token) return;
        aviso.classList.remove("hidden");
        const password = document.getElementById("password").value;
        if (password !== document.getElementById("confirm-password").value) {
            aviso.textContent = "Passwords do not match.";
            return;
        }
        btn.disabled = true;
        try {
            await Api.resetearPassword({ token: token, nuevaPassword: password });
            form.reset();
            aviso.textContent = "Your password has been updated. You can now return to sign in.";
        } catch (err) {
            aviso.textContent = err.message;
            btn.disabled = false;
        }
    });
})();
