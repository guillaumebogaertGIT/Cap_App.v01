// Run: node tools/test_food_diary.mjs
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
const elements = new Map(), listeners = new Map();
class Element {
    constructor() { this.value = ''; this.children = []; this.events = {}; }
    append(...items) { this.children.push(...items); }
    replaceChildren(...items) { this.children = items; }
    add(item) { this.append(item); if (!this.value) this.value = item.value; }
    addEventListener(name, callback) { this.events[name] = callback; }
    setAttribute() {} focus() {} reportValidity() { return true; } reset() {}
}
const get = id => { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); };
let selected = { id: 'preview-guillaume', name: 'Guillaume' };
const context = { window: { capAccess: { signedIn: true }, capNutritionTargets: { get: () => null, selectedAthlete: () => selected } },
    previewRole: 'athlete', Intl, Date, crypto: { randomUUID: () => String(Math.random()) },
    Option: class extends Element { constructor(text, value) { super(); this.textContent = text; this.value = value; } },
    document: { getElementById: get, createElement: () => new Element(), addEventListener: (event, fn) => listeners.set(event, fn) } };
vm.createContext(context);
for (const file of ['food-data.js', 'food-diary.js']) vm.runInContext(await fs.readFile(new URL('../frontend/js/' + file, import.meta.url), 'utf8'), context);
const math = context.window.capFoodMath, foods = context.window.capFoodDatabase.foods;
assert.equal(JSON.stringify(math.macroShares({ proteinG: 25, carbsG: 25, fatG: 100 / 9 })), '[34,33,33]');
assert.equal(JSON.stringify(math.macroShares({ proteinG: 0, carbsG: 0, fatG: 10 })), '[0,0,100]');
assert.equal(JSON.stringify(math.macroShares({ proteinG: 0, carbsG: 0, fatG: 0 })), '[0,0,0]');
assert.equal(foods.length, 55); assert.equal(foods.filter(food => food.complete).length, 53);
const food = foods[0], portion = math.portion(food, 150);
assert.equal(portion.calories, food.calories * 1.5);
assert.ok(Math.abs(math.totals([portion, portion]).proteinG - food.proteinG * 3) < 1e-10);
for (const amount of ['', 0, -1, Infinity]) assert.throws(() => math.portion(food, amount));
assert.throws(() => math.portion(foods.find(food => !food.complete), 100));
assert.throws(() => math.values({ calories: '', proteinG: 0, carbsG: 0, fatG: 0 }));
assert.equal(math.validDay('2026-02-30', '2026-10-22'), false);
assert.equal(math.validDay('2026-10-23', '2026-10-22'), false);
assert.equal(math.validDay('2026-10-22', '2026-10-22'), true);
listeners.get('DOMContentLoaded')();
const fire = (id, event) => get(id).events[event]({ preventDefault() {}, currentTarget: get(id) });
get('food-meal').value = 'breakfast'; get('food-product').value = food.id; get('food-amount').value = '150';
fire('food-amount', 'input');
const portionCalories = () => get('food-portion-chart').children[0].children[0].children[0].textContent;
assert.equal(portionCalories(), new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(food.calories * 1.5));
get('food-amount').value = '300'; fire('food-amount', 'input');
assert.equal(portionCalories(), new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(food.calories * 3));
get('food-amount').value = '150';
fire('food-entry-form', 'submit');
assert.equal(portionCalories(), '0'); // Submitted portion resets; daily totals remain logged.
assert.equal(get('athlete-food-totals').children[0].children.some(child => child.className === 'macro-bar macro-bar-calories'), true);
assert.match(get('athlete-food-status').textContent, /^1 product /);
assert.match(get('coach-food-status').textContent, /^1 product /);
const meal = get('athlete-food-entries').children[0];
const saveForm = meal.children.at(-1).children[1];
saveForm.children[0].children[0].value = 'Overnight oats';
saveForm.events.submit({ preventDefault() {} });
assert.equal(get('food-saved-meals').children[0].children[0].children[0].textContent, 'Overnight oats');
meal.children[1].children[1].events.click();
assert.match(get('athlete-food-status').textContent, /^0 producten /);
fire('food-undo', 'click'); assert.match(get('athlete-food-status').textContent, /^1 product /);
const day = get('athlete-food-date').value;
get('athlete-food-date').value = '2026-01-01'; fire('athlete-food-date', 'change');
assert.match(get('athlete-food-status').textContent, /^0 producten /);
get('food-saved-meals').children[0].children[1].events.click();
assert.match(get('athlete-food-status').textContent, /^1 product /);
assert.equal(get('athlete-food-entries').children[0].children[1].children[0].children[0].textContent, 'Overnight oats');
get('athlete-food-date').value = day; fire('athlete-food-date', 'change');
selected = { id: 'example-anna-peeters', name: 'Anna' }; listeners.get('cap:nutrition-athlete-changed')();
assert.match(get('coach-food-status').textContent, /^0 producten /);
context.previewRole = 'coach'; get('food-amount').value = '100'; fire('food-entry-form', 'submit');
assert.match(get('athlete-food-status').textContent, /^1 product /);
context.window.capAccess.signedIn = false; listeners.get('cap:viewchange')();
assert.match(get('athlete-food-status').textContent, /^0 producten /);
assert.equal(get('food-saved-meals').children[0].textContent, 'Je hebt nog geen vaste maaltijden bewaard.');
console.log('Food diary: portion/date validation, add/delete/undo, date and athlete isolation, role guard and signout passed.');
