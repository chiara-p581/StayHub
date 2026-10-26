const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const web = path.resolve(__dirname, '../src/main/webapp');
const tick = () => new Promise(resolve => setImmediate(resolve));
const user = { id: 3, rol: 'HUESPED', nombre: 'Ana', apellido: 'Prueba', email: 'ana@example.test' };
function page(name, api) {
    const dom = new JSDOM(fs.readFileSync(path.join(web, name + '.html'), 'utf8'), {
        url: 'https://stayhub.test/StayHub/' + name + '.html?hotelId=2&reservaId=13', runScripts: 'outside-only'
    });
    const w = dom.window;
    for (const file of ['vendor/purify.min.js', 'dom-seguro.js']) w.eval(fs.readFileSync(path.join(web, 'assets/js', file), 'utf8') + (file === 'dom-seguro.js' ? '\nwindow.StayHubDOM=StayHubDOM;' : ''));
    w.Api = { requireLogin: () => true, currentUser: () => user, ...api };
    w.mostrarError = (el, err) => { el.textContent = err.message; el.classList.remove('hidden'); };
    w.ocultarError = el => el.classList.add('hidden');
    w.eval(fs.readFileSync(path.join(web, 'assets/js/pages', name === 'help-faq' ? 'help-faq-events.js' : name + '-1.js'), 'utf8'));
    return dom;
}
function change(w, id, value) {
    const el = w.document.getElementById(id);
    el.value = value;
    el.dispatchEvent(new w.Event('change'));
}
async function hotel() {
    const pending = [];
    const dom = page('hotel-details', {
        consultarHotel: async () => ({ id: 2, nombre: 'Hotel', servicios: [], tiposHabitacion: [{ codigo: 'STANDARD', nombre: 'Standard', capacidadMaxima: 2 }] }),
        consultarTarifas: () => new Promise(resolve => pending.push(resolve)),
        consultarDisponibilidad: async () => [{ tipoHabitacion: 'STANDARD', unidadesDisponibles: 5 }],
        addLocalCartItem: () => { throw new Error('The cart supports at most 10 bookings.'); }
    });
    await tick();
    const w = dom.window;
    const d = new Date(); d.setDate(d.getDate() + 2);
    const iso = d.toISOString().slice(0, 10); d.setDate(d.getDate() + 2);
    change(w, 'check-in', iso); change(w, 'check-out', d.toISOString().slice(0, 10));
    return { dom, w, pending };
}
test('una tarifa atrasada no habilita reservar tras borrar las fechas', async () => {
    const { dom, w, pending } = await hotel();
    change(w, 'check-out', '');
    pending[0]([{ tipoHabitacion: 'STANDARD', importe: 100, moneda: 'ARS' }]);
    await tick();
    assert.equal(w.document.getElementById('btn-reservar').disabled, true);
    assert.equal(w.document.getElementById('precio-total').value, '');
    dom.window.close();
});
test('cambiar cantidad no permite que una respuesta anterior reemplace el precio vigente', async () => {
    const { dom, w, pending } = await hotel();
    change(w, 'cantidad-habitaciones', '2');
    pending[1]([{ tipoHabitacion: 'STANDARD', importe: 200, moneda: 'ARS' }]);
    await tick();
    pending[0]([{ tipoHabitacion: 'STANDARD', importe: 100, moneda: 'ARS' }]);
    await tick();
    assert.equal(w.document.getElementById('precio-total').value, '400.00');
    assert.equal(w.document.getElementById('btn-reservar').disabled, false);
    dom.window.close();
});
test('un carrito lleno muestra el error y permite volver a intentar', async () => {
    const { dom, w, pending } = await hotel();
    pending[0]([{ tipoHabitacion: 'STANDARD', importe: 100, moneda: 'ARS' }]);
    await tick();
    w.document.getElementById('telefono').value = '1100000000';
    w.document.getElementById('btn-reservar').click();
    assert.match(w.document.getElementById('reserva-error').textContent, /at most 10/);
    assert.equal(w.document.getElementById('btn-reservar').disabled, false);
    dom.window.close();
});
test('el detalle distingue una reserva pendiente de una confirmada', async () => {
    const dom = page('reservation-details', {
        consultarReserva: async () => ({ id: 13, hotelId: 2, estado: 'PENDIENTE', checkIn: '2099-10-20', checkOut: '2099-10-22', precioTotal: 100, moneda: 'ARS' }),
        listarHoteles: async () => []
    });
    await tick();
    assert.equal(dom.window.document.getElementById('estado').textContent, 'PENDING PAYMENT');
    assert.equal(dom.window.document.getElementById('detalle').classList.contains('hidden'), false);
    dom.window.close();
});

