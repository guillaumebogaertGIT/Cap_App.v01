import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const element = tag => ({
    tag, children: [], options: [], value: '', min: '', max: '',
    attributes: {}, listeners: {},
    append(...items) { this.children.push(...items); },
    add(option) { this.options.push(option); },
    replaceChildren() { this.children = []; },
    setAttribute(name, value) { this.attributes[name] = value; },
    addEventListener(name, callback) { this.listeners[name] = callback; },
    before() {},
    closest() { return this; }
});
const context = {
    window: {}, document: { createElement: element },
    Option: class { constructor(text, value) { this.textContent = text; this.value = value; } }
};
vm.runInNewContext(readFileSync(new URL('../frontend/js/assignment-agenda.js', import.meta.url), 'utf8'), context);
const agenda = context.window.capAgenda;
assert.equal(agenda.monday('2026-10-25'), '2026-10-19');
assert.equal(agenda.add('2026-10-24', 2), '2026-10-26'); // European daylight-saving boundary.
assert.equal(agenda.monday('2027-01-01'), '2026-12-28');
const first = agenda.monday(agenda.add(agenda.today(), 7));
const input = element('input'); input.value = first;
let selection;
const picker = agenda.mount(input, { onChange: value => { selection = value; } });
assert.equal(picker.value().mode, 'week');
assert.equal(picker.value().weekStart, first);
assert.equal(picker.box.children[2].children.length, 7);
const mode = picker.box.children[0].children[0];
mode.value = 'day'; mode.listeners.change();
picker.box.children[2].children[2].listeners.click();
assert.equal(selection.date, agenda.add(first, 2));
assert.equal(selection.mode, 'day');
picker.setGroup(true);
assert.match(mode.options[0].textContent, /groepssessies/);
const bounded = element('input'); bounded.value = first;
const dayPicker = agenda.mount(bounded, { dayOnly: true, fixedWeek: first });
assert.equal(bounded.min, first);
assert.equal(bounded.max, agenda.add(first, 6));
assert.equal(dayPicker.value().mode, 'day');
assert.equal(dayPicker.box.children[1].children[0].disabled, true);
assert.equal(dayPicker.box.children[1].children[2].disabled, true);
console.log('Agenda checks passed: week boundaries, day selection, group labels and bounded athlete planning.');
