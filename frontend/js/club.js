// Local club preview. This is not authentication or a shared booking system.
(() => {
    const storageKey = 'cap-club-preview-v1';
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const shiftDate = (date, amount) => {
        const value = new Date(`${date}T12:00:00Z`);
        value.setUTCDate(value.getUTCDate() + amount);
        return value.toISOString().slice(0, 10);
    };
    const dateLabel = (date) => new Intl.DateTimeFormat('nl-BE', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));
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
    let week = today();
    let layout = 'calendar';
    const coach = () => previewRole === 'coach';
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
            <header class="dashboard-header"><div><span class="card-label">Jouw club, jouw ritme</span><h2 id="planning-title" tabindex="-1">Planning</h2><p class="dashboard-intro">Groepslessen en persoonlijke begeleiding op één plek.</p></div><button type="button" class="primary-button" id="new-session">+ Les toevoegen</button></header>
            <div class="club-toolbar">
                <button type="button" class="secondary-button" id="previous-week" aria-label="Vorige week">←</button><label>Week vanaf<input id="week-date" type="date" required></label><button type="button" class="secondary-button" id="next-week" aria-label="Volgende week">→</button>
                <button type="button" class="secondary-button" id="current-week">Vandaag</button>
                <label>Activiteit<select id="session-filter"><option value="">Alle activiteiten</option><option>Groep</option><option>Privé</option></select></label>
                <label>Trainer<select id="coach-filter"><option value="">Alle trainers</option></select></label>
                <button type="button" class="secondary-button" id="layout-toggle" aria-pressed="true">Weekoverzicht</button>
            </div>
            <p class="sample-notice">Alle tijden zijn lokale clubtijden (België). Op je telefoon verschijnen de dagen onder elkaar.</p>
            <div id="session-list"></div>
        </section>
        <section id="bookings" data-view hidden aria-labelledby="bookings-title">
            <header class="dashboard-header"><div><span class="card-label">Samen aan de slag</span><h2 id="bookings-title" tabindex="-1">Boekingen</h2><p class="dashboard-intro" id="bookings-intro"></p></div></header>
            <div class="club-toolbar"><label>Toon<select id="booking-filter"><option value="upcoming">Aankomend</option><option value="history">Historie en annuleringen</option></select></label></div><div id="booking-list" class="club-grid"></div>
        </section>
        <section id="shop" data-view hidden aria-labelledby="shop-title">
            <header class="dashboard-header"><div><span class="card-label">Meer uit jouw training</span><h2 id="shop-title" tabindex="-1">Shop</h2><p class="dashboard-intro">Abonnementen, beurtenkaarten en clubartikelen.</p></div><button type="button" class="primary-button" id="new-product">+ Product toevoegen</button></header>
            <div class="club-toolbar"><label>Categorie<select id="product-filter"><option value="">Alles</option><option>Abonnement</option><option>Beurtenkaart</option><option>Personal training</option><option>Artikel</option></select></label></div><div id="product-list" class="club-grid"></div>
        </section>
        <dialog id="session-dialog" class="club-dialog" aria-labelledby="session-dialog-title">
            <form id="session-form" class="club-form"><h2 id="session-dialog-title">Les toevoegen</h2><input name="id" type="hidden">
                <label>Naam<input name="title" required maxlength="100"></label>
                <div class="club-form-grid"><label>Datum<input name="date" type="date" required></label><label>Type<select name="type"><option>Groep</option><option>Privé</option></select></label><label>Start<input name="start" type="time" required></label><label>Einde<input name="end" type="time" required></label><label>Coach<input name="coach" required maxlength="80"></label><label>Locatie<input name="location" required maxlength="120"></label><label>Max. deelnemers<input name="capacity" type="number" min="1" max="200" required value="8"></label><label>Wekelijks herhalen<select name="repeat"><option value="1">Eenmalig</option><option value="4">4 weken</option><option value="8">8 weken</option></select></label></div>
                <p class="sample-notice">Herhalingen worden losse lessen. Wijzigingen gelden alleen voor de gekozen les.</p><p id="session-error" role="alert"></p><div class="editor-actions"><button class="primary-button" type="submit">Les bewaren</button><button class="secondary-button" type="button" data-close="session-dialog">Sluiten</button></div>
            </form>
        </dialog>
        <dialog id="product-dialog" class="club-dialog" aria-labelledby="product-dialog-title">
            <form id="product-form" class="club-form"><h2 id="product-dialog-title">Product toevoegen</h2><input name="id" type="hidden"><label>Naam<input name="title" required maxlength="100"></label><label>Beschrijving<textarea name="description" rows="3" required maxlength="1000"></textarea></label><div class="club-form-grid"><label>Categorie<select name="category"><option>Abonnement</option><option>Beurtenkaart</option><option>Personal training</option><option>Artikel</option></select></label><label>Prijs (€)<input name="price" type="number" required min="0" max="100000" step="0.01"></label><label>Zichtbaarheid<select name="available"><option value="true">Beschikbaar</option><option value="false">Verborgen voor sporters</option></select></label></div><div class="editor-actions"><button class="primary-button" type="submit">Product bewaren</button><button class="secondary-button" type="button" data-close="product-dialog">Sluiten</button></div></form>
        </dialog>`);
    const $ = (id) => document.getElementById(id);
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
    const past = (session) => `${session.date}T${session.start}` <= new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date()).replace(' ', 'T');
    function sessionCard(session) {
        const card = element('article', undefined, 'club-card');
        card.append(element('span', `${session.start} – ${session.end} · ${session.type}`, 'card-label'), element('h3', session.title), element('p', `${session.coach} · ${session.location}`));
        const active = bookingsFor(session);
        card.append(element('span', session.cancelled ? 'Geannuleerd' : `${active.length} / ${session.capacity} deelnemers`, 'status-label'));
        const actions = element('div', undefined, 'club-actions');
        if (coach()) {
            actions.append(button('Bewerken', () => openSession(session)));
            actions.append(button(session.cancelled ? 'Herstellen' : 'Les annuleren', () => {
                session.cancelled = !session.cancelled;
                if (session.cancelled) active.forEach((booking) => { booking.cancelled = true; });
                save(session.cancelled ? 'Les geannuleerd. Boekingen zijn geannuleerd.' : 'Les hersteld. Sporters kunnen opnieuw boeken.');
            }));
        } else {
            const booked = active.some((booking) => booking.athlete === 'Guillaume');
            const permitted = window.capAccess.can(session.type === 'Groep' ? 'group' : 'private');
            const unavailable = session.cancelled || past(session) || (!booked && active.length >= session.capacity);
            const control = button(booked ? 'Boeking annuleren' : session.cancelled ? 'Geannuleerd' : past(session) ? 'Afgelopen / gestart' : unavailable ? 'Volzet' : 'Boeken', () => {
                if (!window.capAccess.signedIn || (!booked && !window.capAccess.can(session.type === 'Groep' ? 'group' : 'private'))) {
                    announce('Voor deze sessie is een geschikt abonnement nodig. Bekijk de voorbeeldrechten bovenaan.');
                    return;
                }
                if (booked) active.filter((booking) => booking.athlete === 'Guillaume').forEach((booking) => { booking.cancelled = true; });
                else if (!session.cancelled && !past(session) && bookingsFor(session).length < session.capacity) data.bookings.push({ id: uid(), sessionId: session.id, athlete: 'Guillaume', cancelled: false, attendance: 'Onbekend' });
                save(booked ? 'Boeking geannuleerd in de preview.' : 'Geboekt voor Guillaume in de preview. Bekijk Boekingen.');
            }, !booked);
            control.disabled = unavailable;
            if (!booked && !unavailable && !permitted) control.textContent = 'Abonnement nodig';
            actions.append(control);
        }
        card.append(actions);
        return card;
    }
    function renderPlanning() {
        $('week-date').value = week;
        $('new-session').hidden = !coach();
        const selectedCoach = $('coach-filter').value;
        $('coach-filter').replaceChildren(new Option('Alle trainers', ''));
        [...new Set(data.sessions.map((session) => session.coach))].sort().forEach((name) => $('coach-filter').add(new Option(name, name)));
        $('coach-filter').value = [...$('coach-filter').options].some((option) => option.value === selectedCoach) ? selectedCoach : '';
        const container = $('session-list');
        container.className = layout === 'calendar' ? 'club-calendar' : 'club-agenda';
        container.replaceChildren();
        for (let day = 0; day < 7; day++) {
            const date = shiftDate(week, day);
            const column = element('section', undefined, 'club-day');
            column.append(element('h3', dateLabel(date), date === today() ? 'club-today' : ''));
            const sessions = data.sessions.filter((session) => session.date === date && (!$('session-filter').value || session.type === $('session-filter').value) && (!$('coach-filter').value || session.coach === $('coach-filter').value)).sort((a, b) => a.start.localeCompare(b.start));
            if (!sessions.length) column.append(element('p', 'Geen lessen', 'sample-notice'));
            sessions.forEach((session) => column.append(sessionCard(session)));
            container.append(column);
        }
    }
    function renderBookings() {
        $('bookings-intro').textContent = coach() ? 'Beheer deelnemers en houd hun aanwezigheid bij.' : 'Jouw gereserveerde trainingen en geschiedenis.';
        const list = $('booking-list');
        list.replaceChildren();
        data.bookings.forEach((booking) => {
            const session = data.sessions.find((item) => item.id === booking.sessionId);
            if (!session) return;
            const history = booking.cancelled || session.cancelled || past(session);
            if (history !== ($('booking-filter').value === 'history')) return;
            const card = element('article', undefined, 'club-card');
            card.append(element('span', dateLabel(session.date), 'card-label'), element('h3', session.title), element('p', `${session.start} – ${session.end} · ${session.location}`), element('p', `${booking.athlete} · ${booking.cancelled || session.cancelled ? 'Geannuleerd' : 'Geboekt'}`));
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
        const upcoming = data.sessions.filter((session) => !session.cancelled && !past(session))
            .sort((a, b) => `${a.date}${a.start}`.localeCompare(`${b.date}${b.start}`));
        const reserved = upcoming.filter((session) => bookingsFor(session).some((booking) => booking.athlete === 'Guillaume'));
        ['dashboard', 'coach'].forEach((id) => {
            const panel = document.querySelector(`#${id} .dashboard-overview`);
            if (!panel) return;
            const isCoach = id === 'coach';
            const relevant = isCoach ? upcoming : reserved;
            panel.replaceChildren();
            const heading = element('div', undefined, 'overview-heading');
            if (isCoach) heading.append(element('span', 'Jouw club in beeld', 'card-label'));
            heading.append(element('h3', isCoach ? 'Meer overzicht. Meer tijd voor coaching.' : 'Jouw planning'));
            panel.append(heading);
            const stats = element('div', undefined, 'overview-stats');
            const metrics = isCoach
                ? [[upcoming.length, 'Komende lessen'], [upcoming.reduce((sum, session) => sum + bookingsFor(session).length, 0), 'Reserveringen'], [data.products.filter((product) => product.available).length, 'Actieve producten']]
                : [[reserved.length, 'Jouw boekingen'], [upcoming.filter((session) => bookingsFor(session).length < session.capacity).length, 'Lessen met plaats']];
            metrics.forEach(([value, label]) => {
                const metric = element('div'); metric.append(element('strong', String(value)), element('span', label)); stats.append(metric);
            });
            panel.append(stats);
            const next = relevant[0];
            const preview = element('div', undefined, 'next-session');
            if (isCoach) preview.append(element('span', 'Eerstvolgende les', 'card-label'));
            preview.append(element('h4', next ? next.title : isCoach ? 'Ruimte voor een nieuwe les' : 'Nog niets gepland'));
            preview.append(element('p', next ? `${dateLabel(next.date)} · ${next.start} · ${next.coach}` : 'Bekijk de planning en maak ruimte voor beweging.'));
            const action = element('a', next ? 'Bekijk in planning →' : 'Naar de planning →', isCoach ? 'primary-button' : 'secondary-button');
            action.href = '#planning';
            action.addEventListener('click', () => {
                week = next ? next.date : today();
                $('session-filter').value = '';
                $('coach-filter').value = '';
            });
            preview.append(action); panel.append(preview);
            if (!isCoach) {
                const bookingsLink = element('a', 'Boekingen bekijken', 'dashboard-text-link');
                bookingsLink.href = '#bookings';
                preview.append(bookingsLink);
            }
        });
    }
    function render() { renderPlanning(); renderBookings(); renderShop(); renderDashboard(); }
    function openSession(session) {
        if (!coach()) return;
        const form = $('session-form'); form.reset();
        const values = session || { id: '', date: week, start: '18:00', end: '19:00', coach: 'Glenn', location: 'CAP Gistel', capacity: 8, type: 'Groep' };
        Object.entries(values).forEach(([name, value]) => { if (form.elements.namedItem(name)) form.elements.namedItem(name).value = value; });
        form.elements.repeat.disabled = Boolean(session);
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
            form.querySelectorAll('input:not([type]), textarea').forEach((input) => input.setCustomValidity(input.value.trim() ? '' : 'Vul dit veld in.'));
            if (!form.reportValidity()) return;
            const values = Object.fromEntries(new FormData(form));
            Object.keys(values).forEach((key) => { values[key] = values[key].trim(); });
            if (form.id === 'session-form') {
                const existing = data.sessions.find((session) => session.id === values.id);
                if (values.end <= values.start) { $('session-error').textContent = 'De eindtijd moet na de starttijd liggen.'; return; }
                if (existing && Number(values.capacity) < bookingsFor(existing).length) { $('session-error').textContent = 'De capaciteit kan niet lager zijn dan het aantal boekingen.'; return; }
                const session = { ...values, capacity: Number(values.capacity), cancelled: existing?.cancelled || false };
                delete session.repeat;
                if (existing) Object.assign(existing, session);
                else for (let index = 0; index < Number(values.repeat); index++) data.sessions.push({ ...session, id: uid(), date: shiftDate(values.date, index * 7) });
                week = values.date;
                $('session-dialog').close(); save('Les bewaard. De planning is bijgewerkt voor beide rollen.');
            } else {
                const product = { ...values, price: Number(values.price), available: values.available === 'true' };
                const existing = data.products.find((item) => item.id === product.id);
                if (existing) Object.assign(existing, product); else data.products.push({ ...product, id: uid() });
                $('product-dialog').close(); save('Product bewaard. Het aanbod voor sporters is bijgewerkt.');
            }
        });
    });
    document.querySelectorAll('[data-close]').forEach((control) => control.addEventListener('click', () => $(control.dataset.close).close()));
    $('new-session').addEventListener('click', () => openSession());
    $('new-product').addEventListener('click', () => openProduct());
    $('previous-week').addEventListener('click', () => { week = shiftDate(week, -7); renderPlanning(); });
    $('next-week').addEventListener('click', () => { week = shiftDate(week, 7); renderPlanning(); });
    $('current-week').addEventListener('click', () => { week = today(); renderPlanning(); });
    $('week-date').addEventListener('change', (event) => { if (event.target.value) week = event.target.value; renderPlanning(); });
    $('layout-toggle').addEventListener('click', () => {
        layout = layout === 'calendar' ? 'list' : 'calendar';
        $('layout-toggle').textContent = layout === 'calendar' ? 'Weekoverzicht' : 'Lijstoverzicht';
        $('layout-toggle').setAttribute('aria-pressed', String(layout === 'calendar')); renderPlanning();
    });
    ['session-filter', 'coach-filter', 'booking-filter', 'product-filter'].forEach((id) => $(id).addEventListener('change', render));
    document.addEventListener('cap:viewchange', render);
    // Link the existing group overview to the working schedule.
    const groupCard = document.querySelector('#group-classes .coach-create-card');
    if (groupCard) {
        groupCard.querySelector('p').textContent = 'Plan groepslessen, kies een coach en stel het aantal plaatsen in. Dezelfde planning is zichtbaar voor jouw sporters.';
        groupCard.querySelector('.status-label').textContent = 'Lokale preview';
        const link = element('a', 'Groepslessen plannen', 'primary-button'); link.href = '#planning';
        link.addEventListener('click', () => { $('session-filter').value = 'Groep'; });
        groupCard.append(link);
    }
    ['dashboard', 'coach'].forEach((id) => {
        if (id === 'dashboard') {
            const overview = element('section', undefined, 'dashboard-overview');
            overview.setAttribute('aria-label', 'Jouw planning in het kort');
            document.querySelector('#dashboard .dashboard-support').prepend(overview);
            return;
        }
        const panel = element('div', undefined, 'club-quick-links');
        [['planning', 'Planning bekijken'], ['bookings', 'Boekingen'], ['shop', 'Shop']].forEach(([route, label]) => {
            const link = element('a', label, 'secondary-button'); link.href = `#${route}`; panel.append(link);
        });
        document.querySelector(`#${id} .dashboard-header`).after(panel);
        const overview = element('section', undefined, 'dashboard-overview');
        overview.setAttribute('aria-label', id === 'coach' ? 'Cluboverzicht' : 'Jouw planning in het kort');
        panel.after(overview);
    });
    showCurrentView(false);
})();
