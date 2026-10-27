// Keep native ISO-valued controls for validation/FormData and present local text fields.
(() => {
    const format = window.capDateTime;
    const enhanced = new WeakSet();
    const valueProperty = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    function enhance(input) {
        if (enhanced.has(input)) return;
        enhanced.add(input);
        const isDate = input.type === 'date';
        const wrapper = document.createElement('span'); wrapper.className = 'local-date-control';
        input.before(wrapper); wrapper.append(input);
        const display = document.createElement('input'); display.type = 'text';
        display.className = 'local-date-display'; display.inputMode = 'numeric';
        display.placeholder = isDate ? 'dd/mm/jjjj' : 'uu:mm';
        display.autocomplete = 'off'; display.maxLength = isDate ? 10 : 5;
        display.setAttribute('aria-label', (input.getAttribute('aria-label') || input.closest('label')?.childNodes[0]?.textContent.trim() || (isDate ? 'Datum' : 'Tijd')) + (isDate ? ' (dd/mm/jjjj)' : ' (24 uur, uu:mm)'));
        for (const name of ['aria-describedby', 'form']) if (input.hasAttribute(name)) display.setAttribute(name, input.getAttribute(name));
        wrapper.prepend(display);
        input.classList.add('local-date-original'); input.tabIndex = -1; input.setAttribute('aria-hidden', 'true');
        let editing = false;
        function error() {
            if (input.validity.rangeUnderflow) return isDate ? `Kies ${format.date(input.min)} of later.` : `Kies ${input.min} of later.`;
            if (input.validity.rangeOverflow) return isDate ? `Kies ${format.date(input.max)} of eerder.` : `Kies ${input.max} of eerder.`;
            if (input.validity.valueMissing) return isDate ? 'Vul een datum in (dd/mm/jjjj).' : 'Vul een tijd in (uu:mm, 24 uur).';
            return input.validationMessage;
        }
        function sync() {
            display.disabled = input.disabled; display.required = input.required; display.readOnly = input.readOnly;
            if (input.hasAttribute('form')) display.setAttribute('form', input.getAttribute('form'));
            else display.removeAttribute('form');
            if (!editing) {
                display.value = isDate ? format.date(input.value) : input.value;
                display.setCustomValidity(error());
            }
        }
        Object.defineProperty(input, 'value', {
            configurable: true,
            get() { return valueProperty.get.call(this); },
            set(value) { valueProperty.set.call(this, value); sync(); }
        });
        input.focus = options => display.focus(options);
        function commit(event) {
            event.stopPropagation();
            const raw = display.value;
            const parsed = isDate ? format.parseDate(raw) : format.parseTime(raw);
            const invalid = raw.trim() && !parsed ? (isDate ? 'Gebruik een geldige datum: dd/mm/jjjj.' : 'Gebruik een tijd tussen 00:00 en 23:59.') : '';
            editing = true;
            input.setCustomValidity(''); input.value = parsed;
            input.dispatchEvent(new Event(event.type, { bubbles: true }));
            editing = false;
            display.setCustomValidity(invalid || error());
            if (event.type === 'change' && parsed) display.value = isDate ? format.date(parsed) : parsed;
        }
        display.addEventListener('input', commit); display.addEventListener('change', commit);
        input.addEventListener('input', sync); input.addEventListener('change', sync);
        input.addEventListener('invalid', event => { event.preventDefault(); sync(); display.reportValidity(); display.focus(); });
        if (isDate) {
            const picker = document.createElement('button'); picker.type = 'button'; picker.className = 'local-date-picker secondary-button';
            picker.setAttribute('aria-label', 'Kalender openen'); picker.title = 'Kalender openen';
            picker.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18"/></svg>';
            picker.addEventListener('click', () => { if (input.disabled || input.readOnly) return; try { input.showPicker(); } catch { display.focus(); } });
            wrapper.append(picker);
            new MutationObserver(() => { picker.disabled = input.disabled || input.readOnly; }).observe(input, { attributes: true, attributeFilter: ['disabled', 'readonly'] });
            picker.disabled = input.disabled || input.readOnly;
        }
        new MutationObserver(sync).observe(input, { attributes: true, attributeFilter: ['disabled', 'required', 'readonly', 'min', 'max', 'form'] });
        input.form?.addEventListener('reset', () => queueMicrotask(sync));
        sync();
    }
    function scan(root) {
        if (root.matches?.('input[type="date"], input[type="time"]')) enhance(root);
        root.querySelectorAll?.('input[type="date"], input[type="time"]').forEach(enhance);
    }
    scan(document);
    new MutationObserver(records => records.forEach(record => record.addedNodes.forEach(node => { if (node.nodeType === 1) scan(node); }))).observe(document.body, { childList: true, subtree: true });
})();
