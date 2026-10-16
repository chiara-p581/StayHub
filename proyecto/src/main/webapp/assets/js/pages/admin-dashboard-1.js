(function () {
    var errorBanner = document.getElementById("error-banner");
    var acceso = document.getElementById("acceso-denegado");
    var panel = document.getElementById("panel");

    if (!Api.requireLogin()) return;
    var usuario = Api.currentUser();
    if (usuario.rol !== "ADMIN") {
        acceso.classList.remove("hidden");
        panel.classList.add("hidden");
        document.getElementById("desktop-header").classList.add("hidden");
        pintarPerfil();
        return;
    }

    function pintarPerfil() {
        document.getElementById("perfil-admin").innerHTML =
            StayHubDOM.html('<div class="w-10 h-10 rounded-full bg-secondary-container flex items-center justify-center text-on-secondary-container font-bold">' +
            usuario.nombre.charAt(0) + usuario.apellido.charAt(0) + '</div>' +
            '<div class="flex-1 min-w-0">' +
            '<p class="text-body-md font-body-md text-on-surface font-semibold truncate">' + StayHubDOM.text(usuario.nombre) + ' ' + StayHubDOM.text(usuario.apellido) + '</p>' +
            '<p class="text-label-md font-label-md text-on-surface-variant truncate">' + StayHubDOM.text(usuario.rol) + '</p></div>' +
            '<button id="btn-logout" class="material-symbols-outlined text-on-surface-variant" title="Sign out">logout</button>');
        document.getElementById("btn-logout").addEventListener("click", function () {
            Api.clearSession().then(function () { location.href = "index.html"; }).catch(function () { alert("Could not sign out. Try again."); });

        });
    }
    pintarPerfil();

    function fmtMoney(n) {
        return "$" + Number(n).toLocaleString("en-US", { minimumFractionDigits: 2 });
    }

    function cargarHoteles() {
        return Api.listarHoteles(false).then(function (hoteles) {
            var opts = hoteles.map(function (h) {
                return '<option value="' + h.id + '">' + h.nombre + ' (' + h.ciudad + ')</option>';
            }).join("");
            document.getElementById("hotel-select").innerHTML = StayHubDOM.html(opts);
            document.getElementById("hotel-select-mobile").innerHTML = StayHubDOM.html(opts);
            return hoteles;
        });
    }

    function cargarReservas(hotelId) {
        return Api.listarReservasPorHotel(hotelId).then(pintarTodo);
    }

    function pintarTodo(reservas) {
        var confirmadas = reservas.filter(function (r) { return r.estado === "CONFIRMADA"; });
        var pendientes = reservas.filter(function (r) { return r.estado === "PENDIENTE"; });
        var noCanceladas = reservas.filter(function (r) { return r.estado !== "CANCELADA"; });
        var ingresos = noCanceladas.reduce(function (acc, r) { return acc + Number(r.precioTotal || 0); }, 0);

        document.getElementById("kpi-total").textContent = reservas.length;
        document.getElementById("kpi-confirmadas").textContent = confirmadas.length;
        document.getElementById("kpi-pendientes").textContent = pendientes.length;
        document.getElementById("kpi-ingresos").textContent = fmtMoney(ingresos);

        var body = document.getElementById("reservas-body");
        body.innerHTML = StayHubDOM.html("");
        document.getElementById("reservas-vacio").classList.toggle("hidden", reservas.length > 0);

        reservas.forEach(function (r) {
            var tr = document.createElement("tr");
            tr.className = "border-b border-surface-container-high hover:bg-surface-container-low";
            var estadoColor = r.estado === "CONFIRMADA" ? "text-secondary" : r.estado === "CANCELADA" ? "text-error" : "text-tertiary-fixed-dim";
            var acciones = "";
            if (r.estado === "PENDIENTE") {
                acciones += '<button data-id="' + r.id + '" class="btn-confirmar text-label-md font-label-md text-secondary hover:underline mr-sm">Confirm</button>';
            }
            if (r.estado !== "CANCELADA") {
                acciones += '<button data-id="' + r.id + '" class="btn-cancelar text-label-md font-label-md text-error hover:underline">Cancel</button>';
            }
            tr.innerHTML =
                StayHubDOM.html('<td class="py-sm pr-md">' + StayHubDOM.text(r.id) + '</td>' +
                '<td class="py-sm pr-md">' + StayHubDOM.text(r.huespedNombre) + ' ' + StayHubDOM.text(r.huespedApellido) + '</td>' +
                '<td class="py-sm pr-md">' + StayHubDOM.text(r.checkIn) + '</td>' +
                '<td class="py-sm pr-md">' + StayHubDOM.text(r.checkOut) + '</td>' +
                '<td class="py-sm pr-md ' + estadoColor + ' font-semibold">' + ({PENDIENTE:'PENDING',CONFIRMADA:'CONFIRMED',CANCELADA:'CANCELLED',RECHAZADA:'REJECTED'}[r.estado]||r.estado) + '</td>' +
                '<td class="py-sm pr-md">' + fmtMoney(r.precioTotal) + ' ' + StayHubDOM.text(r.moneda) + '</td>' +
                '<td class="py-sm pr-md whitespace-nowrap">' + acciones + '</td>');
            body.appendChild(tr);
        });

        body.querySelectorAll(".btn-confirmar").forEach(function (btn) {
            btn.addEventListener("click", function () {
                Api.confirmarReserva(btn.dataset.id).then(function () { return cargarReservas(hotelSeleccionado()); }).catch(function (err) { mostrarError(errorBanner, err); });
            });
        });
        body.querySelectorAll(".btn-cancelar").forEach(function (btn) {
            btn.addEventListener("click", function () {
                Api.cancelarReserva(btn.dataset.id).then(function () { return cargarReservas(hotelSeleccionado()); }).catch(function (err) { mostrarError(errorBanner, err); });
            });
        });

        // Upcoming arrivals: check-in dentro de los próximos 3 días, no canceladas
        var hoy = new Date(); hoy.setHours(0, 0, 0, 0);
        var limite = new Date(hoy); limite.setDate(limite.getDate() + 3);
        var llegadas = noCanceladas.filter(function (r) {
            var d = new Date(r.checkIn + "T00:00:00");
            return d >= hoy && d <= limite;
        }).sort(function (a, b) { return a.checkIn.localeCompare(b.checkIn); });

        var lista = document.getElementById("llegadas-lista");
        lista.innerHTML = StayHubDOM.html("");
        document.getElementById("llegadas-vacio").classList.toggle("hidden", llegadas.length > 0);
        llegadas.forEach(function (r) {
            var div = document.createElement("div");
            div.className = "p-md card-bg-subtle border-l-4 border-tertiary-fixed-dim rounded-r-lg";
            div.innerHTML =
                StayHubDOM.html('<h4 class="text-body-md font-body-md font-semibold text-on-surface">' + StayHubDOM.text(r.huespedNombre) + ' ' + StayHubDOM.text(r.huespedApellido) + '</h4>' +
                '<p class="text-label-md font-label-md text-on-surface-variant mt-1">Check-in ' + StayHubDOM.text(r.checkIn) + ' · ' + StayHubDOM.text(r.tipoHabitacion) + ' x' + StayHubDOM.text(r.cantidadHabitaciones) + '</p>');
            lista.appendChild(div);
        });
    }

    function hotelSeleccionado() {
        return document.getElementById("hotel-select").value;
    }

    function onHotelChange(valor) {
        document.getElementById("hotel-select").value = valor;
        document.getElementById("hotel-select-mobile").value = valor;
        ocultarError(errorBanner);
        cargarReservas(valor).catch(function (err) { mostrarError(errorBanner, err); });
    }

    cargarHoteles()
        .then(function (hoteles) {
            if (!hoteles.length) {
                mostrarError(errorBanner, new Error("No hotels have been loaded yet."));
                return;
            }
            document.getElementById("hotel-select").addEventListener("change", function () { onHotelChange(this.value); });
            document.getElementById("hotel-select-mobile").addEventListener("change", function () { onHotelChange(this.value); });
            onHotelChange(hoteles[0].id);
        })
        .catch(function (err) { mostrarError(errorBanner, err); });

    // Simple scroll shadow effect for top header
    var mainContent = document.querySelector("main");
    var desktopHeader = document.getElementById("desktop-header");
    if (mainContent && desktopHeader) {
        mainContent.addEventListener("scroll", function () {
            if (mainContent.scrollTop > 10) {
                desktopHeader.classList.add("shadow-ambient", "border-outline-variant");
                desktopHeader.classList.remove("border-transparent");
            } else {
                desktopHeader.classList.remove("shadow-ambient", "border-outline-variant");
                desktopHeader.classList.add("border-transparent");
            }
        });
    }
})();
