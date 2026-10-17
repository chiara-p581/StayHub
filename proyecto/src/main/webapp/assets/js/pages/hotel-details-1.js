(function () {
    var params = new URLSearchParams(location.search);
    var hotelId = params.get("hotelId");
    var canalSeleccionado = params.get("canal") || "STAYHUB";
    var loading = document.getElementById("loading");
    var contenido = document.getElementById("contenido");
    var errorBanner = document.getElementById("error-banner");
    var reservaError = document.getElementById("reserva-error");
    var reservaOk = document.getElementById("reserva-ok");
    var hotel = null;
    var tarifaValida = false;
    var consultaTarifa = 0;

    var usuarioActual = Api.currentUser();
    if (usuarioActual && usuarioActual.rol === "ADMIN") document.getElementById("brand-admin").classList.remove("hidden");

    var hoy = new Date();
    var hoyIso = new Date(hoy.getTime() - hoy.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    document.getElementById("check-in").min = hoyIso;
    document.getElementById("check-out").min = hoyIso;
    if (params.get("checkIn") >= hoyIso) document.getElementById("check-in").value = params.get("checkIn");
    if (params.get("checkOut") > (params.get("checkIn") || hoyIso)) document.getElementById("check-out").value = params.get("checkOut");

    function escapeHtml(s) {
        var d = document.createElement("div");
        d.textContent = s == null ? "" : s;
        return d.innerHTML;
    }

    if (!hotelId) {
        loading.classList.add("hidden");
        mostrarError(errorBanner, new Error("No hotel was selected. Return home and choose one."));
        errorBanner.classList.remove("hidden");
    } else {
        Api.consultarHotel(hotelId)
            .then(function (h) {
                hotel = h;
                pintarHotel(h);
                contenido.classList.remove("hidden");
            })
            .catch(function (err) { mostrarError(errorBanner, err); })
            .finally(function () { loading.classList.add("hidden"); });
    }

    function pintarHotel(h) {
        var fotos = ["stayhub-resort-hero.png", "stayhub-patagonia.png", "stayhub-costa.png"];
        document.title = "StayHub - " + h.nombre;
        document.getElementById("hotel-nombre").textContent = h.nombre;
        document.getElementById("hotel-imagen").src = "assets/images/" + fotos[Math.abs(Number(h.id) || 0) % fotos.length];
        document.getElementById("hotel-imagen").alt = "Vista de " + h.nombre;
        document.getElementById("hotel-direccion").textContent =
            h.direccion + ", " + h.ciudad + ", " + h.pais;
        document.getElementById("hotel-canal").textContent = canalSeleccionado === "STAYHUB"
            ? "Direct booking" : "Listed on " + canalSeleccionado;

        var descripcionEl = document.getElementById("hotel-descripcion");
        descripcionEl.innerHTML = StayHubDOM.html(h.descripcion
            ? "<p>" + escapeHtml(h.descripcion) + "</p>"
            : '<p class="italic">This hotel does not have a description yet.</p>');
        var serviciosTexto = h.servicios && h.servicios.length
            ? h.servicios.slice(0, 3).join(", ").toLowerCase()
            : "personalized service and spaces designed for relaxation";
        document.getElementById("hotel-experiencia").textContent =
            "The experience combines " + serviciosTexto + ". Common areas and rooms are designed to make every stage of the stay comfortable, from arrival through the final breakfast.";
        document.getElementById("hotel-entorno").textContent =
            "Located in " + h.ciudad + ", it is an ideal base for exploring the area and returning to a peaceful setting. The staff can recommend transportation, activities, and nearby options during your stay.";

        var serviciosEl = document.getElementById("hotel-servicios");
        var servicios = h.servicios && h.servicios.length ? h.servicios : [];
        serviciosEl.innerHTML = StayHubDOM.html(servicios.length
            ? servicios.map(function (s) {
                return '<div class="stayhub-service-item flex items-center gap-sm text-on-surface-variant p-md rounded-lg transition-colors">' +
                    '<span class="material-symbols-outlined text-primary">check_circle</span>' +
                    '<span class="font-body-md text-body-md">' + escapeHtml(s) + '</span></div>';
            }).join("")
            : '<p class="italic text-on-surface-variant col-span-full">This hotel does not have any amenities listed yet.</p>');

        var select = document.getElementById("tipo-habitacion");
        var tipos = h.tiposHabitacion || [];
        select.innerHTML = StayHubDOM.html(tipos.length
            ? tipos.map(function (t) {
                return '<option value="' + escapeHtml(t.codigo) + '">' + escapeHtml(t.nombre) +
                    ' (up to ' + StayHubDOM.text(t.capacidadMaxima) + ' guests)</option>';
            }).join("")
            : '<option value="">No room types have been loaded</option>');

        document.getElementById("btn-reservar").disabled = true;
        if (usuarioActual && usuarioActual.rol === "ADMIN") {
            document.getElementById("reserva-form").innerHTML = StayHubDOM.html('<div class="p-lg rounded-xl bg-surface-container-low text-on-surface-variant"><span class="material-symbols-outlined text-primary mb-sm">admin_panel_settings</span><h3 class="font-title-lg text-title-lg text-primary mb-sm">Account administrativa</h3><p>Administrators can view hotels, availability, and rates, but cannot make purchases or bookings.</p></div>');
        } else if (document.getElementById("check-in").value && document.getElementById("check-out").value) {
            calcularTarifaEstimada();
        }
    }

    function calcularTarifaEstimada() {
        var consulta = ++consultaTarifa;
        var checkIn = document.getElementById("check-in").value;
        var checkOut = document.getElementById("check-out").value;
        var tipo = document.getElementById("tipo-habitacion").value;
        var hint = document.getElementById("tarifa-hint");
        var btn = document.getElementById("btn-reservar");
        tarifaValida = false;
        btn.disabled = true;
        document.getElementById("precio-total").value = "";
        if (!checkIn || !checkOut || !tipo) {
            hint.textContent = "Choose dates to calculate the price.";
            return;
        }
        if (checkIn < hoyIso) {
            hint.textContent = "El check-in no puede estar en el pasado.";
            return;
        }
        if (checkOut <= checkIn) {
            hint.textContent = "El checkout debe ser posterior to check-in.";
            return;
        }

        hint.textContent = "Checking availability and price…";
        Promise.all([
            Api.consultarTarifas(hotelId, checkIn, checkOut),
            Api.consultarDisponibilidad(hotelId, checkIn, checkOut)
        ]).then(function (resultado) {
                if (consulta !== consultaTarifa) return;
                var tarifas = resultado[0] || [];
                var disponibilidad = resultado[1] || [];
                var delType = tarifas.filter(function (t) { return t.tipoHabitacion === tipo; });
                if (!delType.length) {
                    hint.textContent = "No rate is available for those dates. Choose another period or contact the hotel.";
                    return;
                }
                var cupo = disponibilidad.find(function (d) { return d.tipoHabitacion === tipo; });
                var cantidad = Number(document.getElementById("cantidad-habitaciones").value || 1);
                if (!cupo || Number(cupo.unidadesDisponibles) < cantidad) {
            hint.textContent = "There are not " + cantidad + " room(s) available for the entire period.";
                    return;
                }
                var total = delType.reduce(function (acc, t) { return acc + Number(t.importe); }, 0);
                total = total * cantidad;
                document.getElementById("precio-total").value = total.toFixed(2);
                document.getElementById("moneda").value = delType[0].moneda || "ARS";
                tarifaValida = true;
                btn.disabled = false;
                hint.textContent = "Available: " + cupo.unidadesDisponibles + " unit(s). Price calculated for the entire stay.";
            })
            .catch(function () {
                if (consulta !== consultaTarifa) return;
                hint.textContent = "We could not retrieve the rate. Please try again.";
            });
    }

    document.getElementById("check-in").addEventListener("change", function () {
        var salida = document.getElementById("check-out");
        if (this.value && this.value < hoyIso) {
            this.value = "";
            salida.value = "";
            mostrarError(reservaError, new Error("No se pueden seleccionar fechas anteriores a hoy."));
            calcularTarifaEstimada();
            return;
        }
        salida.min = this.value || hoyIso;
        if (salida.value && salida.value <= this.value) salida.value = "";
    });

    document.getElementById("check-out").addEventListener("change", function () {
        var entrada = document.getElementById("check-in").value;
        if (this.value && (this.value < hoyIso || (entrada && this.value <= entrada))) {
            this.value = "";
            mostrarError(reservaError, new Error("El checkout debe ser posterior to check-in y no puede estar en el pasado."));
            calcularTarifaEstimada();
        }
    });

    ["check-in", "check-out", "tipo-habitacion", "cantidad-habitaciones"].forEach(function (id) {
        document.getElementById(id).addEventListener("change", calcularTarifaEstimada);
    });

    document.getElementById("btn-reservar").addEventListener("click", function () {
        ocultarError(reservaError);
        reservaOk.classList.add("hidden");

        if (!Api.requireLogin()) return;

        var usuario = Api.currentUser();
        if (usuario && usuario.rol === "ADMIN") {
            mostrarError(reservaError, new Error("Las cuentas ADMIN no pueden realizar compras ni reservas."));
            return;
        }
        var tipoHabitacion = document.getElementById("tipo-habitacion").value;
        var checkIn = document.getElementById("check-in").value;
        var checkOut = document.getElementById("check-out").value;
        var cantidadHabitaciones = Number(document.getElementById("cantidad-habitaciones").value || 1);
        var telefono = document.getElementById("telefono").value.trim();
        var precioTotal = Number(document.getElementById("precio-total").value || 0);
        var moneda = document.getElementById("moneda").value.trim() || "ARS";

        if (!tipoHabitacion) { mostrarError(reservaError, new Error("Choose a room type.")); return; }
        if (!checkIn || !checkOut) { mostrarError(reservaError, new Error("Choose check-in and check-out dates.")); return; }
        if (!telefono) { mostrarError(reservaError, new Error("Enter a contact phone number.")); return; }
        if (checkIn < hoyIso) { mostrarError(reservaError, new Error("El check-in no puede estar en el pasado.")); return; }
        if (checkOut <= checkIn) { mostrarError(reservaError, new Error("El checkout debe ser posterior to check-in.")); return; }
        if (!tarifaValida || !precioTotal) { mostrarError(reservaError, new Error("No valid rate is available for this selection.")); return; }

        var btn = document.getElementById("btn-reservar");
        btn.disabled = true;
        btn.textContent = "Agregando...";

        try {
            Api.addLocalCartItem({
                hotelId: Number(hotelId), hotelNombre: hotel.nombre, tipoHabitacion: tipoHabitacion,
                canal: canalSeleccionado,
                cantidadHabitaciones: cantidadHabitaciones, checkIn: checkIn, checkOut: checkOut,
                huespedNombre: usuario.nombre, huespedApellido: usuario.apellido, huespedEmail: usuario.email,
                huespedTelefono: telefono, precioTotal: precioTotal, moneda: moneda
            });
            location.href = "cart.html";
        } catch (err) {
            mostrarError(reservaError, err);
            btn.disabled = false;
            btn.textContent = "Add to cart";
        }
    });
})();
