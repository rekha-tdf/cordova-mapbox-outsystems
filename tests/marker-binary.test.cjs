// Waypoint binary images and Find marker URL regression tests (simulated WebView + bridge).
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const WWW = path.join(__dirname, '..', 'www');
const PNG = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
const JPEG = [0xFF, 0xD8, 0xFF, 0xE0];
const BMP = [0x42, 0x4D];

function bytes(signature, size, seed) {
  const out = Buffer.alloc(size);
  Buffer.from(signature).copy(out);
  for (let i = signature.length; i < size; i++) out[i] = (i * 31 + seed * 7) & 0xff;
  return out;
}
const b64 = (buf) => buf.toString('base64');

function load({ canvas = true, decodable = () => true } = {}) {
  const calls = [];
  const warnings = [];
  const win = {
    console: { warn: (m) => warnings.push(m), error() {} },
    btoa: (s) => Buffer.from(s, 'binary').toString('base64'),
    atob: (s) => Buffer.from(s, 'base64').toString('binary'),
    setTimeout, clearTimeout,
    cordova: { platformId: 'browser' }
  };
  if (canvas) {
    win.Image = function () {
      const img = this;
      Object.defineProperty(img, 'src', {
        set(value) {
          img._src = value;
          setImmediate(() => {
            const ok = /^data:image\//.test(value) && decodable(value);
            if (ok) { img.naturalWidth = 400; img.naturalHeight = 300; img.onload && img.onload(); }
            else if (img.onerror) img.onerror();
          });
        }
      });
    };
    win.document = {
      createElement() {
        const c = { drawn: null, getContext: () => ({ drawImage: (i, sx, sy, sw, sh, dx, dy, dw, dh) => { c.drawn = { src: i._src, sx, sy, sw, sh, dw, dh }; } }) };
        c.toDataURL = (type) => 'data:' + type + ';base64,' +
          Buffer.from('canvas ' + c.width + 'x' + c.height + ' ' + require('crypto').createHash('sha1').update(c.drawn.src).digest('hex')).toString('base64');
        return c;
      }
    };
  }
  const modules = {};
  function requireModule(id) {
    if (id === 'cordova/exec') {
      return (ok, fail, service, action, args) => {
        calls.push({ action, args, sync: true });
        setImmediate(() => ok(action === 'addMarker' ? { id: args[0].id } : undefined));
      };
    }
    const name = id.replace('./', '');
    if (!modules[name]) {
      const module = { exports: {} };
      const source = fs.readFileSync(path.join(WWW, name + '.js'), 'utf8');
      vm.runInNewContext('(function (require, module, exports, window) {' + source + '\n})', {
        Promise, ArrayBuffer, Uint8Array, Object, Array, JSON, String, Math, Error, Number, console
      })(requireModule, module, module.exports, win);
      modules[name] = module.exports;
    }
    return modules[name];
  }
  const api = requireModule('./MapboxPlugin');
  return { api, calls, warnings, win };
}

test('Find marker with imageUrl uses the original synchronous path, unchanged', async () => {
  const { api, calls } = load();
  const options = { id: 'f1', latitude: 10, longitude: 20, isFind: true, imageUrl: 'https://cdn.example.com/a.png' };
  const promise = api.addMarker(options);
  assert.strictEqual(calls.length, 1, 'exec must run synchronously');
  assert.strictEqual(calls[0].args[0], options, 'same object, untouched');
  assert.deepStrictEqual(await promise, { id: 'f1' });
});

test('Waypoint addMarker converts binary into a data URI and keeps every other field', async () => {
  const { api, calls } = load();
  const options = {
    id: 'w1', latitude: -33.9, longitude: 151.2, isFind: false, pinColor: '#16A34A',
    pinStyle: 'photo', isOnline: false, imageBinary: b64(bytes(PNG, 2000, 1)), imageMimeType: 'application/octet-stream'
  };
  const before = JSON.stringify(options);
  await api.addMarker(options);
  const sent = calls[0].args[0];
  assert.match(sent.imageUrl, /^data:image\/png;base64,/);
  assert.strictEqual(sent.imageBinary, undefined);
  assert.strictEqual(sent.imageMimeType, undefined);
  for (const key of ['id', 'latitude', 'longitude', 'isFind', 'pinColor', 'pinStyle', 'isOnline']) {
    assert.strictEqual(sent[key], options[key], key);
  }
  assert.strictEqual(JSON.stringify(options), before, 'caller object not mutated');
  assert.match(Buffer.from(sent.imageUrl.split(',')[1], 'base64').toString(), /^canvas 192x192 /, 'center square, reduced');
});