test('checkout conserva el carrito si el servidor rechaza y permite reintentar', async () => {
    let calls = 0, cleared = 0;
    const items = [{ reservaId: 13, hotelNombre: 'Hotel', precioTotal: 100, checkIn: '2099-10-20', checkOut: '2099-10-22' }];
    const dom = page('checkout', {
        localCart: () => items,
        procesarPagoLote: async dto => {
            calls++;
            assert.deepEqual(Array.from(dto.reservaIds), [13]);
            assert.equal(dto.monto, 100);
            assert.equal(dto.moneda, 'ARS');
            assert.ok(dto.claveIdempotencia);
            assert.equal('numero' in dto, false);
            if (calls === 1) throw new Error('Error temporal');
            return { id: 9, reservaIds: [13] };
        },
        clearLocalCart: () => cleared++
    });
    const w = dom.window;
    for (const [id, value] of Object.entries({ numero: '4111111111111111', titular: 'PRUEBA', vencimiento: '12/99', cvv: '123' })) w.document.getElementById(id).value = value;
    const submit = () => w.document.getElementById('payment-form').dispatchEvent(new w.Event('submit', { cancelable: true }));
    submit(); await tick();
    assert.equal(cleared, 0);
    assert.match(w.document.getElementById('error-banner').textContent, /Error temporal/);
    assert.equal(w.document.getElementById('pagar').disabled, false);
    const key = w.sessionStorage.getItem('stayhub_pago_13');
    submit(); await tick();
    assert.equal(calls, 2); assert.equal(cleared, 1);
    assert.ok(key); assert.equal(w.sessionStorage.getItem('stayhub_pago_13'), null);
    assert.equal(w.document.getElementById('exito').classList.contains('hidden'), false);
    assert.equal(w.document.getElementById('reserva-detalle-link').getAttribute('href'), 'reservation-details.html?reservaId=13');
    dom.window.close();
});

test('el checkout de varias reservas muestra un solo pago y enlace al historial', async () => {
    let submitted;
    const dom = page('checkout', {
        localCart: () => [{ reservaId: 14, precioTotal: 200 }, { reservaId: 13, precioTotal: 100 }],
        procesarPagoLote: async dto => { submitted = dto; return { id: 9, reservaIds: [13, 14] }; },
        clearLocalCart: () => {}
    });
    const w = dom.window;
    for (const [id, value] of Object.entries({ numero: '4111111111111111', titular: 'PRUEBA', vencimiento: '12/99', cvv: '123' })) w.document.getElementById(id).value = value;
    w.document.getElementById('payment-form').dispatchEvent(new w.Event('submit', { cancelable: true }));
    await tick();
    assert.equal(submitted.monto, 300);
    assert.deepEqual(Array.from(submitted.reservaIds), [13,14]);
    assert.equal(w.document.getElementById('reserva-detalle-link').getAttribute('href'), 'settings.html#mis-reservas');
    assert.match(w.document.getElementById('exito-texto').textContent, /2 booking/);
    dom.window.close();
});

test('ayuda filtra por texto y categoría, abre respuestas y explica el contacto', () => {
    const dom = page('help-faq', {}), w = dom.window, d = w.document;
    const search = d.getElementById('faq-search');
    search.value = 'simulated payment'; search.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelectorAll('.accordion-item:not([hidden])').length, 1);
    const result = d.querySelector('.accordion-item:not([hidden])');
    assert.equal(result.dataset.category, 'payments');
    result.querySelector('button').click();
    assert.equal(result.querySelector('button').getAttribute('aria-expanded'), 'true');
    assert.equal(result.querySelector('.accordion-content').hidden, false);
    search.value = 'inexistente'; search.dispatchEvent(new w.Event('input'));
    assert.equal(d.getElementById('faq-empty').hidden, false);
    d.querySelector('[data-faq-category="account"]').click();
    assert.equal(d.querySelectorAll('.accordion-item:not([hidden])').length, 1);
    assert.equal(d.querySelector('.accordion-item:not([hidden])').dataset.category, 'account');
    d.getElementById('faq-support').click();
    assert.equal(d.getElementById('faq-support-note').hidden, false);
    dom.window.close();
});

