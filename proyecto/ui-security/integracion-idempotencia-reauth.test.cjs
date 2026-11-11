const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

test('integración con reautenticación conserva la Idempotency-Key y el cuerpo al reintentar', async () => {
  const dom = new JSDOM('<!doctype html><html><body></body></html>', {
    url: 'https://stayhub.test/StayHub/index.html', runScripts: 'outside-only'
  });
  const w = dom.window, pagos = [];
  const errores = [];
  w.addEventListener('error', event => errores.push(event.error));
  const js = path.resolve(__dirname, '../src/main/webapp/assets/js');
  w.eval(fs.readFileSync(path.join(js, 'vendor/purify.min.js'), 'utf8'));
  w.eval(fs.readFileSync(path.join(js, 'dom-seguro.js'), 'utf8') + '\nwindow.StayHubDOM=StayHubDOM;');
  const response = (status, data) => ({ ok: status < 400, status,
    headers: { get: () => status === 403 ? 'application/problem+json' : 'application/json' }, json: async () => data,
    text: async () => JSON.stringify(data) });
  w.fetch = async (url, options) => {
    if (url.endsWith('csrf')) return response(200, { token: 'csrf-prueba' });
    if (url.endsWith('reautenticar')) return response(204, null);
    if (url.endsWith('pagos')) {
      pagos.push(options);
      return pagos.length === 1 ? response(403, { codigo: 'REAUTENTICACION_REQUERIDA' })
        : response(201, { id: 7 });
    }
    throw new Error('Petición inesperada: ' + url);
  };
  w.eval(fs.readFileSync(path.resolve(__dirname, '../src/main/webapp/assets/js/api.js'), 'utf8') + '\nwindow.testApi=Api;');
  w.testApi.setSession({ id: 2, rol: 'ADMIN' });
  const body = { reservaId: 7, monto: 100, moneda: 'ARS', claveIdempotencia: 'qa-reauth-clave-0001' };
  try {
    const pending = w.testApi.procesarPago(body);
    await new Promise(resolve => setImmediate(resolve));
    const form = w.document.getElementById('stayhub-reauth-form');
    assert.ok(form, 'Debe conservarse el diálogo de reautenticación de development');
    w.document.getElementById('stayhub-reauth-password').value = 'password-de-prueba';
    w.document.getElementById('stayhub-reauth-code').value = '123456';
    form.dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
    assert.equal((await pending).id, 7);
    assert.equal(pagos.length, 2);
    assert.equal(pagos[0].headers['Idempotency-Key'], body.claveIdempotencia);
    assert.equal(pagos[1].headers['Idempotency-Key'], body.claveIdempotencia);
    assert.equal(pagos[1].body, pagos[0].body);
    assert.deepEqual(errores, []);
  } finally { dom.window.close(); }
});
