// Date-only arithmetic in UTC avoids browser timezone and daylight-saving shifts.
window.capAgenda = {
    today() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date()); },
    add(date, days) {
        const value = new Date(`${date}T12:00:00Z`);
        value.setUTCDate(value.getUTCDate() + days);
        return value.toISOString().slice(0, 10);
    },
    monday(date) {
        const day = new Date(`${date}T12:00:00Z`).getUTCDay();
        return this.add(date, -((day + 6) % 7));
    },
    label(date) { return new Intl.DateTimeFormat('nl-BE', { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`)); },
    range(date) { const first = this.monday(date); return `${this.label(first)} – ${this.label(this.add(first, 6))}`; },
    mount(input, { mode = 'week', onChange = () => {}, fixedWeek = null, dayOnly = false, sessions = false } = {}) {
        const api = this;
        const box = document.createElement('div'); box.className = 'assignment-agenda';
        const modeLabel = document.createElement('label'); modeLabel.textContent = 'Planning';
        const select = document.createElement('select');
        select.add(new Option('Workouts deze week · sporter kiest de dag', 'week'));
        select.add(new Option('Op een dag plannen', 'day'));
        select.value = dayOnly ? 'day' : mode; modeLabel.append(select); modeLabel.hidden = dayOnly;
        const toolbar = document.createElement('div'); toolbar.className = 'agenda-week-toolbar';
        const previous = document.createElement('button'); previous.type = 'button'; previous.className = 'secondary-button'; previous.textContent = '←'; previous.setAttribute('aria-label', 'Vorige week');
        const next = document.createElement('button'); next.type = 'button'; next.className = 'secondary-button'; next.textContent = '→'; next.setAttribute('aria-label', 'Volgende week');
        const title = document.createElement('strong'); title.setAttribute('aria-live', 'polite');
        toolbar.append(previous, title, next);
        const days = document.createElement('div'); days.className = 'agenda-days';
        const help = document.createElement('p'); help.className = 'sample-notice';
        box.append(modeLabel, toolbar, days, help); input.closest('label').before(box);
        input.min = api.today();
        if (fixedWeek) { input.min = fixedWeek > api.today() ? fixedWeek : api.today(); input.max = api.add(fixedWeek, 6); }
        if (!input.value || input.value < input.min) input.value = input.min;
        const value = () => ({ mode: select.value, date: input.value, weekStart: api.monday(input.value) });
        function render() {
            select.options[0].textContent = sessions ? 'Week bekijken · kies groepssessies' : 'Workouts deze week · sporter kiest de dag';
            select.options[1].textContent = sessions ? 'Dag bekijken · kies groepssessies' : 'Op een dag plannen';
            const first = api.monday(input.value || api.today());
            title.textContent = api.range(first); days.replaceChildren();
            for (let index = 0; index < 7; index++) {
                const date = api.add(first, index);
                const button = document.createElement('button'); button.type = 'button'; button.className = 'agenda-day';
                const weekday = document.createElement('span'); weekday.textContent = ['Ma', 'Di', 'Wo', 'Do', 'Vr', 'Za', 'Zo'][index];
                const number = document.createElement('strong'); number.textContent = Number(date.slice(8));
                button.append(weekday, number); button.setAttribute('aria-label', api.label(date));
                button.disabled = date < input.min || Boolean(input.max && date > input.max);
                button.setAttribute('aria-pressed', String(select.value === 'week' || date === input.value));
                button.addEventListener('click', () => { input.value = date; render(); onChange(value()); });
                days.append(button);
            }
            previous.disabled = Boolean(fixedWeek) || api.add(first, -1) < input.min;
            next.disabled = Boolean(fixedWeek);
            help.textContent = sessions ? 'Kies hieronder de specifieke geplande groepssessies die deze workout krijgen.' : select.value === 'week'
                ? 'Eén workout voor deze week. De sporter kiest zelf een dag.'
                : 'De workout wordt op de gekozen dag gepland.';
        }
        previous.addEventListener('click', () => { input.value = api.add(input.value, -7); if (input.value < input.min) input.value = input.min; render(); onChange(value()); });
        next.addEventListener('click', () => { input.value = api.add(input.value, 7); render(); onChange(value()); });
        select.addEventListener('change', () => { render(); onChange(value()); });
        input.addEventListener('change', () => { if (input.value) { render(); onChange(value()); } });
        render();
        return { value, box, setGroup(group) { sessions = group; render(); }, reset(date = api.today(), nextMode = 'week') { input.value = date; select.value = dayOnly ? 'day' : nextMode; render(); } };
    }
};
