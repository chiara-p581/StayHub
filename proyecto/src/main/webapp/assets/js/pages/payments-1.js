(function () {
    var errorBanner = document.getElementById("error-banner");

    function pintarSesion() {
        var usuario = Api.currentUser();
        if (usuario && usuario.rol === "ADMIN") document.getElementById("brand-admin").classList.remove("hidden");
        var area = document.getElementById("session-area");
        if (usuario) {
            area.innerHTML = StayHubDOM.html('<a href="settings.html" class="font-label-md text-label-md text-on-surface-variant hover:text-primary">' + StayHubDOM.text(usuario.nombre) + '</a>');
        } else {
            area.innerHTML = StayHubDOM.html('<a href="login.html" class="bg-primary text-on-primary font-label-md text-label-md px-md py-xs rounded-full">Sign in</a>');
        }
    }
    pintarSesion();

    if (!Api.requireLogin()) return;

    var params = new URLSearchParams(location.search);
    if (params.get("hotelId")) Api.consultarHotel(params.get("hotelId")).then(function (hotel) {
        var fotos = ["stayhub-resort-hero.png", "stayhub-patagonia.png", "stayhub-costa.png"];
        document.getElementById("hotel-pago").classList.remove("hidden");
        document.getElementById("hotel-pago-nombre").textContent = hotel.nombre;
        document.getElementById("hotel-pago-ubicacion").textContent = hotel.ciudad + ", " + hotel.pais;
        document.getElementById("hotel-pago-imagen").src = "assets/images/" + fotos[Math.abs(Number(hotel.id) || 0) % fotos.length];
    }).catch(function () {});
    if (params.get("reservaId")) {
        document.getElementById("reserva-id").value = params.get("reservaId");
        document.getElementById("reserva-id").readOnly = true;
    }
    if (params.get("monto")) {
        document.getElementById("monto").value = params.get("monto");
        document.getElementById("monto").readOnly = true;
    }
    if (params.get("moneda")) {
        document.getElementById("moneda").value = params.get("moneda");
        document.getElementById("moneda").readOnly = true;
    }
    if (Api.currentUser().rol === "ADMIN") {
        document.getElementById("btn-pagar").disabled = true;
        document.getElementById("btn-pagar").textContent = "Las cuentas ADMIN no pueden realizar pagos";
        ["reserva-id", "monto", "moneda"].forEach(function (id) { document.getElementById(id).disabled = true; });
    }

    function fmtMoney(n, moneda) {
        return Number(n).toLocaleString("en-US", { minimumFractionDigits: 2 }) + " " + moneda;
    }

    function agregarTransaccion(pago) {
        document.getElementById("transacciones-vacio").classList.add("hidden");
        var lista = document.getElementById("transacciones-lista");
        var div = document.createElement("div");
        div.className = "group p-md border-b border-surface-container-highest flex items-center gap-md";
        var estadoClass = pago.estado === "APROBADO" || pago.estado === "EXITOSO"
            ? "bg-surface-container-high text-on-surface"
            : "bg-tertiary-fixed-dim text-tertiary";
        div.innerHTML =
            StayHubDOM.html('<div class="w-12 h-12 rounded-lg bg-surface-container-highest flex items-center justify-center flex-shrink-0">' +
                '<span class="material-symbols-outlined text-on-surface-variant">receipt_long</span></div>' +
            '<div class="flex-1 min-w-0">' +
                '<div class="flex justify-between items-baseline mb-xs">' +
                    '<div class="font-body-lg text-body-lg font-semibold text-on-surface truncate">Booking #' + StayHubDOM.text(pago.reservaId) + '</div>' +
                    '<div class="font-body-lg text-body-lg font-bold text-on-surface ml-sm whitespace-nowrap">' + fmtMoney(pago.monto, pago.moneda) + '</div>' +
                '</div>' +
                '<div class="flex justify-between items-center">' +
                    '<div class="font-body-md text-body-md text-on-surface-variant truncate">Payment #' + StayHubDOM.text(pago.id) + ' · ' + (pago.referenciaPasarela || "") + '</div>' +
                    '<div class="font-label-md text-label-md ' + estadoClass + ' px-2 py-0.5 rounded-full whitespace-nowrap ml-sm">' + ({PENDIENTE:'PENDING',APROBADO:'APPROVED',APROBADA:'APPROVED',RECHAZADO:'REJECTED',RECHAZADA:'REJECTED'}[pago.estado]||pago.estado) + '</div>' +
                '</div>' +
            '</div>');
        lista.prepend(div);
    }

    document.getElementById("btn-pagar").addEventListener("click", function () {
        ocultarError(document.getElementById("pago-error"));
        document.getElementById("pago-ok").classList.add("hidden");

        var reservaId = Number(document.getElementById("reserva-id").value);
        var monto = Number(document.getElementById("monto").value);
        var moneda = document.getElementById("moneda").value.trim() || "ARS";
        if (!reservaId) { mostrarError(document.getElementById("pago-error"), new Error("Enter the booking number.")); return; }
        if (!monto) { mostrarError(document.getElementById("pago-error"), new Error("Enter the amount.")); return; }

        var btn = document.getElementById("btn-pagar");
        btn.disabled = true;
        var claveIdempotencia = crypto.randomUUID ? crypto.randomUUID() :
            "pago-" + Date.now() + "-" + Math.random().toString(36).slice(2);
        Api.procesarPago({ reservaId: reservaId, monto: monto, moneda: moneda,
            claveIdempotencia: claveIdempotencia })
            .then(function (pago) {
                var ok = document.getElementById("pago-ok");
                ok.textContent = "Payment #" + pago.id + " recorded with status " + ({PENDIENTE:'PENDING',APROBADO:'APPROVED',APROBADA:'APPROVED',RECHAZADO:'REJECTED',RECHAZADA:'REJECTED'}[pago.estado]||pago.estado) + ".";
                ok.classList.remove("hidden");
                agregarTransaccion(pago);
            })
            .catch(function (err) { mostrarError(document.getElementById("pago-error"), err); })
            .finally(function () { btn.disabled = false; });
    });

    document.getElementById("btn-consultar-pago").addEventListener("click", function () {
        ocultarError(errorBanner);
        document.getElementById("consulta-resultado").classList.add("hidden");
        var id = document.getElementById("consulta-pago-id").value;
        if (!id) { mostrarError(errorBanner, new Error("Enter a payment number.")); return; }
        Api.consultarPago(id)
            .then(function (pago) {
                var resultado = document.getElementById("consulta-resultado");
                resultado.classList.remove("hidden");
                resultado.innerHTML =
                    StayHubDOM.html("Payment #" + StayHubDOM.text(pago.id) + " — bookings " + (pago.reservaIds || [pago.reservaId]).map(function(id) { return "#" + StayHubDOM.text(id); }).join(", ") + " — " + fmtMoney(pago.monto, pago.moneda) +
                    " — status " + ({PENDIENTE:'PENDING',APROBADO:'APPROVED',APROBADA:'APPROVED',RECHAZADO:'REJECTED',RECHAZADA:'REJECTED'}[pago.estado]||pago.estado) + " — " + (pago.fechaPago || ""));
            })
            .catch(function (err) { mostrarError(errorBanner, err); });
    });
})();