test('buscar hoteles y filtrar canal actualiza las tarjetas y el contador', async () => {
    const dom = page('index', {
        currentUser: () => null,
        listarHoteles: async () => [{ id: 1, nombre: 'Hotel Mar', ciudad: 'Costa', pais: 'Argentina' }, { id: 2, nombre: 'Hotel Sierra', ciudad: 'Córdoba', pais: 'Argentina' }],
        listarOfertasCanales: async () => [{ hotelId: 1, canal: 'BOOKING', puntuacion: 4 }]
    });
    await tick();
    const w = dom.window, d = w.document;
    assert.match(d.getElementById('hoteles-subtitulo').textContent, /^2 /);
    d.querySelector('[data-canal="BOOKING"]').click();
    assert.match(d.getElementById('hoteles-subtitulo').textContent, /^1 /);
    assert.match(d.getElementById('hoteles-grid').textContent, /Hotel Mar/);
    assert.doesNotMatch(d.getElementById('hoteles-grid').textContent, /Hotel Sierra/);
    d.getElementById('search-destino').value = 'inexistente';
    d.getElementById('search-destino').dispatchEvent(new w.KeyboardEvent('keyup', { key: 'Enter' }));
    assert.match(d.getElementById('hoteles-subtitulo').textContent, /^0 /);
    dom.window.close();
});

const admin = { ...user, rol: 'ADMIN' };
test('panel admin calcula solo confirmadas y mantiene monedas separadas', async () => {
    const dom = page('admin-dashboard', {
        currentUser: () => admin,
        listarHoteles: async () => [{ id: 2, nombre: 'Hotel', ciudad: 'QA' }],
        listarReservasPorHotel: async () => [
            { id: 1, estado: 'CONFIRMADA', precioTotal: 100, moneda: 'ARS', checkIn: '2099-01-01' },
            { id: 2, estado: 'PENDIENTE', precioTotal: 900, moneda: 'ARS', checkIn: '2099-01-01' },
            { id: 3, estado: 'RECHAZADA', precioTotal: 800, moneda: 'ARS', checkIn: '2099-01-01' },
            { id: 4, estado: 'CONFIRMADA', precioTotal: 50, moneda: 'USD', checkIn: '2099-01-01' }
        ]
    });
    await tick();
    const d = dom.window.document;
    assert.equal(d.getElementById('kpi-total').textContent, '4');
    assert.equal(d.getElementById('kpi-confirmadas').textContent, '2');
    assert.equal(d.getElementById('kpi-pendientes').textContent, '1');
    assert.equal(d.getElementById('kpi-ingresos').textContent, 'ARS 100.00 · USD 50.00');
    assert.equal(d.getElementById('reservas-body').rows.length, 4);
    assert.equal(d.getElementById('reservas-body').rows[0].cells.length, 7);
    dom.window.close();
});

test('inventario muestra disponibilidad y tarifas en filas y columnas reales', async () => {
    const dom = page('inventory', {
        currentUser: () => admin,
        listarHoteles: async () => [{ id: 2, nombre: 'Hotel' }],
        listarTiposHabitacion: async () => [{ codigo: 'STANDARD', nombre: 'Standard' }],
        consultarDisponibilidad: async () => [{ tipoHabitacion: 'STANDARD', desde: '2026-10-20', hasta: '2026-10-22', unidadesDisponibles: 4 }],
        consultarTarifas: async () => [{ tipoHabitacion: 'STANDARD', desde: '2026-10-20', hasta: '2026-10-22', importe: 184000, moneda: 'ARS' }]
    });
    await tick(); const w = dom.window, d = w.document;
    change(w, 'consulta-desde', '2026-10-20'); change(w, 'consulta-hasta', '2026-10-22');
    d.getElementById('btn-consultar').click(); await tick();
    assert.equal(d.getElementById('disponibilidad-body').rows[0].cells[3].textContent, '4');
    assert.equal(d.getElementById('tarifas-body').rows[0].cells[3].textContent, '184000 ARS');
    dom.window.close();
});

