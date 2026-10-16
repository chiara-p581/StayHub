(function () {
    if (!Api.requireLogin()) return;
    var usuario = Api.currentUser();
    if (usuario.rol !== "ADMIN") {
        document.getElementById("acceso-denegado").classList.remove("hidden");
        document.getElementById("panel").classList.add("hidden");
        return;
    }
    document.getElementById("perfil-admin").innerHTML =
        StayHubDOM.html('<div class="w-10 h-10 rounded-full bg-secondary-container flex items-center justify-center text-on-secondary-container font-bold">' +
        usuario.nombre.charAt(0) + usuario.apellido.charAt(0) + '</div>' +
        '<div><div class="font-title-lg text-body-lg text-primary">' + StayHubDOM.text(usuario.nombre) + ' ' + StayHubDOM.text(usuario.apellido) + '</div>' +
        '<div class="font-label-md text-label-md text-on-surface-variant">' + StayHubDOM.text(usuario.rol) + '</div></div>');
})();
