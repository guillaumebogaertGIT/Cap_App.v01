// Local club preview. This is not authentication or a shared booking system.
(() => {
    const storageKey = 'cap-club-preview-v1';
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const shiftDate = (date, amount) => {
        const value = new Date(`${date}T12:00:00Z`);
        value.setUTCDate(value.getUTCDate() + amount);
        return value.toISOString().slice(0, 10);
    };
    const dateLabel = date => window.capDateTime.dayLabel(date);
    const money = (value) => new Intl.NumberFormat('nl-BE', { style: 'currency', currency: 'EUR' }).format(value);
    const uid = () => crypto.randomUUID();
    const seed = {
        sessions: [
            { id: 'demo-group', title: 'CAP circuit', date: shiftDate(today(), 1), start: '18:00', end: '19:00', coach: 'Glenn', location: 'CAP Gistel', capacity: 8, type: 'Groep', cancelled: false },
            { id: 'demo-private', title: 'Personal training', date: shiftDate(today(), 2), start: '10:00', end: '11:00', coach: 'Dieter', location: 'CAP Gistel', capacity: 1, type: 'Privé', cancelled: false }
        ],
        bookings: [],
        products: [
            { id: 'demo-pass', title: 'Groepslessen — 10 beurten', category: 'Beurtenkaart', price: 100, description: 'Voorbeeldpakket voor tien groepslessen. Prijs uitsluitend ter illustratie.', available: true },
            { id: 'demo-member', title: 'CAP maandabonnement', category: 'Abonnement', price: 75, description: 'Voorbeeldabonnement. De coach kan de inhoud en prijs aanpassen.', available: true }
        ]
    };
    let data = seed;
    let storageMessage = 'Lokale preview: voorbeeldgegevens, geen echte boekingen of betalingen. Opgeslagen in deze browser.';
    try {
        const saved = JSON.parse(localStorage.getItem(storageKey));
        if (saved && Array.isArray(saved.sessions) && Array.isArray(saved.bookings) && Array.isArray(saved.products)) data = saved;
    } catch {
        storageMessage = 'Browseropslag niet beschikbaar of onleesbaar. Wijzigingen blijven alleen tijdens deze pagina beschikbaar.';
    }
    // One-time busy schedule preview; preserve existing sessions and user bookings.
    if (!data.busyPlanningPreviewAdded) {
        const slots = [
            ['06:00', 'HYROX ochtend', 'Groep'], ['07:00', 'Strength circuit', 'Groep'],
            ['08:00', 'Functional training', 'Groep'], ['17:00', 'HYROX avond', 'Groep'],
            ['18:00', 'Full body', 'Groep'], ['19:00', 'Conditioning', 'Groep'],
            ...['06:00', '07:00', '09:00', '16:00', '18:00', '20:00'].map(time => [time, 'Personal training', 'Privé']),
            ...['06:00', '08:00', '10:00', '12:00', '17:00', '19:00'].map(time => [time, 'Open gym', 'Open gym'])
        ];
        for (let day = 1; day <= 3; day++) {
            const date = shiftDate(today(), day);
            slots.forEach(([start, title, type], index) => {
                const id = `demo-busy-${date}-${index}`;
                if (data.sessions.some(session => session.id === id)) return;
                const end = `${String(Number(start.slice(0, 2)) + 1).padStart(2, '0')}:00`;
                const capacity = type === 'Open gym' ? 7 : type === 'Privé' ? 1 : 10;
                const count = type === 'Privé' ? index % 2 : index % 3 === 0 ? capacity : Math.max(0, capacity - 2 - day);
                data.sessions.push({ id, title, date, start, end, type, capacity,
                    coach: type === 'Open gym' ? '' : type === 'Privé' ? 'Dieter' : 'Glenn',
                    location: 'CAP Gistel', cancelled: false, preview: true,
                    program: type === 'Open gym' ? '' : 'Voorbeeldprogramma\nOpwarming · 10 minuten\n3 rondes: 12 squats, 10 push-ups, 250 m roeien\nCooling-down · 5 minuten' });
                for (let person = 0; person < count; person++) data.bookings.push({
                    id: `${id}-booking-${person}`, sessionId: id, athlete: `Voorbeeldsporter ${person + 1}`,
                    cancelled: false, attendance: 'Onbekend'
                });
            });
        }
        data.busyPlanningPreviewAdded = true;
        try { localStorage.setItem(storageKey, JSON.stringify(data)); }
        catch { storageMessage = 'Drukke voorbeeldplanning is alleen tijdens deze pagina beschikbaar; browseropslag is niet beschikbaar.'; }
    }
    let week = today();
    let bookingWeek = today();
    // Supply example content for older preview data without replacing saved sessions.
    data.sessions.forEach(session => {
        if (session.preview && session.type === 'Groep' && !session.category) {
            session.category = session.title.startsWith('HYROX') ? 'CAP HYROX'
                : session.title === 'Functional training' ? 'CAP Move'
                : session.title === 'Conditioning' ? 'CAP Hybrid' : 'CAP Lift & HIT';
        }
        if (session.program === undefined) session.program = session.id === 'demo-group'
            ? 'Opwarming · 10 minuten mobiliteit en rustig bewegen\nCircuit · 3 rondes: 12 air squats, 10 push-ups en 250 m roeien\nCooling-down · 5 minuten rustig bewegen en stretchen'
            : '';
    });
    const coach = () => previewRole === 'coach';
    const visibleSession = session => window.capSessionAccess.canView(session, previewRole, 'preview-guillaume');
    const ownBooking = booking => booking.athleteId ? booking.athleteId === 'preview-guillaume' : booking.athlete === 'Guillaume';
    const athletes = Array.from(document.querySelectorAll('#athletes .membership-athlete, #athletes .athlete-card'), item => {
        const name = (item.classList.contains('athlete-card') ? item.querySelector('h3') : item.querySelectorAll('span')[1]).textContent.trim();
        return { name, id: name === 'Guillaume' ? 'preview-guillaume' : 'example-' + name.toLocaleLowerCase('nl-BE').replaceAll(' ', '-') };
    }).sort((a,b) => a.name.localeCompare(b.name, 'nl-BE'));
    const element = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    };
    const button = (text, action, primary = false) => {
        const node = element('button', text, primary ? 'primary-button' : 'secondary-button');
        node.type = 'button';
        node.addEventListener('click', action);
        return node;
    };
    const main = document.querySelector('main');
    main.insertAdjacentHTML('beforeend', `
        <p class="club-preview-note" id="club-storage-note"></p>
        <p class="club-feedback" id="club-feedback" role="status"></p>
        <section id="planning" data-view hidden aria-labelledby="planning-title">
            <header class="dashboard-header"><div><h2 id="planning-title" tabindex="-1">Planning</h2><p class="dashboard-intro">Groepslessen en persoonlijke begeleiding op één plek.</p></div><button type="button" class="primary-button" id="new-session">+ Les toevoegen</button></header>
            <p class="sample-notice">Clubtijd (Belgi&euml;) &middot; Kies een dag om de lessen te bekijken.</p>
        </section>
        <section id="bookings" data-view hidden aria-labelledby="bookings-title">
            <header class="dashboard-header"><div><span class="card-label">Samen aan de slag</span><h2 id="bookings-title" tabindex="-1">Boekingen</h2><p class="dashboard-intro" id="bookings-intro"></p></div></header>
            <div class="club-toolbar"><label>Toon<select id="booking-filter"><option value="upcoming">Aankomend</option><option value="history">Historie en annuleringen</option></select></label><label id="coach-booking-week-label" hidden>Week vanaf<input type="date" id="coach-booking-week"></label></div><div id="booking-list" class="club-grid"></div>
        </section>
        <section id="shop" data-view hidden aria-labelledby="shop-title">
            <header class="dashboard-header"><div><span class="card-label">Meer uit jouw training</span><h2 id="shop-title" tabindex="-1">Shop</h2><p class="dashboard-intro">Abonnementen, beurtenkaarten en clubartikelen.</p></div><button type="button" class="primary-button" id="new-product">+ Product toevoegen</button></header>
            <div class="club-toolbar"><label>Categorie<select id="product-filter"><option value="">Alles</option><option>Abonnement</option><option>Beurtenkaart</option><option>Personal training</option><option>Artikel</option></select></label></div><div id="product-list" class="club-grid"></div>
        </section>
        <dialog id="session-dialog" class="club-dialog" aria-labelledby="session-dialog-title">
            <form id="session-form" class="club-form"><div class="session-dialog-header"><h2 id="session-dialog-title">Les toevoegen</h2><button class="secondary-button session-dialog-close" type="button" data-close="session-dialog" aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div><input name="id" type="hidden">
                <input type="hidden" name="type" value="Groep"><input type="hidden" name="category" value="">
                <label>Activiteit<select id="session-kind"><option value="">Andere groepsles</option><option>CAP HYROX</option><option>CAP Hybrid</option><option>CAP Move</option><option>CAP Lift &amp; HIT</option><option>Privé</option><option>Open gym</option></select></label>
                <fieldset id="session-athletes" hidden><legend>Sporters voor deze privésessie</legend><p class="sample-notice">Alleen de coach en geselecteerde sporters zien deze afspraak. Zij worden automatisch ingeschreven.</p><label>Zoek sporter<input id="session-athlete-search" type="search" placeholder="Naam van de sporter"></label><div id="session-athlete-options"></div><p id="session-athlete-count" role="status"></p></fieldset>
                <label>Naam<input name="title" required maxlength="100"></label>
                <label>Programma voor sporters<textarea name="program" rows="5" maxlength="3000" placeholder="Bijvoorbeeld: opwarming, oefeningen en cooling-down"></textarea></label>
                <p class="sample-notice" id="session-program-help">Sporters kunnen dit programma bekijken voor ze boeken. Na inschrijving verschijnt de sessie ook bij hun workouts.</p>
                <div class="club-form-grid"><label>Datum<input name="date" type="date" required></label><label>Start<input name="start" type="time" required></label><label>Einde<input name="end" type="time" required></label><label>Coach<input name="coach" required maxlength="80"></label><label>Locatie<input name="location" required maxlength="120"></label><label>Max. deelnemers<input name="capacity" type="number" min="1" max="200" required value="8"></label><label>Wekelijks herhalen<select name="repeat"><option value="1">Eenmalig</option><option value="4">4 weken</option><option value="8">8 weken</option></select></label></div>
                <p class="sample-notice">Wekelijkse herhalingen vallen op dezelfde weekdag als de gekozen datum. Elke herhaling is een losse les; bewerken geldt alleen voor die les.</p><p id="session-error" role="alert"></p><div class="editor-actions"><button class="primary-button" type="submit">Les bewaren</button><button class="secondary-button" type="submit" id="session-add-workout">Workout toevoegen</button></div>
                <p class="sample-notice" id="session-workout-help">Workout toevoegen bewaart eerst de les. Daarna kun je een nieuwe workout maken of een opgeslagen workout aanpassen voor deze sessie.</p>
            </form>
        </dialog>
        <dialog id="product-dialog" class="club-dialog" aria-labelledby="product-dialog-title">
            <form id="product-form" class="club-form"><h2 id="product-dialog-title">Product toevoegen</h2><input name="id" type="hidden"><label>Naam<input name="title" required maxlength="100"></label><label>Beschrijving<textarea name="description" rows="3" required maxlength="1000"></textarea></label><div class="club-form-grid"><label>Categorie<select name="category"><option>Abonnement</option><option>Beurtenkaart</option><option>Personal training</option><option>Artikel</option></select></label><label>Prijs (€)<input name="price" type="number" required min="0" max="100000" step="0.01"></label><label>Zichtbaarheid<select name="available"><option value="true">Beschikbaar</option><option value="false">Verborgen voor sporters</option></select></label></div><div class="editor-actions"><button class="primary-button" type="submit">Product bewaren</button><button class="secondary-button" type="button" data-close="product-dialog">Sluiten</button></div></form>
        </dialog>`);
    const $ = (id) => document.getElementById(id);
    $('planning').append($('planning-calendar'));
    const sessionWorkouts = element('section');
    sessionWorkouts.id = 'booked-session-workouts';
    sessionWorkouts.setAttribute('aria-labelledby', 'booked-session-workouts-title');
    $('workout-library-panel').before(sessionWorkouts);
    let visibleSessionLimit = 6;
    document.querySelector('.welcome-bar').after($('club-storage-note'), $('club-feedback'));
    $('club-storage-note').textContent = storageMessage;
    const announce = (message) => { $('club-feedback').textContent = message; };
    function save(message) {
        try { localStorage.setItem(storageKey, JSON.stringify(data)); }
        catch { $('club-storage-note').textContent = 'Opslaan in deze browser is mislukt. Wijzigingen verdwijnen bij vernieuwen.'; }
        render();
        announce(message);
    }
    const bookingsFor = (session) => data.bookings.filter((booking) => booking.sessionId === session.id && !booking.cancelled);
    const bookingFeature = session => session.type === 'Open gym' ? 'openGym' : session.type === 'Groep' ? 'group' : 'private';
    const past = (session) => `${session.date}T${session.start}` <= new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date()).replace(' ', 'T');
    function programPreview(session) {
        const preview = element('details', undefined, 'saved-workout session-program');
        preview.append(element('summary', 'Bekijk sessieprogramma'));
        if (session.workoutSnapshot) preview.append(window.capWorkoutView.render(session.workoutSnapshot.exercises));
        else preview.append(element('p', session.program?.trim() || 'De coach heeft het programma nog niet toegevoegd.', 'session-program-text'));
        return preview;
    }
    function renderSessionWorkouts() {
        sessionWorkouts.replaceChildren();
        sessionWorkouts.hidden = coach();
        if (coach()) return;
        const heading = element('h3', 'Mijn geboekte sessies');
        heading.id = 'booked-session-workouts-title';
        sessionWorkouts.append(heading);
        const sessions = data.sessions.filter(session => visibleSession(session) && session.type !== 'Open gym' && !session.cancelled && !past({ ...session, start: session.end })
            && bookingsFor(session).some(ownBooking))
            .sort((a, b) => `${a.date}${a.start}`.localeCompare(`${b.date}${b.start}`));
        const list = element('div', undefined, 'compact-session-list');
        sessions.slice(0, visibleSessionLimit).forEach(session => {
            const card = element('details', undefined, 'compact-session');
            const summary = element('summary');
            summary.append(element('span', `${dateLabel(session.date)} · ${session.start}`, 'card-label'), element('strong', session.title));
            card.append(summary);
            card.append(element('span', `${dateLabel(session.date)} · ${session.start} – ${session.end}`, 'card-label'),
                element('h3', session.title), element('p', `${session.coach} · ${session.location}`), programPreview(session));
            const link = element('a', 'Bekijk boekingen', 'secondary-button');
            link.href = '#bookings'; card.append(link); list.append(card);
        });
        if (!sessions.length) list.append(element('p', 'Nog geen komende sessies geboekt. Bekijk eerst het programma in Planning en schrijf je in.', 'club-empty'));
        sessionWorkouts.append(list);
        if (sessions.length > visibleSessionLimit) {
            sessionWorkouts.append(button(`Meer sessies tonen (${sessions.length - visibleSessionLimit})`, () => { visibleSessionLimit += 6; renderSessionWorkouts(); }));
        }
    }
    function sessionCard(session) {
        const card = element('details', undefined, 'club-card planning-slot');
        const active = bookingsFor(session);
        const summary = element('summary');
        const title = element('span', undefined, 'slot-title');
        title.append(element('strong', session.title), element('small', session.category || session.type));
        if (session.preview) title.lastChild.textContent += ' · Voorbeeld';
        const booked = active.some(ownBooking);
        const capacity = element('span', `${booked && !coach() ? '✓ ' : ''}${active.length}/${session.capacity}`, 'slot-capacity');
        capacity.setAttribute('aria-label', `${active.length} van ${session.capacity} plaatsen geboekt`);
        summary.append(element('span', session.start, 'slot-time'), title, capacity);
        card.append(summary, element('p', `${session.start} – ${session.end} · ${[session.coach, session.location].filter(Boolean).join(' · ')}`));
        const actions = element('div', undefined, 'club-actions');
        if (session.type !== 'Open gym') card.append(programPreview(session));
        else card.append(element('p', 'Vrij trainen. Je reserveert een plek; je persoonlijke workout kies je zelf.', 'sample-notice'));
        if (coach()) {
            actions.append(button('Bewerken', () => openSession(session)));
            actions.append(button('Les verwijderen', () => {
                if (!window.confirm(`Weet je zeker dat je de sessie "${session.title}" op ${dateLabel(session.date)} om ${session.start} wilt verwijderen? Bijbehorende boekingen worden ook verwijderd.`)) return;
                data.sessions = data.sessions.filter(item => item.id !== session.id);
                data.bookings = data.bookings.filter(booking => booking.sessionId !== session.id);
                save('Les en bijbehorende boekingen verwijderd uit de planning.');
                $('calendar-day-title').focus();
            }));
        } else {
            const booked = active.some(ownBooking);
            const permitted = window.capAccess.can(bookingFeature(session));
            const unavailable = session.cancelled || past(session) || (!booked && active.length >= session.capacity);
            const control = button(booked ? 'Boeking annuleren' : session.cancelled ? 'Geannuleerd' : past(session) ? 'Afgelopen / gestart' : unavailable ? 'Volzet' : 'Boeken', () => {
                if (!visibleSession(session)) return;
                if (!window.capAccess.signedIn || (!booked && !window.capAccess.can(bookingFeature(session)))) {
                    announce('Voor deze sessie is een geschikt abonnement nodig. Bekijk de voorbeeldrechten bovenaan.');
                    return;
                }
                if (booked) active.filter(ownBooking).forEach((booking) => { booking.cancelled = true; });
                else if (!session.cancelled && !past(session) && bookingsFor(session).length < session.capacity) data.bookings.push({ id: uid(), sessionId: session.id, athleteId: 'preview-guillaume', athlete: 'Guillaume', cancelled: false, attendance: 'Onbekend' });
                save(booked ? 'Boeking geannuleerd.' : session.type === 'Open gym' ? 'Open gym geboekt. Bekijk Boekingen voor je gereserveerde plek.' : 'Geboekt voor Guillaume in de preview. De sessie staat nu ook bij Workouts.');
            }, !booked);
            control.disabled = unavailable;
            if (!booked && !unavailable && !permitted) control.textContent = 'Abonnement nodig';
            actions.append(control);
        }
        card.append(actions);
        return card;
    }
    function renderPlanning() {
        $('new-session').hidden = !coach();
    }
    function renderCoachBookings(list, openRows, focusKey) {
        list.className = 'coach-booking-days';
        const history = $('booking-filter').value === 'history';
        for (let offset = 0; offset < 7; offset++) {
            const date = shiftDate(bookingWeek, offset);
            const sessions = data.sessions.filter(session => session.date === date && (history
                ? session.cancelled || past({ ...session, start: session.end }) || data.bookings.some(b => b.sessionId === session.id && b.cancelled)
                : !session.cancelled && !past({ ...session, start: session.end })))
                .sort((a, b) => a.start.localeCompare(b.start) || a.title.localeCompare(b.title));
            const day = element('details', undefined, 'coach-booking-day');
            day.dataset.bookingKey = date; day.open = openRows.has(date);
            const total = sessions.reduce((sum, session) => sum + bookingsFor(session).length, 0);
            day.append(element('summary', `${dateLabel(date)} · ${sessions.length} sessies · ${total} boekingen`));
            if (!sessions.length) day.append(element('p', 'Geen sessies in dit overzicht.', 'sample-notice'));
            sessions.forEach(session => {
                const item = element('details', undefined, 'coach-booking-session');
                item.dataset.bookingKey = session.id; item.open = openRows.has(session.id);
                const participants = data.bookings.filter(b => b.sessionId === session.id && (history || !b.cancelled));
                item.append(element('summary', `${session.start} · ${session.title} · ${bookingsFor(session).length}/${session.capacity}${session.cancelled ? ' · Geannuleerd' : ''}`));
                item.append(element('p', `${session.start} – ${session.end} · ${session.type} · ${session.location}`, 'sample-notice'));
                if (!participants.length) item.append(element('p', 'Nog geen deelnemers.', 'sample-notice'));
                participants.sort((a, b) => a.athlete.localeCompare(b.athlete, 'nl-BE')).forEach(booking => {
                    const row = element('div', undefined, 'coach-booking-person');
                    row.append(element('span', `${booking.athlete}${booking.cancelled || session.cancelled ? ' · Geannuleerd' : ''}`));
                    if (!booking.cancelled && !session.cancelled) {
                        const attendance = document.createElement('select');
                        attendance.dataset.bookingControl = booking.id;
                        attendance.setAttribute('aria-label', `Aanwezigheid ${booking.athlete}`);
                        ['Onbekend', 'Aanwezig', 'Afwezig'].forEach(status => attendance.add(new Option(status, status)));
                        attendance.value = booking.attendance;
                        attendance.addEventListener('change', () => { booking.attendance = attendance.value; save('Aanwezigheid bijgewerkt.'); });
                        row.append(attendance);
                        if (!past(session)) row.append(button('Annuleren', () => { booking.cancelled = true; save('Boeking geannuleerd.'); }));
                    }
                    item.append(row);
                });
                day.append(item);
            });
            list.append(day);
        }
        if (focusKey) Array.from(list.querySelectorAll('[data-booking-control]')).find(input => input.dataset.bookingControl === focusKey)?.focus();
    }
    function renderBookings() {
        $('bookings-intro').textContent = coach() ? 'Beheer deelnemers en houd hun aanwezigheid bij.' : 'Jouw gereserveerde trainingen en geschiedenis.';
        const list = $('booking-list');
        const openRows = new Set(Array.from(list.querySelectorAll('details[open]'), row => row.dataset.bookingKey));
        const focusKey = document.activeElement?.dataset.bookingControl;
        list.replaceChildren();
        $('coach-booking-week-label').hidden = !coach();
        $('coach-booking-week').value = bookingWeek;
        if (coach()) { renderCoachBookings(list, openRows, focusKey); return; }
        list.className = 'club-grid';
        data.bookings.forEach((booking) => {
            if (!ownBooking(booking)) return;
            const session = data.sessions.find((item) => item.id === booking.sessionId);
            if (!session || !visibleSession(session)) return;
            const history = booking.cancelled || session.cancelled || past(session);
            if (history !== ($('booking-filter').value === 'history')) return;
            const card = element('article', undefined, 'club-card');
            card.append(element('span', dateLabel(session.date), 'card-label'), element('h3', session.title), element('p', `${session.start} – ${session.end} · ${session.location}`), element('p', `${booking.athlete} · ${booking.cancelled || session.cancelled ? 'Geannuleerd' : 'Geboekt'}`));
            if (session.type !== 'Open gym') card.append(programPreview(session));
            if (coach() && !booking.cancelled && !session.cancelled) {
                const label = element('label', 'Aanwezigheid');
                const select = document.createElement('select');
                ['Onbekend', 'Aanwezig', 'Afwezig'].forEach((status) => select.add(new Option(status, status)));
                select.value = booking.attendance;
                select.addEventListener('change', () => { booking.attendance = select.value; save('Aanwezigheid bijgewerkt.'); });
                label.append(select); card.append(label);
            }
            if (!history) card.append(button('Boeking annuleren', () => { booking.cancelled = true; save('Boeking geannuleerd.'); }));
            list.append(card);
        });
        if (!list.children.length) list.append(element('p', 'Nog geen boekingen in dit overzicht. Reserveer een les via Planning.', 'club-empty'));
    }
    function renderShop() {
        $('new-product').hidden = !coach();
        const list = $('product-list'); list.replaceChildren();
        data.products.filter((product) => (coach() || product.available) && (!$('product-filter').value || product.category === $('product-filter').value)).forEach((product) => {
            const card = element('article', undefined, 'club-card');
            card.append(element('span', product.category, 'card-label'), element('h3', product.title), element('p', product.description), element('strong', money(product.price), 'club-price'));
            if (coach()) {
                card.append(element('p', product.available ? 'Beschikbaar voor sporters' : 'Verborgen voor sporters'), button('Product bewerken', () => openProduct(product)));
            } else card.append(button('Bekijk aanbod', () => {
                announce(`${product.title}: ${money(product.price)}. Dit is een voorbeeldproduct; aankopen en betalen zijn nog niet beschikbaar.`);
            }));
            list.append(card);
        });
        if (!list.children.length) list.append(element('p', 'Geen producten in deze categorie.', 'club-empty'));
    }
    function renderDashboard() {
        const upcoming = data.sessions.filter((session) => visibleSession(session) && !session.cancelled && !past(session))
            .sort((a, b) => `${a.date}${a.start}`.localeCompare(`${b.date}${b.start}`));
        const reserved = upcoming.filter((session) => bookingsFor(session).some(ownBooking));
        $('home-planning-meta').textContent = reserved.length ? `${reserved.length} komende boeking(en)` : 'Bekijk het rooster';
    }
    function render() {
        renderPlanning(); renderBookings(); renderShop(); renderDashboard(); renderSessionWorkouts();
        document.dispatchEvent(new CustomEvent('cap:club-changed'));
    }
    function updateSessionType() {
        const form = $('session-form');
        const openGym = form.elements.namedItem('type').value === 'Open gym';
        $('session-athletes').hidden = form.elements.namedItem('type').value !== 'Privé';
        const program = form.elements.namedItem('program');
        program.disabled = openGym;
        program.closest('label').hidden = openGym;
        $('session-add-workout').hidden = openGym;
        $('session-program-help').hidden = openGym;
        $('session-workout-help').hidden = openGym;
        const coachInput = form.elements.namedItem('coach');
        coachInput.required = !openGym;
        coachInput.disabled = openGym;
        coachInput.closest('label').hidden = openGym;
    }
    const selectedAthletes = () => Array.from($('session-athlete-options').querySelectorAll('input:checked'), input => input.value);
    function updateAthleteCount() {
        const count = selectedAthletes().length;
        $('session-athlete-count').textContent = count + ' sporter(s) geselecteerd';
        if ($('session-form').elements.type.value === 'Privé') $('session-form').elements.capacity.value = Math.max(1, count);
    }
    athletes.forEach(athlete => {
        const label = element('label', undefined, 'session-athlete-option');
        const input = document.createElement('input'); input.type = 'checkbox'; input.value = athlete.id;
        label.append(input, document.createTextNode(athlete.name));
        $('session-athlete-options').append(label); input.addEventListener('change', updateAthleteCount);
    });
    $('session-athlete-search').addEventListener('input', event => {
        const query = event.target.value.trim().toLocaleLowerCase('nl-BE');
        Array.from($('session-athlete-options').children).forEach(label => { label.hidden = !label.textContent.toLocaleLowerCase('nl-BE').includes(query); });
    });
    $('session-kind').addEventListener('change', () => {
        const form = $('session-form'); const choice = $('session-kind').value;
        const previous = form.elements.category.value || (form.elements.type.value === 'Groep' ? '' : form.elements.type.value);
        if (!form.elements.title.value.trim() || form.elements.title.value === previous) form.elements.title.value = choice;
        form.elements.type.value = ['Privé', 'Open gym'].includes(choice) ? choice : 'Groep';
        form.elements.category.value = form.elements.type.value === 'Groep' ? choice : '';
        if (!form.elements.namedItem('id').value) form.elements.capacity.value = choice === 'Privé' ? Math.max(1, selectedAthletes().length) : choice === 'Open gym' ? 7 : 8;
        updateSessionType();
    });
    function openSession(session, selectedDate) {
        if (!coach()) return;
        const form = $('session-form'); form.reset();
        form.elements.namedItem('date').min = today();
        const values = session || { id: '', date: selectedDate || week, start: '18:00', end: '19:00', coach: 'Glenn', location: 'CAP Gistel', capacity: 8, type: 'Groep' };
        Object.entries(values).forEach(([name, value]) => { if (form.elements.namedItem(name)) form.elements.namedItem(name).value = value; });
        $('session-kind').value = values.type === 'Groep' ? values.category || '' : values.type;
        $('session-athlete-search').value = '';
        Array.from($('session-athlete-options').children).forEach(label => {
            label.hidden = false;
            label.querySelector('input').checked = (session?.athleteIds || []).includes(label.querySelector('input').value);
        });
        $('session-athlete-count').textContent = selectedAthletes().length + ' sporter(s) geselecteerd';
        form.elements.repeat.disabled = Boolean(session);
        updateSessionType();
        $('session-error').textContent = '';
        $('session-dialog-title').textContent = session ? 'Les bewerken' : 'Les toevoegen';
        $('session-dialog').showModal();
    }
    function openProduct(product) {
        if (!coach()) return;
        const form = $('product-form'); form.reset();
        form.elements.id.value = '';
        if (product) Object.entries(product).forEach(([name, value]) => { if (form.elements.namedItem(name)) form.elements.namedItem(name).value = String(value); });
        $('product-dialog-title').textContent = product ? 'Product bewerken' : 'Product toevoegen';
        $('product-dialog').showModal();
    }
    // Trim text inputs as well as using native required/date/number validation.
    document.querySelectorAll('.club-form').forEach((form) => {
        form.addEventListener('input', (event) => event.target.setCustomValidity(''));
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!coach()) return;
            form.querySelectorAll('input:not([type])[required], textarea[required]').forEach((input) => input.setCustomValidity(input.value.trim() ? '' : 'Vul dit veld in.'));
            if (!form.reportValidity()) return;
            const values = Object.fromEntries(new FormData(form));
            Object.keys(values).forEach((key) => { values[key] = values[key].trim(); });
            // A control named "id" shadows form.id; read the HTML attribute instead.
            if (form.getAttribute('id') === 'session-form') {
                const existing = data.sessions.find((session) => session.id === values.id);
                if (values.date < today()) { $('session-error').textContent = 'Kies vandaag of een toekomstige datum.'; return; }
                if (values.end <= values.start) { $('session-error').textContent = 'De eindtijd moet na de starttijd liggen.'; return; }
                const athleteIds = values.type === 'Privé' ? selectedAthletes() : [];
                if (values.type === 'Privé' && !athleteIds.length) { $('session-error').textContent = 'Selecteer minstens een sporter voor deze privésessie.'; $('session-athlete-search').focus(); return; }
                if (Number(values.capacity) < athleteIds.length) { $('session-error').textContent = 'Voorzie een plaats voor elke geselecteerde sporter.'; return; }
                if (existing && values.type !== 'Privé' && Number(values.capacity) < bookingsFor(existing).length) { $('session-error').textContent = 'De capaciteit kan niet lager zijn dan het aantal boekingen.'; return; }
                const session = { ...values, athleteIds, capacity: Number(values.capacity), cancelled: existing?.cancelled || false };
                session.workoutSnapshot = existing?.program === values.program ? existing?.workoutSnapshot || null : null;
                if (values.type !== 'Groep') session.category = '';
                if (values.type === 'Open gym') { session.coach = ''; session.program = ''; }
                if (values.type === 'Open gym') session.workoutSnapshot = null;
                delete session.repeat;
                const targetIds = [];
                if (existing) { Object.assign(existing, session); targetIds.push(existing.id); }
                else for (let index = 0; index < Number(values.repeat); index++) {
                    const created = { ...session, id: uid(), date: shiftDate(values.date, index * 7) };
                    data.sessions.push(created); targetIds.push(created.id);
                }
                targetIds.forEach(sessionId => {
                    if (values.type !== 'Privé') return;
                    data.bookings = data.bookings.filter(booking => booking.sessionId !== sessionId || athleteIds.includes(booking.athleteId));
                    athleteIds.forEach(athleteId => {
                        const booking = data.bookings.find(item => item.sessionId === sessionId && item.athleteId === athleteId);
                        if (!booking) data.bookings.push({ id: uid(), sessionId, athleteId, athlete: athletes.find(item => item.id === athleteId).name, cancelled: false, attendance: 'Onbekend' });
                    });
                });
                week = values.date;
                $('session-dialog').close(); save(values.type === 'Privé' ? 'Privésessie bewaard voor de geselecteerde sporters.' : 'Les bewaard. De planning is bijgewerkt.');
                document.dispatchEvent(new CustomEvent('cap:session-saved', { detail: { date: values.date, id: targetIds[0] } }));
                if (event.submitter?.id === 'session-add-workout') {
                    location.hash = 'create-workout';
                    document.dispatchEvent(new CustomEvent('cap:workout-for-sessions', { detail: targetIds }));
                }
            } else {
                const product = { ...values, price: Number(values.price), available: values.available === 'true' };
                const existing = data.products.find((item) => item.id === product.id);
                if (existing) Object.assign(existing, product); else data.products.push({ ...product, id: uid() });
                $('product-dialog').close(); save('Product bewaard. Het aanbod voor sporters is bijgewerkt.');
            }
        });
    });
    document.querySelectorAll('[data-close]').forEach((control) => control.addEventListener('click', () => $(control.dataset.close).close()));
    $('coach-booking-week').addEventListener('change', event => {
        if (event.target.value && event.target.validity.valid) bookingWeek = event.target.value;
        renderBookings();
    });
    $('booking-filter').addEventListener('change', () => { bookingWeek = $('booking-filter').value === 'history' ? shiftDate(today(), -6) : today(); });
    $('new-product').addEventListener('click', () => openProduct());
    ['booking-filter', 'product-filter'].forEach((id) => $(id).addEventListener('change', render));
    document.addEventListener('cap:viewchange', render);
    window.capClub = {
        calendarSessions() {
            return data.sessions.filter(session => visibleSession(session) && !session.cancelled)
                .map(({ id, title, date, start, end, coach, category, type }) => ({ id, title, date, start, end, coach, category, type }));
        },
        planSession(date) {
            if (coach() && date >= today()) openSession(undefined, date);
        },
        renderCalendarDay(date, container, expandedId) {
            container.replaceChildren();
            const sessions = data.sessions.filter(session => visibleSession(session) && session.date === date && !session.cancelled)
                .sort((a, b) => a.start.localeCompare(b.start));
            sessions.forEach(session => {
                const card = sessionCard(session);
                card.open = session.id === expandedId;
                if (past(session)) {
                    card.querySelector('.club-actions')?.remove();
                    card.append(element('p', 'Afgelopen / gestart · Historiek blijft behouden.', 'sample-notice'));
                }
                container.append(card);
            });
            if (!sessions.length) container.append(element('p', 'Geen sessies op deze dag.', 'club-empty'));
        },
        sessions() {
            return data.sessions.filter(session => session.type !== 'Open gym' && visibleSession(session) && !session.cancelled && !past(session))
                .sort((a, b) => `${a.date}${a.start}`.localeCompare(`${b.date}${b.start}`))
                .map(session => ({ id: session.id, title: session.title, date: session.date, start: session.start, coach: session.coach, category: session.category || '' }));
        },
        assignWorkout(ids, workout) {
            if (!coach() || !ids.length || ids.some(id => !this.sessions().some(session => session.id === id))) {
                throw new Error('Kies opnieuw een beschikbare sessie in de planning.');
            }
            const program = [workout.name, workout.description, ...workout.exercises.map(exercise =>
                `${exercise.name}${exercise.setPlan?.length ? ' · ' + exercise.setPlan.map((set, i) => 'Set ' + (i + 1) + ': ' + window.capWorkoutView.targetText(set)).join(' / ') : ' · ' + exercise.sets + ' sets × ' + exercise.reps + ' herhalingen' + (exercise.targetKg != null ? ' · ' + exercise.targetKg + ' kg' : '')}${exercise.restSeconds != null ? ` · Rust: ${exercise.restSeconds} s` : ''}${exercise.tempo ? ` · Tempo: ${exercise.tempo}` : ''}${exercise.supersetGroup ? ` · Superset ${exercise.supersetGroup}` : ''}`)]
                .filter(Boolean).join('\n');
            const updated = { ...data, sessions: data.sessions.map(session => ids.includes(session.id) ? { ...session, program, workoutSnapshot: structuredClone(workout) } : session) };
            // Persist before changing the screen so failed storage does not look like a saved assignment.
            localStorage.setItem(storageKey, JSON.stringify(updated));
            data = updated;
            render();
        }
    };
    showCurrentView(false);
})();
