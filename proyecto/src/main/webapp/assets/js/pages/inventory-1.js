(function () {
    var errorBanner = document.getElementById("error-banner");
    var acceso = document.getElementById("acceso-denegado");
    var panel = document.getElementById("panel");

    if (!Api.requireLogin()) return;
    var usuario = Api.currentUser();
    if (usuario.rol !== "ADMIN") {
        acceso.classList.remove("hidden");
        panel.classList.add("hidden");
        return;
    }

    var hotelSelect = document.getElementById("carga-hotel");
    var tipoSelect = document.getElementById("carga-tipo");
    var tiposPorHotel = {};

    function cargarHoteles() {
        return Api.listarHoteles(false).then(function (hoteles) {
            if (!hoteles.length) {
                mostrarError(errorBanner, new Error("No hotels have been loaded yet."));
                return;
            }
            hotelSelect.innerHTML = StayHubDOM.html(hoteles.map(function (h) {
                return '<option value="' + StayHubDOM.text(h.id) + '">' + StayHubDOM.text(h.nombre) + ' (' + StayHubDOM.text(h.ciudad) + ')</option>';
            }).join(""));
            return refrescarTipos(hoteles[0].id);
        });
    }

    function refrescarTipos(hotelId) {
        return Api.listarTiposHabitacion(hotelId).then(function (tipos) {
            tiposPorHotel[hotelId] = tipos;
            tipoSelect.innerHTML = StayHubDOM.html(tipos.length
                ? tipos.map(function (t) { return '<option value="' + StayHubDOM.text(t.codigo) + '">' + StayHubDOM.text(t.codigo) + ' - ' + StayHubDOM.text(t.nombre) + '</option>'; }).join("")
                : '<option value="">This hotel has no room types loaded</option>');
        });
    }

    hotelSelect.addEventListener("change", function () { refrescarTipos(this.value); });

    document.getElementById("btn-cargar").addEventListener("click", function () {
        var cargaError = document.getElementById("carga-error");
        var cargaOk = document.getElementById("carga-ok");
        ocultarError(cargaError);
        cargaOk.classList.add("hidden");

        var dto = {
            hotelId: Number(hotelSelect.value),
            tipoHabitacion: tipoSelect.value,
            desde: document.getElementById("carga-desde").value,
            hasta: document.getElementById("carga-hasta").value,
            unidadesTotales: Number(document.getElementById("carga-unidades").value || 0),
            precio: Number(document.getElementById("carga-precio").value || 0),
            moneda: document.getElementById("carga-moneda").value.trim() || "ARS",
        };
        if (!dto.tipoHabitacion) { mostrarError(cargaError, new Error("This hotel has no room types.")); return; }
        if (!dto.desde || !dto.hasta) { mostrarError(cargaError, new Error("Complete the date range.")); return; }

        Api.cargarInventario(dto)
            .then(function (resp) {
                cargaOk.textContent = "Loaded: " + resp.diasCargados + " day(s) for " + resp.tipoHabitacion + ".";
                cargaOk.classList.remove("hidden");
            })
            .catch(function (err) { mostrarError(cargaError, err); });
    });

    document.getElementById("btn-consultar").addEventListener("click", function () {
        ocultarError(errorBanner);
        var hotelId = hotelSelect.value;
        var desde = document.getElementById("consulta-desde").value;
        var hasta = document.getElementById("consulta-hasta").value;
        if (!hotelId || !desde || !hasta) { mostrarError(errorBanner, new Error("Choose a hotel and date range.")); return; }

        Api.consultarDisponibilidad(hotelId, desde, hasta)
            .then(function (lista) {
                var body = document.getElementById("disponibilidad-body");
                body.innerHTML = StayHubDOM.html(lista.map(function (d) {
                    return '<tr class="border-b border-surface-container-high"><td class="py-xs pr-md">' + StayHubDOM.text(d.tipoHabitacion) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.desde) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.hasta) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.unidadesDisponibles) + '</td></tr>';
                }).join(""));
                document.getElementById("disponibilidad-vacio").classList.toggle("hidden", lista.length > 0);
            })
            .catch(function (err) { mostrarError(errorBanner, err); });

        Api.consultarTarifas(hotelId, desde, hasta)
            .then(function (lista) {
                var body = document.getElementById("tarifas-body");
                body.innerHTML = StayHubDOM.html(lista.map(function (t) {
                    return '<tr class="border-b border-surface-container-high"><td class="py-xs pr-md">' + StayHubDOM.text(t.tipoHabitacion) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(t.desde) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(t.hasta) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(t.importe) + ' ' + StayHubDOM.text(t.moneda) + '</td></tr>';
                }).join(""));
                document.getElementById("tarifas-vacio").classList.toggle("hidden", lista.length > 0);
            })
            .catch(function (err) { mostrarError(errorBanner, err); });
    });

    cargarHoteles().catch(function (err) { mostrarError(errorBanner, err); });
})();
