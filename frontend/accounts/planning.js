// Shared planning reads only the account-aware API, never browser preview records.
(() => {
    const api = window.capAccount;
    const $ = (id) => document.getElementById(id);
    const today = () =>
        new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const asDate = (date) => new Date(date + 'T12:00:00Z');
    const shift = (date, days) => {
        const result = asDate(date);
        result.setUTCDate(result.getUTCDate() + days);
        return result.toISOString().slice(0, 10);
    };
    let selected = today(),
        month = selected.slice(0, 7) + '-01';
    let sessions = [],
        bookings = [],
        people = [],
        owner = null,
        generation = 0;
    const node = (tag, text, className) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (className) item.className = className;
        return item;
    };
    const coach = () => owner?.role === 'COACH';
    const button = (text, action) => {
        const item = node('button', text, 'secondary-button');
        item.type = 'button';
        item.addEventListener('click', () => api.run(action));
        return item;
    };
    $('real-planning').innerHTML = `
        <div class="account-heading"><h2>Planning</h2><div><button id="real-refresh-planning" type="button" class="secondary-button">Vernieuwen</button><button id="real-add-session" type="button" class="primary-button" hidden>Les toevoegen</button></div></div>
        <p class="sample-notice">Clubtijd (België) · Kies een dag om de lessen te bekijken.</p>
        <div class="planning-calendar-layout"><div class="planning-calendar-month-panel">
            <div class="planning-calendar-toolbar"><h3 id="real-month" aria-live="polite"></h3><div class="planning-calendar-controls"><button id="real-previous" type="button" class="secondary-button" aria-label="Vorige maand"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 6-6 6 6 6"/></svg></button><button id="real-today" type="button" class="secondary-button">Vandaag</button><button id="real-next" type="button" class="secondary-button" aria-label="Volgende maand"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6"/></svg></button></div></div>
            <div class="planning-calendar-weekdays" aria-hidden="true"><span>Ma</span><span>Di</span><span>Wo</span><span>Do</span><span>Vr</span><span>Za</span><span>Zo</span></div><div id="real-calendar" class="planning-calendar-grid" role="group" aria-labelledby="real-month"></div>
        </div><section class="planning-calendar-selected" aria-labelledby="real-day"><h3 id="real-day" tabindex="-1"></h3><div id="real-day-sessions"></div></section></div>
        <section aria-labelledby="real-bookings-title"><h2 id="real-bookings-title">${'Boekingen'}</h2><div id="real-bookings"></div></section>
        <dialog id="real-session-dialog" class="club-dialog" aria-labelledby="real-session-heading"><form id="real-session-form" class="club-form">
            <div class="account-heading"><h2 id="real-session-heading">Les toevoegen</h2><button id="real-close-session" type="button" class="secondary-button" aria-label="Sluiten"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
            <input name="id" type="hidden"><label>Activiteit<select name="kind"><option value="">Andere groepsles</option><option>CAP HYROX</option><option>CAP Hybrid</option><option>CAP Move</option><option>CAP Lift &amp; HIT</option><option>Privé</option><option>Open gym</option></select></label>
            <fieldset id="real-recipients" hidden><legend>Sporters voor deze privésessie</legend><p class="sample-notice">Alleen coaches en geselecteerde sporters zien deze les. Zij worden automatisch ingeschreven.</p><div id="real-recipient-list"></div></fieldset>
            <label>Naam<input name="title" required maxlength="100"></label><div class="club-form-grid"><label>Datum<input name="date" type="date" required></label><label>Start<input name="start" type="time" required></label><label>Einde<input name="end" type="time" required></label><label>Coach<select name="coachId" required></select></label><label>Locatie<input name="location" required maxlength="120" value="CAP Gistel"></label><label>Max. deelnemers<input name="capacity" type="number" min="1" max="200" required value="8"></label><label>Wekelijks herhalen<select name="repeat"><option value="1">Eenmalig</option><option value="4">4 weken</option><option value="8">8 weken</option></select></label></div>
            <label id="real-program-label">Programma<textarea name="program" rows="4" maxlength="3000"></textarea></label><p class="sample-notice">Elke herhaling is een losse les. Bewerken geldt alleen voor deze les.</p><p id="real-session-error" role="alert"></p><button type="submit" class="primary-button">Les bewaren</button>
        </form></dialog>`;
    const form = $('real-session-form');
    function updateKind() {
        const kind = form.elements.kind.value;
        $('real-recipients').hidden = kind !== 'Privé';
        $('real-program-label').hidden = kind === 'Open gym';
    }
    form.elements.kind.addEventListener('change', () => {
        const kind = form.elements.kind.value;
        if (
            !form.elements.title.value ||
            ['CAP HYROX', 'CAP Hybrid', 'CAP Move', 'CAP Lift & HIT', 'Privé', 'Open gym'].includes(
                form.elements.title.value,
            )
        )
            form.elements.title.value = kind;
        form.elements.capacity.value = kind === 'Privé' ? 1 : kind === 'Open gym' ? 7 : 8;
        updateKind();
    });
    function edit(session) {
        form.reset();
        $('real-session-error').textContent = '';
        const value = session || {
            id: '',
            date: selected < today() ? today() : selected,
            start: '18:00',
            end: '19:00',
            coachId: owner.id,
            location: 'CAP Gistel',
            capacity: 8,
            title: '',
            program: '',
        };
        for (const [key, fieldValue] of Object.entries(value))
            if (form.elements.namedItem(key))
                form.elements.namedItem(key).value = String(fieldValue);
        form.elements.date.min = today();
        form.elements.start.value = value.start.slice(0, 5);
        form.elements.end.value = value.end.slice(0, 5);
        form.elements.kind.value = session
            ? session.type === 'Groep'
                ? session.category
                : session.type
            : '';
        form.elements.repeat.disabled = Boolean(session);
        $('real-session-heading').textContent = session ? 'Les bewerken' : 'Les toevoegen';
        $('real-recipient-list').replaceChildren();
        people
            .filter((person) => person.role === 'ATHLETE')
            .forEach((person) => {
                const label = node('label', null, 'real-recipient');
                const input = document.createElement('input');
                input.type = 'checkbox';
                input.value = person.id;
                input.checked = value.athleteIds?.includes(person.id) || false;
                input.addEventListener('change', () => {
                    if (form.elements.kind.value === 'Privé')
                        form.elements.capacity.value = Math.max(
                            1,
                            $('real-recipient-list').querySelectorAll('input:checked').length,
                        );
                });
                label.append(input, document.createTextNode(person.name));
                $('real-recipient-list').append(label);
            });
        if (!people.some((person) => person.role === 'ATHLETE'))
            $('real-recipient-list').append(
                node(
                    'p',
                    'Nodig eerst een sporter uit. Na het aanmaken van hun account kun je hen selecteren.',
                ),
            );
        updateKind();
        $('real-session-dialog').showModal();
    }
    function isPast(session) {
        return (
            `${session.date}T${session.start.slice(0, 5)}` <=
            new Intl.DateTimeFormat('sv-SE', {
                timeZone: 'Europe/Brussels',
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                hourCycle: 'h23',
            })
                .format(new Date())
                .replace(' ', 'T')
        );
    }
    async function mutate(path, method, body) {
        const csrf = await api.request('/csrf');
        await api.request('/api/club' + path, {
            method,
            headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
            body: body == null ? undefined : JSON.stringify(body),
        });
        await refresh();
        api.message('De wijziging is opgeslagen.');
        document.dispatchEvent(new CustomEvent('cap:planning-updated'));
    }
    function render() {
        if (!owner) return;
        $('real-add-session').hidden = !coach();
        $('real-month').textContent = new Intl.DateTimeFormat('nl-BE', {
            month: 'long',
            year: 'numeric',
            timeZone: 'UTC',
        }).format(asDate(month));
        const grid = $('real-calendar');
        grid.replaceChildren();
        const first = shift(month, -(asDate(month).getUTCDay() + 6) % 7);
        for (let index = 0; index < 42; index++) {
            const date = shift(first, index),
                count = sessions.filter((session) => session.date === date).length;
            const cell = node('div', null, 'planning-calendar-day');
            cell.classList.toggle('is-selected', date === selected);
            cell.classList.toggle('is-other-month', date.slice(0, 7) !== month.slice(0, 7));
            const choose = node('button', null, 'calendar-day-select');
            choose.type = 'button';
            choose.dataset.date = date;
            choose.setAttribute(
                'aria-label',
                `${window.capDateTime.dayLabel(date)}: ${count} sessies`,
            );
            choose.setAttribute('aria-pressed', String(date === selected));
            if (date === today()) choose.setAttribute('aria-current', 'date');
            choose.append(node('span', String(asDate(date).getUTCDate()), 'calendar-day-number'));
            if (count)
                choose.append(
                    node(
                        'span',
                        `${count} ${count === 1 ? 'sessie' : 'sessies'}`,
                        'calendar-day-count',
                    ),
                );
            choose.addEventListener('click', () => {
                selected = date;
                month = date.slice(0, 7) + '-01';
                render();
                $('real-day').focus();
            });
            cell.append(choose);
            grid.append(cell);
        }
        $('real-day').textContent = window.capDateTime.dayLabel(selected);
        const list = $('real-day-sessions');
        list.replaceChildren();
        sessions
            .filter((session) => session.date === selected)
            .forEach((session) => {
                const card = node('details', null, 'club-card planning-slot');
                const summary = node(
                    'summary',
                    `${session.start.slice(0, 5)} · ${session.title} · ${session.bookedCount}/${session.capacity}${session.booked ? ' · Geboekt' : ''}`,
                );
                card.append(
                    summary,
                    node(
                        'p',
                        `${session.start.slice(0, 5)} – ${session.end.slice(0, 5)} · ${session.coach} · ${session.location}`,
                    ),
                );
                if (session.type !== 'Open gym')
                    card.append(
                        node(
                            'p',
                            session.program || 'Er is nog geen programma toegevoegd.',
                            'session-program-text',
                        ),
                    );
                if (session.workoutSnapshot)
                    card.append(window.capWorkoutView.render(session.workoutSnapshot.exercises));
                if (!isPast(session)) {
                    if (coach())
                        card.append(
                            button('Bewerken', async () => edit(session)),
                            button('Les verwijderen', async () => {
                                if (confirm('Deze les en de bijbehorende boekingen verwijderen?'))
                                    await mutate('/sessions/' + session.id, 'DELETE');
                            }),
                        );
                    else {
                        const action = button(
                            session.booked
                                ? 'Boeking annuleren'
                                : session.bookedCount >= session.capacity
                                  ? 'Volzet'
                                  : 'Boeken',
                            () =>
                                mutate(
                                    '/sessions/' + session.id + '/booking',
                                    session.booked ? 'DELETE' : 'POST',
                                ),
                        );
                        action.disabled =
                            !session.booked && session.bookedCount >= session.capacity;
                        card.append(action);
                    }
                } else card.append(node('p', 'Afgelopen / gestart', 'sample-notice'));
                list.append(card);
            });
        if (!list.children.length) list.append(node('p', 'Geen lessen op deze dag.', 'club-empty'));
        const bookingList = $('real-bookings');
        bookingList.replaceChildren();
        bookings.forEach((booking) => {
            const session = sessions.find((session) => session.id === booking.sessionId);
            if (!session) return;
            const card = node('details', null, 'club-card');
            card.append(
                node(
                    'summary',
                    `${window.capDateTime.date(session.date)} · ${session.start.slice(0, 5)} · ${session.title}${booking.cancelled ? ' · Geannuleerd' : ''}`,
                ),
            );
            if (coach()) card.append(node('p', booking.athlete));
            if (coach() && !booking.cancelled) {
                const label = node('label', 'Aanwezigheid');
                const select = document.createElement('select');
                ['Onbekend', 'Aanwezig', 'Afwezig'].forEach((status) =>
                    select.add(new Option(status, status)),
                );
                select.value = booking.attendance;
                select.addEventListener('change', () =>
                    api.run(() =>
                        mutate(
                            `/sessions/${session.id}/bookings/${booking.athleteId}/attendance`,
                            'PUT',
                            { status: select.value },
                        ),
                    ),
                );
                label.append(select);
                card.append(label);
                if (!isPast(session))
                    card.append(
                        button('Boeking annuleren', () =>
                            mutate(
                                `/sessions/${session.id}/bookings/${booking.athleteId}`,
                                'DELETE',
                            ),
                        ),
                    );
            }
            if (!coach() && !booking.cancelled && !isPast(session))
                card.append(
                    button('Boeking annuleren', () =>
                        mutate('/sessions/' + session.id + '/booking', 'DELETE'),
                    ),
                );
            bookingList.append(card);
        });
        if (!bookingList.children.length)
            bookingList.append(node('p', 'Nog geen boekingen.', 'club-empty'));
    }
    async function refresh() {
        const ticket = ++generation,
            current = owner?.id;
        const [nextSessions, nextBookings, nextPeople] = await Promise.all([
            api.request('/api/club/sessions'),
            api.request('/api/club/bookings'),
            coach() ? api.request('/api/club/people') : Promise.resolve([]),
        ]);
        if (ticket !== generation || current !== owner?.id) return;
        sessions = nextSessions;
        bookings = nextBookings;
        people = nextPeople;
        form.elements.coachId.replaceChildren();
        people
            .filter((person) => person.role === 'COACH')
            .forEach((person) => form.elements.coachId.add(new Option(person.name, person.id)));
        render();
    }
    $('real-add-session').addEventListener('click', () =>
        api.run(async () => {
            await refresh();
            edit();
        }),
    );
    $('real-refresh-planning').addEventListener('click', () => api.run(refresh));
    window.addEventListener('hashchange', () => {
        if (owner && location.hash === '#planning')
            refresh().catch((error) => api.message(error.message));
    });
    $('real-close-session').addEventListener('click', () => $('real-session-dialog').close());
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        api.run(async () => {
            const values = Object.fromEntries(new FormData(form));
            const privateSession = values.kind === 'Privé';
            const input = {
                ...values,
                type: privateSession ? 'Privé' : values.kind === 'Open gym' ? 'Open gym' : 'Groep',
                category: ['Privé', 'Open gym'].includes(values.kind) ? '' : values.kind,
                capacity: Number(values.capacity),
                repeat: Number(values.repeat || 1),
                athleteIds: privateSession
                    ? Array.from(
                          $('real-recipient-list').querySelectorAll('input:checked'),
                          (input) => input.value,
                      )
                    : [],
            };
            try {
                await mutate(
                    '/sessions' + (values.id ? '/' + values.id : ''),
                    values.id ? 'PUT' : 'POST',
                    input,
                );
            } catch (error) {
                $('real-session-error').textContent = error.message;
                throw error;
            }
            selected = values.date;
            month = selected.slice(0, 7) + '-01';
            render();
            $('real-session-dialog').close();
            $('real-day').focus();
        });
    });
    function changeMonth(amount) {
        const date = asDate(month);
        date.setUTCMonth(date.getUTCMonth() + amount);
        month = date.toISOString().slice(0, 10);
        selected = month;
        render();
    }
    $('real-previous').addEventListener('click', () => changeMonth(-1));
    $('real-next').addEventListener('click', () => changeMonth(1));
    $('real-today').addEventListener('click', () => {
        selected = today();
        month = selected.slice(0, 7) + '-01';
        render();
    });
    document.addEventListener('cap:account-ready', (event) => {
        owner = event.detail;
        generation++;
        sessions = [];
        bookings = [];
        people = [];
        $('real-calendar').replaceChildren();
        $('real-day-sessions').replaceChildren();
        $('real-bookings').replaceChildren();
        $('real-recipient-list').replaceChildren();
        form.elements.coachId.replaceChildren();
        form.reset();
        if ($('real-session-dialog').open) $('real-session-dialog').close();
        if (owner) refresh().catch((error) => api.message(error.message));
    });
})();