test('isFind defaults to Waypoint, JPEG stays JPEG, data URI and byte inputs work', async () => {
  const { api, calls } = load();
  await api.addMarker({ id: 'a', latitude: 1, longitude: 1, imageBinary: 'data:image/jpg;base64,' + b64(bytes(JPEG, 500, 2)) });
  await api.addMarker({ id: 'b', latitude: 1, longitude: 1, imageBinary: new Uint8Array(bytes(PNG, 500, 3)) });
  await api.addMarker({ id: 'c', latitude: 1, longitude: 1, imageBinary: bytes(PNG, 500, 4).buffer.slice(0, 500) });
  await api.addMarker({ id: 'd', latitude: 1, longitude: 1, imageBinary: Array.from(bytes(PNG, 100, 5)) });
  assert.match(calls[0].args[0].imageUrl, /^data:image\/jpeg;base64,/);
  for (const call of calls.slice(1)) assert.match(call.args[0].imageUrl, /^data:image\/png;base64,/);
});

test('Find marker ignores binary and keeps its URL', async () => {
  const { api, calls, warnings } = load();
  await api.addMarker({ id: 'f', latitude: 0, longitude: 0, isFind: true, imageUrl: 'https://x.example/f.png', imageBinary: b64(bytes(PNG, 100, 1)) });
  assert.strictEqual(calls[0].args[0].imageUrl, 'https://x.example/f.png');
  assert.strictEqual(calls[0].args[0].imageBinary, undefined);
  assert.ok(warnings.some((w) => /ignored for Find/.test(w)));
});

test('loadMarkers: mixed Find + Waypoint list, each waypoint gets its own image, order and positions kept', async () => {
  const { api, calls } = load();
  const shared = b64(bytes(PNG, 800, 9));
  const markers = [
    { Id: 'find-1', Latitude: 1, Longitude: 2, IsFind: true, ImageUrl: 'https://cdn.example.com/find.png' },
    { Id: 'wp-1', Latitude: 3, Longitude: 4, IsFind: false, ImageBinary: b64(bytes(PNG, 800, 1)) },
    { Id: 'wp-2', Latitude: 5, Longitude: 6, IsFind: false, ImageBinary: b64(bytes(JPEG, 800, 2)) },
    { Id: 'wp-3', Latitude: 7, Longitude: 8, ImageBinary: shared, PinStyle: 'photo' },
    { Id: 'wp-4', Latitude: 9, Longitude: 10, ImageBinary: shared },
    { Id: 'wp-5', Latitude: 11, Longitude: 12, ImageUrl: 'https://cdn.example.com/w.png' }
  ];
  const snapshot = JSON.stringify(markers);
  await api.loadMarkers(markers, { replace: true });
  assert.strictEqual(calls.length, 1);
  const sent = calls[0].args[0];
  assert.strictEqual(sent.replace, true);
  assert.deepStrictEqual(sent.markers.map((m) => [m.Id, m.Latitude, m.Longitude]), markers.map((m) => [m.Id, m.Latitude, m.Longitude]));
  assert.strictEqual(sent.markers[0], markers[0], 'Find marker passed through as-is');
  assert.strictEqual(sent.markers[5], markers[5], 'URL waypoint passed through as-is');
  const urls = sent.markers.slice(1, 5).map((m) => m.ImageUrl);
  urls.forEach((u) => assert.match(u, /^data:image\/(png|jpeg);base64,/));
  assert.strictEqual(new Set(urls.slice(0, 3)).size, 3, 'distinct images stay distinct');
  assert.strictEqual(urls[2], urls[3], 'identical binaries share one source');
  assert.strictEqual(sent.markers[3].PinStyle, 'photo');
  sent.markers.forEach((m) => { assert.strictEqual(m.ImageBinary, undefined); });
  assert.strictEqual(JSON.stringify(markers), snapshot, 'caller list not mutated');
});

