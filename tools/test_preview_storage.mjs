import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../frontend/js/preview-storage.js', import.meta.url), 'utf8');
const disk = new Map();
let warning = null;
const context = {
    window: {},
    localStorage: {
        getItem: key => disk.get(key) ?? null,
        setItem: (key, value) => disk.set(key, value)
    },
    document: {
        getElementById: () => warning,
        createElement: () => ({ setAttribute() {} }),
        querySelector: () => ({ prepend: element => { warning = element; } })
    }
};
const reload = () => { vm.runInNewContext(source, context); return context.window.capStorage; };
let store = reload();
store.write('preview-session', { signedIn: true, role: 'coach', plan: 'complete' });
store.map('nutrition-plans').set('preview-guillaume', { targets: { calories: 2400 } });
store.map('food-entries').set('preview-guillaume', [{ name: 'Example', date: '2026-10-23' }]);
store.write('saved-meals', [{ name: 'Example meal', ingredients: [] }]);
store = reload();
assert.equal(store.read('preview-session', {}).role, 'coach');
assert.equal(store.map('nutrition-plans').get('preview-guillaume').targets.calories, 2400);
assert.equal(store.map('food-entries').get('preview-guillaume')[0].name, 'Example');
assert.equal(store.read('saved-meals', [])[0].name, 'Example meal');
store.write('preview-session', { signedIn: false, role: 'coach', plan: 'none' });
store = reload();
assert.equal(store.read('preview-session', {}).signedIn, false);
assert.equal(store.map('food-entries').get('preview-guillaume').length, 1);
disk.set('cap-broken-v1', '{invalid');
assert.equal(store.read('broken', 'fallback'), 'fallback');
context.localStorage.setItem = () => { throw new Error('Storage full'); };
store.write('saved-meals', []);
assert.match(warning.textContent, /Bewaren.*mislukt/);
assert.equal(store.read('saved-meals', [])[0].name, 'Example meal');
console.log('Preview storage checks passed: reload, logout state, retained records, invalid JSON, failed writes.');
