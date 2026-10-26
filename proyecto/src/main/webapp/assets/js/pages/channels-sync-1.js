(function () {
    var errorBanner = document.getElementById("error-banner");
    var hotelSelect = document.getElementById("hotel-select");
    var CANALES = ["BOOKING", "EXPEDIA", "AIRBNB", "DESPEGAR"];
    var CANAL_LABEL = { BOOKING: "Booking.com", EXPEDIA: "Expedia", AIRBNB: "Airbnb", DESPEGAR: "Despegar" };

    if (!Api.requireLogin()) return;
    var usuario = Api.currentUser();
    if (usuario.rol !== "ADMIN") {
        document.querySelector("main .max-w-container-max").innerHTML =
            StayHubDOM.html('<p class="font-body-lg text-body-lg text-on-surface-variant text-center py-xxl">This section is for staff members with the ADMIN role only. <a href="index.html" class="text-primary underline">Back to home</a>.</p>');
        return;
    }

    function pintarCanales() {
        var grid = document.getElementById("canales-grid");
        grid.innerHTML = StayHubDOM.html(CANALES.map(function (canal) {
            return '<div class="bg-surface-container-lowest border-t-2 border-tertiary-fixed rounded-xl ambient-shadow p-lg flex flex-col justify-between h-full">' +
                '<div>' +
                    '<h3 class="font-title-lg text-title-lg text-primary mb-xs">' + CANAL_LABEL[canal] + '</h3>' +
                    '<p id="estado-' + canal + '" class="font-body-md text-body-md text-on-surface-variant mb-lg">Not synchronized in this session yet.</p>' +
                '</div>' +
                '<button data-canal="' + canal + '" class="btn-sync-canal w-full py-sm border border-secondary text-secondary font-label-md text-label-md rounded-lg uppercase hover:bg-secondary/5 transition-colors flex justify-center items-center gap-sm">' +
                    '<span class="material-symbols-outlined text-[16px]">refresh</span> Sincronizar' +
                '</button>' +
            '</div>';
        }).join(""));

        grid.querySelectorAll(".btn-sync-canal").forEach(function (btn) {
            btn.addEventListener("click", function () { sincronizarCanal(btn.dataset.canal, btn); });
        });
    }

    function agregarLog(destino, resultado) {
        document.getElementById("log-vacio").classList.add("hidden");
        var tbody = document.getElementById("log-body");
        var tr = document.createElement("tr");
        tr.className = "border-b border-outline-variant/10";
        var hora = new Date(resultado.fecha).toLocaleTimeString("en-US");
        var estadoTxt = resultado.solicitudId
            ? '<span class="text-on-surface-variant">Queued</span>'
            : resultado.exitoso
            ? '<span class="inline-flex items-center gap-xs text-secondary font-medium"><span class="material-symbols-outlined text-[16px]">check_circle</span>OK</span>'
            : '<span class="inline-flex items-center gap-xs text-error font-medium"><span class="material-symbols-outlined text-[16px]">cancel</span>Error</span>';
        tr.innerHTML =
            StayHubDOM.html('<td class="p-lg text-on-surface-variant whitespace-nowrap">' + hora + '</td>' +
            '<td class="p-lg font-medium text-primary">' + destino + '</td>' +
            '<td class="p-lg">' + estadoTxt + '</td>' +
            '<td class="p-lg">' + StayHubDOM.text(resultado.elementosProcesados == null ? 0 : resultado.elementosProcesados) + '</td>' +
            '<td class="p-lg text-on-surface-variant max-w-md">' + StayHubDOM.text(resultado.mensaje) + '</td>');
        tbody.prepend(tr);
    }

    function requiereRango() {
        var hotelId = hotelSelect.value;
        var desde = document.getElementById("desde").value;
        var hasta = document.getElementById("hasta").value;
        if (!hotelId || !desde || !hasta) {
            mostrarError(errorBanner, new Error("Choose a hotel and date range first."));
            return null;
        }
        ocultarError(errorBanner);
        return { hotelId: hotelId, desde: desde, hasta: hasta };
    }

    function sincronizarCanal(canal, btn) {
        var rango = requiereRango();
        if (!rango) return;
        btn.disabled = true;
        Api.sincronizarOta(canal, rango.hotelId, rango.desde, rango.hasta)
            .then(function (resultado) {
                document.getElementById("estado-" + canal).textContent =
                    (resultado.solicitudId ? "Queued · " : resultado.exitoso ? "OK · " : "Error · ") + resultado.mensaje;
                agregarLog(CANAL_LABEL[canal], resultado);
            })
            .catch(function (err) { mostrarError(errorBanner, err); })
            .finally(function () { btn.disabled = false; });
    }

    document.getElementById("btn-sync-pms").addEventListener("click", function () {
        var rango = requiereRango();
        if (!rango) return;
        Api.sincronizarPms(rango.hotelId, rango.desde, rango.hasta)
            .then(function (resultado) { agregarLog("PMS", resultado); })
            .catch(function (err) { mostrarError(errorBanner, err); });
    });

    document.getElementById("btn-ver-disponibilidad").addEventListener("click", function () {
        var rango = requiereRango();
        if (!rango) return;
        Api.disponibilidadCanales(rango.hotelId, rango.desde, rango.hasta)
            .then(function (lista) {
                lista = Array.isArray(lista) ? lista : [];
                var body = document.getElementById("disponibilidad-body");
                body.innerHTML = StayHubDOM.html(lista.map(function (d) {
                    return '<tr class="border-b border-outline-variant/10"><td class="p-lg">' + StayHubDOM.text(d.tipoHabitacion) + '</td><td class="p-lg">' + StayHubDOM.text(d.desde) + '</td><td class="p-lg">' + StayHubDOM.text(d.hasta) + '</td><td class="p-lg">' + StayHubDOM.text(d.unidadesDisponibles) + '</td></tr>';
                }).join(""));
                document.getElementById("disponibilidad-vacio").classList.toggle("hidden", lista.length > 0);
            })
            .catch(function (err) { mostrarError(errorBanner, err); });
    });

    pintarCanales();
    Api.listarHoteles(false)
        .then(function (hoteles) {
            if (!hoteles.length) { mostrarError(errorBanner, new Error("No hotels have been loaded yet.")); return; }
            hotelSelect.innerHTML = StayHubDOM.html(hoteles.map(function (h) { return '<option value="' + StayHubDOM.text(h.id) + '">' + StayHubDOM.text(h.nombre) + '</option>'; }).join(""));
        })
        .catch(function (err) { mostrarError(errorBanner, err); });
})();
