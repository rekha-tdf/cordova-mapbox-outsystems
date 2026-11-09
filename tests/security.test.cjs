const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const os = require('node:os');
const path = require('node:path');
const hook = require('../hooks/verify-runtime-token');

function bridge() {
  const calls = [];
  const context = { module: { exports: {} }, require(name) {
    assert.equal(name, 'cordova/exec');
    return (ok, fail, service, action, args) => { calls.push({ action, args }); ok({}); };
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../www/MapboxPlugin.js'), 'utf8'), context);
  return { api: context.module.exports, calls };
}

test('geometry budgets apply to total vertices and path points', async () => {
  const { api, calls } = bridge();
  await api.loadBoundaries([{ geometry: Array(20000).fill({ lat: 0, lon: 0 }) }]);
  await api.loadPath({ points: Array(20000).fill({ lat: 0, lon: 0 }) });
  assert.equal(calls.length, 2);
  await assert.rejects(api.loadBoundaries([{ geometry: Array(10001) }, { geometry: Array(10000) }]), /vertices/);
  await assert.rejects(api.loadPath({ points: Array(20001) }), /20000/);
  await assert.rejects(api.loadPath({ points: [] }), /20000/);
  await assert.rejects(api.loadMarkers(Array(10001)), /markers/);
  assert.equal(calls.length, 2, 'invalid inputs never reach Cordova');
});

test('loadBoundaries accepts options objects and JSON payloads while retaining the legacy form', async () => {
  const { api, calls } = bridge();
  const payload = {
    boundaries: [{ geometry: [{ lat: 1, lon: 2 }] }],
    visible: false,
    fillColor: '#123456'
  };

  await api.loadBoundaries(payload);
  await api.loadBoundaries(JSON.stringify(payload));
  await api.loadBoundaries(payload.boundaries, { visible: false });

  assert.deepEqual(JSON.parse(JSON.stringify(calls.map(call => call.args[0]))), [
    payload,
    payload,
    { visible: false, boundaries: payload.boundaries }
  ]);
  await assert.rejects(api.loadBoundaries({ visible: true }), /boundaries array/);
});

test('zoom validation rejects overflow, nonfinite, reversed and out-of-range values', async () => {
  const { api, calls } = bridge();
  for (const action of ['downloadOfflineRegion', 'downloadOfflineRegionForRect']) {
    for (const options of [{minZoom:1e100}, {maxZoom:Infinity}, {minZoom:NaN}, {minZoom:19}, {maxZoom:1}, {minZoom:16,maxZoom:10}]) {
      await assert.rejects(api[action](options), /zoom/);
    }
    await api[action]({ minZoom: 2, maxZoom: 18 });
  }
  assert.equal(calls.length, 2);
});

test('byte budgets and invalid JSON fail before parsing/bridge dispatch', async () => {
  const { api, calls } = bridge();
  await assert.rejects(api.loadPath(' '.repeat(4 * 1024 * 1024 + 1)), /large/);
  await assert.rejects(api.loadBoundaries('bad json'), /Invalid boundaries JSON/);
  await assert.rejects(api.addMarker({ id: 'a'.repeat(4 * 1024 * 1024 + 1) }), /limit/);
  const cycle = {}; cycle.self = cycle;
  await assert.rejects(api.setCamera(cycle));
  assert.equal(calls.length, 0);
});

test('token hook rejects secret and encoded secret values regardless of attribute order', () => {
  for (const token of ['sk.fake.secret', 's&#107;.fake.secret', '&#x73;k.fake.secret', 'tk.fake.token']) {
    assert.throws(() => hook.verify(`<preference value='${token}' name='MAPBOX_ACCESS_TOKEN' />`), /public/);
  }
  hook.verify('<preference name="MAPBOX_ACCESS_TOKEN" value="pk.fake.public"/>');
  hook.verify('<preference name="MAPBOX_ACCESS_TOKEN" value="__MAPBOX_ACCESS_TOKEN_NOT_SET__"/>');
  assert.throws(() => hook.verify('<preference name="MAPBOX_ACCESS_TOKEN" value="pk.fake.public"/><preference name="MAPBOX_ACCESS_TOKEN" value="sk.bad.secret"/>'), /public/);
});

test('token build hook checks generated Android and iOS configuration', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mapbox-test-'));
  try {
    for (const platform of ['android/app/src/main/res/xml', 'ios/TestApp']) {
      const directory = path.join(root, 'platforms', platform);
      fs.mkdirSync(directory, { recursive: true });
      const file = path.join(directory, 'config.xml');
      fs.writeFileSync(file, '<preference name="MAPBOX_ACCESS_TOKEN" value="sk.fake.secret"/>');
      assert.throws(() => hook({ opts: { projectRoot: root } }), /public/);
      fs.writeFileSync(file, '<preference name="MAPBOX_ACCESS_TOKEN" value="pk.fake.public"/>');
      hook({ opts: { projectRoot: root } });
    }
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