test('loadMarkers without binary fields is byte-for-byte the original call', () => {
  const { api, calls } = load();
  const options = { replace: false };
  const markers = [{ Id: '1', Latitude: 1, Longitude: 1, IsFind: true, ImageUrl: 'https://a.example/1.png' }];
  api.loadMarkers(markers, options);
  assert.strictEqual(calls.length, 1, 'synchronous');
  assert.strictEqual(calls[0].args[0], options);
  assert.strictEqual(calls[0].args[0].markers, markers);
});

test('without a canvas the original PNG is sent; unsupported formats fall back to plain pin or URL', async () => {
  const { api, calls, warnings } = load({ canvas: false });
  const png = b64(bytes(PNG, 600, 1));
  await api.loadMarkers([
    { Id: 'a', Latitude: 1, Longitude: 1, ImageBinary: png },
    { Id: 'b', Latitude: 1, Longitude: 1, ImageBinary: b64(bytes(BMP, 600, 2)) },
    { Id: 'c', Latitude: 1, Longitude: 1, ImageBinary: b64(bytes(BMP, 600, 3)), ImageUrl: 'https://a.example/c.png' }
  ]);
  const sent = calls[0].args[0].markers;
  assert.strictEqual(sent[0].ImageUrl, 'data:image/png;base64,' + png);
  assert.strictEqual(sent[1].ImageUrl, undefined);
  assert.strictEqual(sent[2].ImageUrl, 'https://a.example/c.png');
  assert.strictEqual(warnings.length, 2);
});

test('undecodable image data keeps a plain pin instead of failing the load', async () => {
  const { api, calls } = load({ decodable: () => false });
  await api.loadMarkers([{ Id: 'x', Latitude: 1, Longitude: 1, ImageBinary: b64(Buffer.from('not an image at all')) }]);
  assert.strictEqual(calls[0].args[0].markers[0].ImageUrl, undefined);
});

test('invalid binary rejects the whole call before any native change', async () => {
  const { api, calls } = load();
  await assert.rejects(api.loadMarkers([
    { Id: 'ok', Latitude: 1, Longitude: 1, ImageBinary: b64(bytes(PNG, 100, 1)) },
    { Id: 'bad', Latitude: 1, Longitude: 1, ImageBinary: '***' }
  ]), /Invalid ImageBinary for marker "bad"/);
  await assert.rejects(api.addMarker({ id: 'o', ImageBinary: 1, imageBinary: { nope: 1 } }), /Invalid imageBinary/);
  assert.strictEqual(calls.length, 0);
});

