// Date selection for all session types, opened from Planning's Les toevoegen.
(() => {
    const $ = id => document.getElementById(id);
    const club = window.capClub;
    const dialog = $('session-calendar-dialog');
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const asDate = date => new Date(`${date}T12:00:00Z`);
    const iso = date => date.toISOString().slice(0, 10);
    const shift = (date, days) => { const result = asDate(date); result.setUTCDate(result.getUTCDate() + days); return iso(result); };
    const monthOf = date => date.slice(0, 7) + '-01';
    const fullDate = date => window.capDateTime.dayLabel(date);
    let month = monthOf(today());
    let selected = today();
    let expandedId = null;
    let returnToCalendar = false;
    const permitted = () => location.hash === '#planning' && previewRole === 'coach' && window.capAccess.signedIn;
    const visible = () => dialog.open && permitted();
    const node = (tag, text, className) => {
        const result = document.createElement(tag);
        if (text != null) result.textContent = text;
        if (className) result.className = className;
        return result;
    };
    function selectDay(date, plan = false) {
        selected = date; month = monthOf(date); expandedId = null;
        render();
        $('planning-calendar-grid').querySelector(`[data-calendar-date="${date}"]`)?.focus();
        if (plan && date >= today()) club.planSession(date);
    }
    function render() {
        if (!visible()) return;
        const current = today();
        $('planning-calendar-month').textContent = new Intl.DateTimeFormat('nl-BE', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(asDate(month));
        const sessions = club.calendarSessions().sort((a, b) => a.start.localeCompare(b.start));
        const byDate = new Map();
        sessions.forEach(session => {
            if (!byDate.has(session.date)) byDate.set(session.date, []);
            byDate.get(session.date).push(session);
        });
        const count = sessions.filter(session => monthOf(session.date) === month).length;
        $('planning-calendar-summary').textContent = `${count} ${count === 1 ? 'sessie' : 'sessies'} deze maand · Klik op een datum om te plannen.`;
        const grid = $('planning-calendar-grid'); grid.replaceChildren();
        const offset = (asDate(month).getUTCDay() + 6) % 7;
        const first = shift(month, -offset);
        // Six complete Monday–Sunday weeks keep month navigation visually stable.
        for (let index = 0; index < 42; index++) {
            const date = shift(first, index);
            const entries = byDate.get(date) || [];
            const cell = node('div', null, 'planning-calendar-day');
            cell.classList.toggle('is-other-month', monthOf(date) !== month);
            cell.classList.toggle('is-selected', date === selected);
            const choose = node('button', null, 'calendar-day-select'); choose.type = 'button';
            choose.dataset.calendarDate = date;
            choose.setAttribute('aria-pressed', String(date === selected));
            choose.setAttribute('aria-label', `${fullDate(date)} · ${entries.length} sessies${date >= current ? ' · Les plannen' : ''}`);
            if (date === current) choose.setAttribute('aria-current', 'date');
            choose.append(node('span', String(asDate(date).getUTCDate()), 'calendar-day-number'));
            if (entries.length) choose.append(node('span', `${entries.length} ${entries.length === 1 ? 'sessie' : 'sessies'}`, 'calendar-day-count'));
            choose.addEventListener('click', () => selectDay(date, true));
            choose.addEventListener('keydown', event => {
                const amount = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[event.key];
                if (amount == null) return;
                event.preventDefault(); selectDay(shift(date, amount));
            });
            cell.append(choose);
            entries.slice(0, 2).forEach(session => {
                const event = node('button', `${session.start} ${session.category || session.title}`, 'planning-calendar-event'); event.type = 'button';
                event.setAttribute('aria-label', `${session.title}, ${fullDate(date)}, ${session.start}. Details bekijken`);
                event.addEventListener('click', () => {
                    selected = date; month = monthOf(date); expandedId = session.id; render();
                    $('calendar-day-title').focus();
                });
                cell.append(event);
            });
            if (entries.length > 2) {
                const more = node('button', `+${entries.length - 2} meer`, 'planning-calendar-more'); more.type = 'button';
                more.setAttribute('aria-label', `Alle ${entries.length} sessies op ${fullDate(date)} bekijken`);
                more.addEventListener('click', () => { selectDay(date); $('calendar-day-title').focus(); });
                cell.append(more);
            }
            grid.append(cell);
        }
        $('calendar-day-title').textContent = fullDate(selected);
        $('calendar-day-add').hidden = selected < current;
        $('calendar-new-session').disabled = selected < current;
        club.renderCalendarDay(selected, $('calendar-day-sessions'), expandedId);
    }
    function changeMonth(amount) {
        const date = asDate(month); date.setUTCMonth(date.getUTCMonth() + amount);
        month = iso(date); selected = month === monthOf(today()) ? today() : month; expandedId = null;
        render();
    }
    $('calendar-previous-month').addEventListener('click', () => changeMonth(-1));
    $('calendar-next-month').addEventListener('click', () => changeMonth(1));
    $('calendar-current-month').addEventListener('click', () => { month = monthOf(today()); selected = today(); expandedId = null; render(); });
    [$('calendar-new-session'), $('calendar-day-add')].forEach(button => button.addEventListener('click', () => club.planSession(selected)));
    $('calendar-close').addEventListener('click', () => dialog.close());
    document.addEventListener('cap:session-calendar-open', event => {
        if (!permitted()) return;
        selected = event.detail.date >= today() ? event.detail.date : today(); month = monthOf(selected); expandedId = null;
        if (!dialog.open) dialog.showModal();
        render();
    });
    document.addEventListener('cap:session-editor-opening', () => {
        returnToCalendar = dialog.open;
        if (dialog.open) dialog.close();
    });
    $('session-dialog').addEventListener('close', () => {
        if (returnToCalendar && permitted()) {
            returnToCalendar = false; dialog.showModal(); render();
        }
    });
    dialog.addEventListener('close', () => { if (!returnToCalendar) $('new-session').focus(); });
    document.addEventListener('cap:session-saved', event => {
        returnToCalendar = false;
        selected = event.detail.date; month = monthOf(selected); expandedId = event.detail.id;
        if (dialog.open) dialog.close();
        $('new-session').focus();
    });
    document.addEventListener('cap:club-changed', () => {
        // Follow a saved session when its date is changed in the editor.
        if ($('session-dialog').open) return;
        if (expandedId) {
            const session = club.calendarSessions().find(item => item.id === expandedId);
            if (session) { selected = session.date; month = monthOf(selected); }
        }
        render();
    });
    document.addEventListener('cap:viewchange', () => {
        if (!permitted()) {
            returnToCalendar = false;
            if (dialog.open) dialog.close();
            if ($('session-dialog').open) $('session-dialog').close();
        } else render();
    });
    render();
})();
