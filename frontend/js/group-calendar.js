// Month overview for coaches; the existing club service owns sessions and saving.
(() => {
    const $ = id => document.getElementById(id);
    const club = window.capClub;
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const asDate = date => new Date(`${date}T12:00:00Z`);
    const iso = date => date.toISOString().slice(0, 10);
    const shift = (date, days) => { const result = asDate(date); result.setUTCDate(result.getUTCDate() + days); return iso(result); };
    const monthOf = date => date.slice(0, 7) + '-01';
    const fullDate = date => new Intl.DateTimeFormat('nl-BE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(asDate(date));
    let month = monthOf(today());
    let selected = today();
    let expandedId = null;
    const visible = () => location.hash === '#group-classes' && previewRole === 'coach' && window.capAccess.signedIn;
    const node = (tag, text, className) => {
        const result = document.createElement(tag);
        if (text != null) result.textContent = text;
        if (className) result.className = className;
        return result;
    };
    function selectDay(date, plan = false) {
        selected = date; month = monthOf(date); expandedId = null;
        render();
        $('group-calendar-grid').querySelector(`[data-group-date="${date}"]`)?.focus();
        if (plan && date >= today()) club.planGroupSession(date);
    }
    function render() {
        if (!visible()) return;
        const current = today();
        $('group-calendar-month').textContent = new Intl.DateTimeFormat('nl-BE', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(asDate(month));
        const sessions = club.groupSessions().sort((a, b) => a.start.localeCompare(b.start));
        const byDate = new Map();
        sessions.forEach(session => {
            if (!byDate.has(session.date)) byDate.set(session.date, []);
            byDate.get(session.date).push(session);
        });
        const count = sessions.filter(session => monthOf(session.date) === month).length;
        $('group-calendar-summary').textContent = `${count} ${count === 1 ? 'groepsles' : 'groepslessen'} deze maand · Klik op een datum om te plannen.`;
        const grid = $('group-calendar-grid'); grid.replaceChildren();
        const offset = (asDate(month).getUTCDay() + 6) % 7;
        const first = shift(month, -offset);
        // Six complete Monday–Sunday weeks keep month navigation visually stable.
        for (let index = 0; index < 42; index++) {
            const date = shift(first, index);
            const entries = byDate.get(date) || [];
            const cell = node('div', null, 'group-calendar-day');
            cell.classList.toggle('is-other-month', monthOf(date) !== month);
            cell.classList.toggle('is-selected', date === selected);
            const choose = node('button', null, 'group-day-select'); choose.type = 'button';
            choose.dataset.groupDate = date;
            choose.setAttribute('aria-pressed', String(date === selected));
            choose.setAttribute('aria-label', `${fullDate(date)} · ${entries.length} groepslessen${date >= current ? ' · Groepsles plannen' : ''}`);
            if (date === current) choose.setAttribute('aria-current', 'date');
            choose.append(node('span', String(asDate(date).getUTCDate()), 'group-day-number'));
            if (entries.length) choose.append(node('span', `${entries.length} ${entries.length === 1 ? 'les' : 'lessen'}`, 'group-day-count'));
            choose.addEventListener('click', () => selectDay(date, true));
            choose.addEventListener('keydown', event => {
                const amount = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[event.key];
                if (amount == null) return;
                event.preventDefault(); selectDay(shift(date, amount));
            });
            cell.append(choose);
            entries.slice(0, 2).forEach(session => {
                const event = node('button', `${session.start} ${session.category || session.title}`, 'group-calendar-event'); event.type = 'button';
                event.setAttribute('aria-label', `${session.title}, ${fullDate(date)}, ${session.start}. Details bekijken`);
                event.addEventListener('click', () => {
                    selected = date; month = monthOf(date); expandedId = session.id; render();
                    $('group-day-title').focus();
                });
                cell.append(event);
            });
            if (entries.length > 2) {
                const more = node('button', `+${entries.length - 2} meer`, 'group-calendar-more'); more.type = 'button';
                more.setAttribute('aria-label', `Alle ${entries.length} groepslessen op ${fullDate(date)} bekijken`);
                more.addEventListener('click', () => { selectDay(date); $('group-day-title').focus(); });
                cell.append(more);
            }
            grid.append(cell);
        }
        $('group-day-title').textContent = fullDate(selected);
        $('group-day-add').hidden = selected < current;
        $('new-group-session').disabled = selected < current;
        club.renderGroupDay(selected, $('group-day-sessions'), expandedId);
    }
    function changeMonth(amount) {
        const date = asDate(month); date.setUTCMonth(date.getUTCMonth() + amount);
        month = iso(date); selected = month === monthOf(today()) ? today() : month; expandedId = null;
        render();
    }
    $('group-previous-month').addEventListener('click', () => changeMonth(-1));
    $('group-next-month').addEventListener('click', () => changeMonth(1));
    $('group-current-month').addEventListener('click', () => { month = monthOf(today()); selected = today(); expandedId = null; render(); });
    [$('new-group-session'), $('group-day-add')].forEach(button => button.addEventListener('click', () => club.planGroupSession(selected)));
    document.addEventListener('cap:group-session-saved', event => {
        selected = event.detail.date; month = monthOf(selected); expandedId = event.detail.id; render();
    });
    document.addEventListener('cap:club-changed', () => {
        // Follow a saved session when its date is changed in the editor.
        if ($('session-dialog').open) return;
        if (expandedId) {
            const session = club.groupSessions().find(item => item.id === expandedId);
            if (session) { selected = session.date; month = monthOf(selected); }
        }
        render();
    });
    document.addEventListener('cap:viewchange', () => {
        if (visible()) render();
        else if ($('session-dialog').open) $('session-dialog').close();
    });
    render();
})();