test('large waypoint lists are split under the bridge limit; first batch replaces, Ids keep list index', async () => {
  const { api, calls } = load({ canvas: false });
  const markers = [];
  for (let i = 0; i < 20; i++) {
    const m = { Latitude: i, Longitude: i, ImageBinary: b64(bytes(PNG, 300 * 1024, i)) };
    if (i % 2) m.Id = 'wp-' + i;
    markers.push(m);
  }
  await api.loadMarkers(markers);
  assert.ok(calls.length >= 2, 'split into batches');
  calls.forEach((call, n) => {
    const json = JSON.stringify(call.args[0]);
    assert.ok(json.length + (json.match(/\//g) || []).length <= 4 * 1024 * 1024, 'batch ' + n + ' fits native limit');
    assert.strictEqual(call.args[0].replace, n === 0 ? undefined : false);
  });
  const all = calls.flatMap((c) => c.args[0].markers);
  assert.deepStrictEqual(all.map((m) => m.Id), markers.map((m, i) => m.Id || String(i)));
  assert.deepStrictEqual(all.map((m) => m.Latitude), markers.map((m) => m.Latitude));
  assert.ok(markers.every((m, i) => i % 2 || m.Id === undefined), 'caller markers not mutated');
});

test('close during image conversion cancels instead of drawing on the closed map', async () => {
  const { api, calls } = load();
  const pending = api.addMarker({ id: 'w', latitude: 1, longitude: 1, imageBinary: b64(bytes(PNG, 100, 1)) });
  api.close();
  await assert.rejects(pending, (e) => e.code === 'MAPBOX_OPERATION_CANCELLED');
  assert.deepStrictEqual(calls.map((c) => c.action), ['close']);
});

test('shared structure: Find has URL + empty/null binary, Waypoint has null URL + binary', async () => {
  const { api, calls, warnings } = load();
  const wp = b64(bytes(PNG, 900, 11));
  await api.loadMarkers([
    { Id: 'f1', Latitude: 1, Longitude: 2, IsFind: true, ImageUrl: 'https://bucket.s3.amazonaws.com/f1.png', ImageBinary: '' },
    { Id: 'f2', Latitude: 3, Longitude: 4, IsFind: true, ImageUrl: 'https://bucket.s3.amazonaws.com/f2.png', ImageBinary: null },
    { Id: 'w1', Latitude: 5, Longitude: 6, IsFind: false, ImageUrl: null, ImageBinary: wp },
    { Id: 'w2', Latitude: 7, Longitude: 8, IsFind: false, ImageUrl: '', ImageBinary: b64(bytes(JPEG, 900, 12)) },
    { Id: 'w3', Latitude: 9, Longitude: 10, IsFind: false, ImageUrl: '', ImageBinary: '' },
    { Id: 'w4', Latitude: 11, Longitude: 12, IsFind: false, ImageUrl: null, ImageBinary: null }
  ], { replace: true });
  const sent = calls[0].args[0].markers;
  assert.strictEqual(sent[0].ImageUrl, 'https://bucket.s3.amazonaws.com/f1.png');
  assert.strictEqual(sent[1].ImageUrl, 'https://bucket.s3.amazonaws.com/f2.png');
  assert.match(sent[2].ImageUrl, /^data:image\/png;base64,/);
  assert.match(sent[3].ImageUrl, /^data:image\/jpeg;base64,/);
  assert.strictEqual(sent[4].ImageUrl, '', 'no image: plain pin');
  assert.ok(!('ImageUrl' in sent[5]), 'null URL is omitted, not sent as JSON null');
  assert.ok(!('ImageUrl' in sent[2]) || /^data:/.test(sent[2].ImageUrl));
  sent.forEach((m) => assert.ok(!('ImageBinary' in m), 'binary never crosses the bridge'));
  assert.deepStrictEqual(sent.map((m) => m.IsFind), [true, true, false, false, false, false]);
  assert.strictEqual(warnings.length, 0, 'empty binaries on Find markers are not warned about');
});

test('private markers (IsPublic false) skip image conversion; public and unset markers are unchanged', async () => {
  let decodes = 0;
  const { api, calls } = load({ decodable: () => { decodes++; return true; } });
  await api.loadMarkers([
    { Id: 'pw', Latitude: 1, Longitude: 1, IsFind: false, IsPublic: false, ImageBinary: b64(bytes(PNG, 900, 21)) },
    { Id: 'pf', Latitude: 2, Longitude: 2, IsFind: true, IsPublic: false, ImageUrl: 'https://bucket.s3.amazonaws.com/secret.png', ImageBinary: '' },
    { Id: 'w', Latitude: 3, Longitude: 3, IsFind: false, IsPublic: true, ImageBinary: b64(bytes(PNG, 900, 22)) },
    { Id: 'w2', Latitude: 4, Longitude: 4, IsFind: false, ImageBinary: b64(bytes(PNG, 900, 23)) }
  ]);
  const sent = calls[0].args[0].markers;
  assert.strictEqual(sent[0].IsPublic, false, 'flag reaches native');
  assert.ok(!('ImageBinary' in sent[0]) && !('ImageUrl' in sent[0]), 'private waypoint sends no image');
  assert.strictEqual(sent[1].IsPublic, false);
  assert.match(sent[2].ImageUrl, /^data:image\/png;base64,/);
  assert.match(sent[3].ImageUrl, /^data:image\/png;base64,/);
  assert.strictEqual(decodes, 2, 'only the two public waypoints were decoded');
});
