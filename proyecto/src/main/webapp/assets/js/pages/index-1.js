(function () {
    var errorBanner = document.getElementById("error-banner");
    var sessionArea = document.getElementById("session-area");
    var loading = document.getElementById("hoteles-loading");
    var grid = document.getElementById("hoteles-grid");
    var subtitulo = document.getElementById("hoteles-subtitulo");
    var todosLosHoteles = [];
    var ofertas = [];
    var canalActivo = new URLSearchParams(location.search).get("canal") || "TODOS";

    function pintarSesion() {
        var usuario = Api.currentUser();
        if (usuario) {
            document.getElementById("brand-admin").classList.toggle("hidden", usuario.rol !== "ADMIN");
            document.getElementById("nav-admin").classList.toggle("hidden", usuario.rol !== "ADMIN");
            sessionArea.innerHTML =
                StayHubDOM.html('<a href="settings.html" class="font-label-md text-label-md text-on-surface-variant hover:text-primary transition-colors">' + StayHubDOM.text(usuario.nombre) + '</a>' +
                '<button id="btn-logout" class="w-8 h-8 rounded-full bg-secondary-container flex items-center justify-center text-on-secondary-container" title="Sign out"><span class="material-symbols-outlined" style="font-size:18px;">logout</span></button>');
            document.getElementById("btn-logout").addEventListener("click", function () {
                Api.clearSession().then(function () { location.href = "index.html"; }).catch(function () { alert("Could not sign out. Try again."); });

            });
        } else {
            sessionArea.innerHTML =
                StayHubDOM.html('<a href="login.html" class="bg-primary text-on-primary font-label-md text-label-md px-md py-xs rounded-full hover:bg-primary-container transition-colors">Sign in</a>');
        }
    }

    function tarjetaHotel(hotel) {
        var fotos = ["stayhub-resort-hero.png", "stayhub-patagonia.png", "stayhub-costa.png"];
        var foto = fotos[Math.abs(Number(hotel.id) || 0) % fotos.length];
        var fechas = '&checkIn=' + encodeURIComponent(document.getElementById('search-checkin').value || '') + '&checkOut=' + encodeURIComponent(document.getElementById('search-checkout').value || '') + '&huespedes=' + encodeURIComponent(document.getElementById('search-guests').value || '2');
        var div = document.createElement("div");
        div.className = "group cursor-pointer rounded-xl overflow-hidden relative h-72 hero-shadow bg-surface-container-lowest border border-outline-variant/20 flex flex-col";
        var fuentes = ofertas.filter(function (o) { return o.hotelId === hotel.id; });
        var badges = fuentes.map(function (o) { return '<span class="px-2 py-1 rounded-full bg-surface-container-high text-[11px] font-semibold">' + escapeHtml(o.canal === 'STAYHUB' ? 'Directo' : o.canal) + ' · ' + Number(o.puntuacion).toFixed(1) + '</span>'; }).join('');
        div.innerHTML =
            StayHubDOM.html('<div class="h-40 bg-primary-container overflow-hidden"><img src="assets/images/' + foto + '" alt="' + escapeHtml(hotel.nombre) + '" class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"/></div>' +
            '<div class="p-lg flex-1 flex flex-col justify-between">' +
                '<div>' +
                    '<h3 class="font-headline-md text-headline-md text-on-surface mb-xs">' + escapeHtml(hotel.nombre) + '</h3>' +
                    '<p class="font-body-md text-body-md text-on-surface-variant flex items-center gap-xs">' +
                        '<span class="material-symbols-outlined" style="font-size:16px;">location_on</span>' +
                        escapeHtml(hotel.ciudad) + ', ' + escapeHtml(hotel.pais) +
                    '</p>' +
                    '<div class="flex flex-wrap gap-xs mt-sm">' + badges + '</div>' +
                '</div>' +
                '<a href="hotel-details.html?hotelId=' + StayHubDOM.text(hotel.id) + '&canal=' + (canalActivo === 'TODOS' ? 'STAYHUB' : canalActivo) + fechas + '" class="mt-md inline-flex items-center gap-xs font-label-md text-label-md text-primary uppercase tracking-wider hover:text-primary-container transition-colors">' +
                    'Ver hotel <span class="material-symbols-outlined" style="font-size:18px;">arrow_forward</span>' +
                '</a>' +
            '</div>');
        return div;
    }

    function escapeHtml(s) {
        var d = document.createElement("div");
        d.textContent = s == null ? "" : s;
        return d.innerHTML;
    }

    function pintarGrid(lista) {
        grid.innerHTML = StayHubDOM.html("");
        if (!lista.length) {
            grid.innerHTML = StayHubDOM.html('<p class="font-body-lg text-body-lg text-on-surface-variant col-span-full">No encontramos hoteles con ese criterio.</p>');
            return;
        }
        lista.forEach(function (h) { grid.appendChild(tarjetaHotel(h)); });
    }

    function buscar() {
        var texto = (document.getElementById("search-destino").value || "").trim().toLowerCase();
        var filtrados = todosLosHoteles.filter(function (h) {
            var coincideTexto = !texto || (h.nombre + " " + h.ciudad + " " + h.pais).toLowerCase().indexOf(texto) !== -1;
            var coincideCanal = canalActivo === "TODOS" || ofertas.some(function (o) { return o.hotelId === h.id && o.canal === canalActivo; });
            return coincideTexto && coincideCanal;
        });
        subtitulo.textContent = filtrados.length + " propiedad" + (filtrados.length === 1 ? "" : "es") + " disponible" + (filtrados.length === 1 ? "" : "s") + ".";
        pintarGrid(filtrados);
    }

    document.querySelectorAll(".canal-chip").forEach(function (btn) {
        btn.addEventListener("click", function () {
            canalActivo = btn.dataset.canal;
            document.querySelectorAll(".canal-chip").forEach(function (b) { b.classList.remove("bg-primary", "text-on-primary"); });
            btn.classList.add("bg-primary", "text-on-primary");
            buscar();
        });
        if (btn.dataset.canal === canalActivo) {
            document.querySelectorAll(".canal-chip").forEach(function (b) { b.classList.remove("bg-primary", "text-on-primary"); });
            btn.classList.add("bg-primary", "text-on-primary");
        }
    });

    document.getElementById("btn-buscar").addEventListener("click", function (ev) {
        ev.preventDefault();
        buscar();
        document.getElementById("hoteles").scrollIntoView({ behavior: "smooth" });
    });
    document.getElementById("search-destino").addEventListener("keyup", function (ev) {
        if (ev.key === "Enter") buscar();
    });

    pintarSesion();
    var hoyBusqueda = new Date();
    var hoyBusquedaIso = new Date(hoyBusqueda.getTime() - hoyBusqueda.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    document.getElementById("search-checkin").min = hoyBusquedaIso;
    document.getElementById("search-checkout").min = hoyBusquedaIso;
    ocultarError(errorBanner);
    Promise.all([Api.listarHoteles(false), Api.listarOfertasCanales().catch(function () { return []; })])
        .then(function (resultado) {
            var hoteles = resultado[0] || [];
            ofertas = resultado[1] || [];
            todosLosHoteles = hoteles;
            buscar();
        })
        .catch(function (err) { mostrarError(errorBanner, err); })
        .finally(function () { loading.classList.add("hidden"); });
})();