test('una sincronizacion aceptada se muestra en cola con el contador del contrato real', async () => {
    const dom = page('channels-sync', {
        currentUser: () => admin,
        listarHoteles: async () => [{ id: 2, nombre: 'Hotel' }],
        sincronizarOta: async () => ({ solicitudId: 'job-1', exitoso: true, elementosProcesados: 0, fecha: new Date().toISOString(), mensaje: 'Aceptada' })
    });
    await tick(); const w = dom.window, d = w.document;
    change(w, 'desde', '2026-10-20'); change(w, 'hasta', '2026-10-22');
    d.querySelector('[data-canal="BOOKING"]').click(); await tick();
    assert.match(d.getElementById('estado-BOOKING').textContent, /^Queued/);
    const row = d.getElementById('log-body').rows[0];
    assert.match(row.cells[2].textContent, /Queued/);
    assert.equal(row.cells[3].textContent, '0');
    dom.window.close();
});

test('consultar un pago inexistente oculta el resultado anterior', async () => {
    let calls = 0;
    const dom = page('payments', {
        currentUser: () => admin,
        consultarHotel: async () => ({ id: 2, nombre: 'Hotel' }),
        consultarPago: async () => { if (calls++) throw new Error('No existe el pago'); return { id: 9, reservaIds: [13,14], monto: 100, moneda: 'ARS', estado: 'APROBADO' }; }
    });
    await tick(); const d = dom.window.document;
    d.getElementById('consulta-pago-id').value = '9'; d.getElementById('btn-consultar-pago').click(); await tick();
    assert.match(d.getElementById('consulta-resultado').textContent, /#13, #14/);
    d.getElementById('consulta-pago-id').value = '999999'; d.getElementById('btn-consultar-pago').click(); await tick();
    assert.equal(d.getElementById('consulta-resultado').classList.contains('hidden'), true);
    assert.match(d.getElementById('error-banner').textContent, /No existe/);
    dom.window.close();
});

test('alta de hotel bloquea doble envio y muestra el hotel persistido', async () => {
    let hotels=[], calls=0, finish;
    const hotel = { id: 40, nombre: 'QA', ciudad: 'QA', pais: 'Argentina', activo: true };
    const dom = page('hotel-management', {
        currentUser: () => admin,
        listarHoteles: async () => hotels,
        crearHotel: () => { calls++; return new Promise(resolve => { finish = () => { hotels=[hotel]; resolve(hotel); }; }); },
        listarTiposHabitacion: async () => [], listarHabitaciones: async () => [], listarOfertasCanales: async () => []
    });
    await tick(); const w = dom.window, d = w.document;
    d.getElementById('btn-nuevo-hotel').click();
    const form=d.getElementById('hotel-form');
    const submit=()=>form.dispatchEvent(new w.Event('submit',{cancelable:true}));
    submit(); submit(); assert.equal(calls,1);
    assert.equal(form.querySelector('[type="submit"]').disabled,true);
    finish(); await tick();
    assert.match(d.getElementById('hoteles-lista').textContent,/QA/);
    assert.equal(d.getElementById('detalle-nombre').textContent,'QA');
    assert.equal(form.querySelector('[type="submit"]').disabled,false);
    dom.window.close();
});

test('cambiar hotel borra el tipo anterior y no acepta respuestas atrasadas', async () => {
    const pending = new Map();
    const dom = page('inventory', {
        currentUser: () => admin,
        listarHoteles: async () => [{ id: 1, nombre: 'Primero' }, { id: 2, nombre: 'Segundo' }],
        listarTiposHabitacion: id => new Promise(resolve => pending.set(String(id), resolve))
    });
    await tick(); const w=dom.window,d=w.document;
    change(w,'carga-hotel','2');
    assert.equal(d.getElementById('btn-cargar').disabled,true);
    assert.equal(d.getElementById('carga-tipo').value,'');
    pending.get('2')([{codigo:'QA',nombre:'QA'}]);await tick();
    pending.get('1')([{codigo:'OTRO',nombre:'Anterior'}]);await tick();
    assert.equal(d.getElementById('carga-tipo').value,'QA');
    assert.equal(d.getElementById('btn-cargar').disabled,false);
    dom.window.close();
});

test('las pantallas administrativas comparten estilos legibles en modo oscuro', () => {
    const theme = fs.readFileSync(path.join(web, 'assets/css/theme.css'), 'utf8');
    for (const selector of ['.bg-background', '.card', '.field', '.nav', '.btn-secondary', 'table tbody']) {
        assert.match(theme, new RegExp('html\\[data-theme="dark"\\][^\\n]*' + selector.replace('.', '\\.')));
    }
    for (const pageName of ['hotel-management', 'inventory', 'alerts']) {
        const html = fs.readFileSync(path.join(web, pageName + '.html'), 'utf8');
        assert.match(html, /theme\.css\?v=20261023-design2/);
    }
});
