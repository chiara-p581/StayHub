const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), {JSDOM}=require('jsdom'), {parse}=require('@babel/parser');
const web=path.resolve(__dirname,'../src/main/webapp');
function browser(){const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://stayhub.test/StayHub/index.html',runScripts:'outside-only'});dom.window.eval(fs.readFileSync(path.join(web,'assets/js/vendor/purify.min.js'),'utf8'));dom.window.eval(fs.readFileSync(path.join(web,'assets/js/dom-seguro.js'),'utf8')+'\nwindow.StayHubDOM=StayHubDOM;window.safeDOM=StayHubDOM;');return dom;}
function walk(n,fn){if(!n||typeof n!=='object')return;fn(n);for(const[k,v]of Object.entries(n)){if(k==='loc')continue;if(Array.isArray(v))v.forEach(x=>walk(x,fn));else if(v&&typeof v==='object')walk(v,fn);}}
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
test('todas las páginas usan scripts locales, sin handlers HTML ni código inline',()=>{
 for(const f of files(web).filter(p=>p.endsWith('.html'))){const dom=new JSDOM(fs.readFileSync(f,'utf8'));for(const script of dom.window.document.querySelectorAll('script')){assert.equal(script.textContent.trim(),'');assert.ok(!/^https?:/.test(script.getAttribute('src')));assert.ok(fs.existsSync(path.join(web,script.getAttribute('src').split('?')[0])));}for(const e of dom.window.document.querySelectorAll('*'))for(const a of e.attributes)assert.ok(!/^on/i.test(a.name),`${f}: ${a.name}`);dom.window.close();}
});
test('cada escritura innerHTML pasa por el saneador antes de tocar DOM',()=>{
 for(const p of files(path.join(web,'assets/js')).filter(p=>p.endsWith('.js')&&!p.includes('/vendor/'))){walk(parse(fs.readFileSync(p,'utf8')),n=>{if(n.type==='AssignmentExpression'&&n.left.type==='MemberExpression'&&n.left.property.name==='innerHTML'){assert.equal(n.operator,'=');assert.equal(n.right.type,'CallExpression',p);assert.equal(n.right.callee.object?.name,'StayHubDOM',p);assert.equal(n.right.callee.property?.name,'html',p);}});}
});
test('payloads de nombre, atributo, SVG y URL no crean código ejecutable',()=>{
 const dom=browser(),w=dom.window;const attacks=['<img src=x onerror="alert(1)">','<svg onload="alert(1)"></svg>','<a href="javascript:alert(1)">x</a>','<script>alert(1)</script>','<iframe srcdoc="<script>alert(1)</script>"></iframe>','<math><mtext><table><mglyph><style><!--</style><img title="--><img src=x onerror=alert(1)>">'];
 for(const payload of attacks){const div=w.document.createElement('div');div.innerHTML=w.safeDOM.html('<div><b>'+payload+'</b></div>');assert.equal(div.querySelectorAll('script,iframe,svg,math,object,embed').length,0);for(const e of div.querySelectorAll('*'))for(const a of e.attributes){assert.ok(!/^on/i.test(a.name));assert.ok(!/^javascript:/i.test(a.value));}}
 assert.equal(w.safeDOM.text('<img onerror=x>'),'&lt;img onerror=x&gt;');dom.window.close();
});
test('login manda CSRF y logout espera al servidor y limpia carrito',async()=>{
 const dom=browser(),w=dom.window,calls=[];w.fetch=async(url,options)=>{calls.push({url,options});return{ok:true,status:url.endsWith('sesion')?204:200,headers:{get:()=> 'application/json'},json:async()=>({token:'token-prueba'}),text:async()=>JSON.stringify({id:1,rol:'HUESPED',nombre:'Ana',apellido:'Paz'})};};
 w.eval(fs.readFileSync(path.join(web,'assets/js/api.js'),'utf8')+'\nwindow.testApi=Api;');await w.testApi.loginUsuario({email:'ana@test.com',password:'password'});assert.equal(calls[0].url,'api/seguridad/csrf');assert.equal(calls[1].options.headers['X-CSRF-Token'],'token-prueba');
 w.testApi.setSession({id:1,rol:'HUESPED'});w.testApi.addLocalCartItem({hotelNombre:'Hotel'});await w.testApi.clearSession();assert.equal(w.testApi.currentUser(),null);assert.equal(w.testApi.localCart().length,0);assert.equal(calls.at(-1).options.method,'DELETE');assert.equal(calls.at(-1).options.headers['X-CSRF-Token'],'token-prueba');dom.window.close();
});

test('recuperar y resetear funcionan sin login y envían CSRF desde las páginas reales', async()=>{
 for(const reset of [false,true]){
  const page=reset?'reset-password.html':'recover-password.html';
  const dom=new JSDOM(fs.readFileSync(path.join(web,page),'utf8'),{url:'https://stayhub.test/StayHub/'+page+(reset?'#token=token-recuperacion':''),runScripts:'outside-only'});
  const w=dom.window,calls=[],errors=[];
  w.addEventListener("error",event=>errors.push(event.error));
  w.eval(fs.readFileSync(path.join(web,"assets/js/vendor/purify.min.js"),"utf8"));
  w.eval(fs.readFileSync(path.join(web,"assets/js/dom-seguro.js"),"utf8")+"\nwindow.StayHubDOM=StayHubDOM;");
  w.fetch=async(url,options)=>{calls.push({url,options});return{ok:true,status:url.endsWith('csrf')?200:204,headers:{get:()=> 'application/json'},json:async()=>({token:'csrf-prueba'})};};
  w.eval(fs.readFileSync(path.join(web,'assets/js/api.js'),'utf8')+'\nwindow.Api=Api;');
  w.eval(fs.readFileSync(path.join(web,'assets/js/pages/'+(reset?'reset-password-1.js':'recover-password-1.js')),'utf8'));
  if(reset){assert.equal(w.location.hash,'');w.document.getElementById('password').value='nueva-password';w.document.getElementById('confirm-password').value='nueva-password';}
  else w.document.getElementById('email').value='ana@test.com';
  w.document.querySelector('form').dispatchEvent(new w.Event('submit',{cancelable:true}));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(calls.length,2);assert.equal(calls[0].url,'api/seguridad/csrf');
  assert.equal(calls[1].url,'api/usuarios/'+(reset?'resetear-password':'recuperar-password'));
  assert.equal(calls[1].options.headers['X-CSRF-Token'],'csrf-prueba');
  assert.equal(calls[1].options.credentials,'same-origin');
  assert.deepEqual(JSON.parse(calls[1].options.body),reset?{token:'token-recuperacion',nuevaPassword:'nueva-password'}:{email:'ana@test.com'});
  assert.match(w.document.getElementById('aviso-pendiente').textContent,reset?/Contraseña actualizada/:/Si existe la cuenta/);
  assert.equal(w.Api.currentUser(),null);assert.deepEqual(errors,[]);dom.window.close();
 }
});
