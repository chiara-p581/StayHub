const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

test('registro presenta el conflicto problem+json como un aviso accesible sin diagnóstico técnico', async () => {
  const root = path.resolve(__dirname, '../src/main/webapp');
  const dom = new JSDOM(fs.readFileSync(path.join(root, 'register.html'), 'utf8'), {
    url: 'https://stayhub.test/StayHub/register.html', runScripts: 'outside-only'
  });
  const w = dom.window;
  w.fetch = async url => {
    const csrf = url.endsWith('csrf');
    return { ok: csrf, status: csrf ? 200 : 409,
      headers: { get: () => csrf ? 'application/json' : 'application/problem+json;charset=UTF-8' },
      json: async () => ({ token: 'qa-csrf' }),
      text: async () => JSON.stringify(csrf ? { token: 'qa-csrf' } : {
        codigo: 'EMAIL_YA_REGISTRADO', mensaje: 'Ya existe un usuario con ese email',
        correlationId: 'diagnostico-interno-qa', status: 409
      }) };
  };
  w.eval(fs.readFileSync(path.join(root, 'assets/js/api.js'), 'utf8') + '\nwindow.Api=Api;');
  w.eval(fs.readFileSync(path.join(root, 'assets/js/pages/register-1.js'), 'utf8'));
  try {
    for (const [id, value] of Object.entries({ nombre: 'QA', apellido: 'Registro', email: 'qa@stayhub.invalid', password: 'Prueba.2026!', confirmPassword: 'Prueba.2026!' })) {
      w.document.getElementById(id).value = value;
    }
    w.document.getElementById('register-form').dispatchEvent(new w.Event('submit', { cancelable: true }));
    await new Promise(resolve => setImmediate(resolve));
    const banner = w.document.getElementById('error-banner');
    assert.match(banner.textContent, /Este correo ya está registrado/);
    assert.doesNotMatch(banner.textContent, /correlationId|diagnostico-interno|"status"|EMAIL_YA_REGISTRADO/);
    assert.equal(banner.querySelector('a').getAttribute('href'), 'login.html');
    assert.equal(banner.getAttribute('role'), 'alert');
    assert.equal(w.document.activeElement, banner);
    assert.equal(w.document.getElementById('email').getAttribute('aria-invalid'), 'true');
    assert.equal(w.document.getElementById('submit-btn').disabled, false);
    assert.equal(w.document.getElementById('email').value, 'qa@stayhub.invalid');
  } finally { dom.window.close(); }
});
