(function () {
    if (!Api.requireLogin()) return;
    if (Api.currentUser().rol !== "ADMIN") { location.replace("index.html"); return; }
    var page = 0, size = 10, total = 0, hotels = {}, selected = null;
    var error = document.getElementById("error-banner"), dialog = document.getElementById("booking-dialog");
    function money(n, currency) { return new Intl.NumberFormat("en-US", { style: "currency", currency: currency || "ARS" }).format(Number(n) || 0); }
    function statusText(s) { return ({PENDIENTE:"PENDING",CONFIRMADA:"CONFIRMED",MODIFICADA:"MODIFIED",CANCELADA:"CANCELLED",RECHAZADA:"REJECTED"}[s] || s); }
    function statusClass(s) { return ({PENDIENTE:"pending",CONFIRMADA:"confirmed",MODIFICADA:"modified",CANCELADA:"cancelled",RECHAZADA:"rejected"}[s] || "pending"); }
    function statusPill(s) { return '<span class="status-pill status-' + statusClass(s) + '">' + statusText(s) + '</span>'; }
    function canCancel(r) { return r.estado !== "CANCELADA" && new Date(r.checkOut + "T00:00:00") >= new Date(new Date().toDateString()); }
    function filters() {
        return { id: document.getElementById("f-id").value, huesped: document.getElementById("f-guest").value.trim(), hotelId: document.getElementById("f-hotel").value, estado: document.getElementById("f-status").value, montoMin: document.getElementById("f-min").value, montoMax: document.getElementById("f-max").value, offset: page * size, limite: size };
    }
    function load() {
        ocultarError(error);
        return Api.buscarReservasAdmin(filters()).then(function (result) {
            total = result.total; var body = document.getElementById("bookings-body"); body.innerHTML = StayHubDOM.html("");
            result.items.forEach(function (r) {
                var tr = document.createElement("tr"); tr.className = "admin-result-row border-t border-outline-variant/20";
                tr.innerHTML = StayHubDOM.html('<td class="p-md font-semibold">#'+r.id+'</td><td class="p-md"><b>'+StayHubDOM.text(r.huespedNombre+' '+r.huespedApellido)+'</b><span class="block text-xs text-on-surface-variant">'+StayHubDOM.text(r.huespedEmail)+'</span></td><td class="p-md">'+StayHubDOM.text(hotels[r.hotelId] || ('Hotel #'+r.hotelId))+'</td><td class="p-md">'+StayHubDOM.text(r.checkIn)+' → '+StayHubDOM.text(r.checkOut)+'</td><td class="p-md">'+statusPill(r.estado)+'</td><td class="p-md text-right font-semibold">'+money(r.precioTotal,r.moneda)+'</td><td class="p-md text-right"><button data-id="'+r.id+'" class="view text-primary underline">View details</button></td>');
                body.appendChild(tr);
            });
            body.querySelectorAll(".view").forEach(function (b) { b.onclick = function () { openDetail(result.items.find(function (r) { return String(r.id) === b.dataset.id; })); }; });
            document.getElementById("empty").classList.toggle("hidden", result.items.length > 0);
            document.getElementById("result-count").textContent = total + " booking" + (total === 1 ? "" : "s");
            document.getElementById("page-label").textContent = total ? "Page " + (page + 1) + " of " + Math.ceil(total / size) : "Page 0 of 0";
            document.getElementById("prev").disabled = page === 0; document.getElementById("next").disabled = (page + 1) * size >= total;
        }).catch(function (e) { mostrarError(error, e); });
    }
    function openDetail(r) {
        selected = r; document.getElementById("booking-confirmation").classList.add("hidden"); document.getElementById("detail-title").textContent = "Booking #" + r.id;
        document.getElementById("detail-content").innerHTML = StayHubDOM.html('<dl class="grid sm:grid-cols-2 gap-md"><div><dt class="text-xs uppercase text-on-surface-variant">Guest</dt><dd class="font-semibold">'+StayHubDOM.text(r.huespedNombre+' '+r.huespedApellido)+'</dd><dd class="text-sm">'+StayHubDOM.text(r.huespedEmail)+'</dd></div><div><dt class="text-xs uppercase text-on-surface-variant">Hotel</dt><dd class="font-semibold">'+StayHubDOM.text(hotels[r.hotelId] || ('Hotel #'+r.hotelId))+'</dd><dd class="text-sm">'+StayHubDOM.text(r.tipoHabitacion)+' · '+r.cantidadHabitaciones+' room(s)</dd></div><div><dt class="text-xs uppercase text-on-surface-variant">Stay</dt><dd>'+r.checkIn+' → '+r.checkOut+'</dd></div><div><dt class="text-xs uppercase text-on-surface-variant">Status</dt><dd>'+statusPill(r.estado)+'</dd></div><div><dt class="text-xs uppercase text-on-surface-variant">Channel</dt><dd>'+StayHubDOM.text(r.canal)+'</dd></div><div><dt class="text-xs uppercase text-on-surface-variant">Amount</dt><dd class="font-semibold">'+money(r.precioTotal,r.moneda)+'</dd></div></dl>');
        var actions = document.getElementById("detail-actions"), actionMarkup = "";
        if (r.estado === "PENDIENTE") actionMarkup += '<button id="confirm-booking" class="bg-primary text-on-primary rounded-full px-lg py-sm">Confirm booking</button>';
        if (canCancel(r)) actionMarkup += '<button id="cancel-booking" class="border border-error text-error rounded-full px-lg py-sm">Cancel booking</button>';
        else if (r.estado !== "CANCELADA") actionMarkup += '<p class="text-sm text-on-surface-variant">Completed stays cannot be cancelled.</p>';
        actions.innerHTML = StayHubDOM.html(actionMarkup);
        if (document.getElementById("confirm-booking")) document.getElementById("confirm-booking").onclick = function () { Api.confirmarReserva(r.id).then(afterAction).catch(function(e){mostrarError(error,e);}); };
        if (document.getElementById("cancel-booking")) document.getElementById("cancel-booking").onclick = function () { document.getElementById("booking-confirmation").classList.remove("hidden"); };
        dialog.showModal();
    }
    function afterAction() { dialog.close(); return load(); }
    Api.listarHoteles(false).then(function (list) { var select = document.getElementById("f-hotel"); list.forEach(function(h){hotels[h.id]=h.nombre; var o=document.createElement("option");o.value=h.id;o.textContent=h.nombre;select.appendChild(o);}); var preset=new URLSearchParams(location.search).get("bookingId"); if(preset)document.getElementById("f-id").value=preset; return load(); }).catch(function(e){mostrarError(error,e);});
    document.getElementById("booking-filters").onsubmit = function(e){e.preventDefault();page=0;void load();};
    document.getElementById("clear-filters").onclick=function(){document.getElementById("booking-filters").reset();page=0;void load();};
    document.getElementById("prev").onclick=function(){if(page>0){page--;void load();}}; document.getElementById("next").onclick=function(){if((page+1)*size<total){page++;void load();}};
    document.getElementById("close-detail").onclick=function(){dialog.close();};
    document.getElementById("booking-cancel-dismiss").onclick=function(){document.getElementById("booking-confirmation").classList.add("hidden");};
    document.getElementById("booking-cancel-approve").onclick=function(){if(!selected)return;Api.cancelarReserva(selected.id).then(afterAction).catch(function(e){mostrarError(error,e);});};
})();
