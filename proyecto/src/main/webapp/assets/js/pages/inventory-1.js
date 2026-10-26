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
    var consultaTipos = 0;
    var consultaDatos = 0;
    var tiposCargadosPara = null;

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
        var consulta = ++consultaTipos;
        tiposCargadosPara = null;
        tipoSelect.innerHTML = StayHubDOM.html('<option value="">Loading room types…</option>');
        tipoSelect.disabled = true;
        document.getElementById('btn-cargar').disabled = true;
        return Api.listarTiposHabitacion(hotelId).then(function (tipos) {
            if (consulta !== consultaTipos) return;
            tiposCargadosPara = String(hotelId);
            tipoSelect.innerHTML = StayHubDOM.html(tipos.length
                ? tipos.map(function (t) { return '<option value="' + StayHubDOM.text(t.codigo) + '">' + StayHubDOM.text(t.codigo) + ' - ' + StayHubDOM.text(t.nombre) + '</option>'; }).join("")
                : '<option value="">This hotel has no room types loaded</option>');
            tipoSelect.disabled = !tipos.length;
            document.getElementById('btn-cargar').disabled = !tipos.length;
        }).catch(function(err) { if (consulta === consultaTipos) mostrarError(errorBanner, err); });
    }

    hotelSelect.addEventListener("change", function () { ++consultaDatos; refrescarTipos(this.value); document.getElementById("disponibilidad-body").textContent=""; document.getElementById("tarifas-body").textContent=""; });

    document.getElementById("btn-cargar").addEventListener("click", function () {
        var cargaError = document.getElementById("carga-error");
        var cargaOk = document.getElementById("carga-ok");
        ocultarError(cargaError);
        cargaOk.classList.add("hidden");

        if (tiposCargadosPara !== String(hotelSelect.value)) return;
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

        var btn = this;
        btn.disabled = true;
        Api.cargarInventario(dto)
            .then(function (resp) {
                cargaOk.textContent = "Loaded: " + resp.diasCargados + " day(s) for " + resp.tipoHabitacion + ".";
                cargaOk.classList.remove("hidden");
            })
            .catch(function (err) { mostrarError(cargaError, err); })
            .finally(function() { btn.disabled = tiposCargadosPara !== String(hotelSelect.value) || !tipoSelect.value; });
    });

    document.getElementById("btn-consultar").addEventListener("click", function () {
        ocultarError(errorBanner);
        var hotelId = hotelSelect.value;
        var desde = document.getElementById("consulta-desde").value;
        var hasta = document.getElementById("consulta-hasta").value;
        if (!hotelId || !desde || !hasta) { mostrarError(errorBanner, new Error("Choose a hotel and date range.")); return; }

        var consulta = ++consultaDatos;
        document.getElementById("disponibilidad-body").textContent = "";
        document.getElementById("tarifas-body").textContent = "";
        Api.consultarDisponibilidad(hotelId, desde, hasta)
            .then(function (lista) {
                if (consulta !== consultaDatos) return;
                var body = document.getElementById("disponibilidad-body");
                body.innerHTML = StayHubDOM.html(lista.map(function (d) {
                    return '<tr class="border-b border-surface-container-high"><td class="py-xs pr-md">' + StayHubDOM.text(d.tipoHabitacion) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.desde) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.hasta) + '</td><td class="py-xs pr-md">' + StayHubDOM.text(d.unidadesDisponibles) + '</td></tr>';
                }).join(""));
                document.getElementById("disponibilidad-vacio").classList.toggle("hidden", lista.length > 0);
            })
            .catch(function (err) { mostrarError(errorBanner, err); });

        Api.consultarTarifas(hotelId, desde, hasta)
            .then(function (lista) {
                if (consulta !== consultaDatos) return;
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
