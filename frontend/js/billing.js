// Real account billing only. The design preview never contacts Stripe.
document.addEventListener('DOMContentLoaded', () => {
    const real = window.capReal;
    if (!real || real.account.role === 'COACH') return;
    const parameters = new URLSearchParams(location.search);
    const returned = parameters.get('billing');
    const dialog = document.createElement('dialog');
    dialog.id = 'billing-dialog'; dialog.className = 'club-dialog';
    dialog.setAttribute('aria-labelledby', 'billing-title');
    dialog.innerHTML = `<div class="session-dialog-header"><h2 id="billing-title">Lidmaatschap & betalingen</h2>
        <button type="button" class="secondary-button session-dialog-close" data-billing-close aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p class="sample-notice"><strong>TEST MODE</strong> · Geen echte betalingen. Gebruik uitsluitend Stripe-testkaarten.</p>
        <label for="billing-offer">Kies je lidmaatschap of credits</label><select id="billing-offer"><option value="">Aanbod laden…</option></select>
        <h3 id="billing-plan">CAP aanbod</h3><p><strong id="billing-price"></strong></p>
        <p id="billing-terms"></p>
        <p class="sample-notice">Lidmaatschappen starten na bevestigde betaling en stoppen automatisch. Een groepsles of Open gym-sessie gebruikt 1 credit als je lidmaatschap de sessiedatum niet dekt. Annuleer vóór de start om je credit terug te krijgen.</p>
        <p id="billing-return-note" role="status"></p>
        <dl id="billing-details" class="billing-details"></dl>
        <p id="billing-feedback" role="status" aria-live="polite"></p>
        <p class="sample-notice">Stripe opent in een nieuw tabblad. CAP blijft hier open.</p>
        <div class="billing-actions"><button type="button" class="primary-button" data-billing-subscribe>Verder naar testbetaling</button>
        <button type="button" class="secondary-button" data-billing-portal>Abonnement beheren</button>
        <button type="button" class="secondary-button" data-billing-cancel hidden>Opzeggen na betaalde maand</button>
        <button type="button" class="secondary-button" data-billing-refresh>Status bijwerken</button></div>`;
    document.body.append(dialog);
    const feedback = dialog.querySelector('#billing-feedback');
    const details = dialog.querySelector('#billing-details');
    const subscribe = dialog.querySelector('[data-billing-subscribe]');
    const portal = dialog.querySelector('[data-billing-portal]');
    const refresh = dialog.querySelector('[data-billing-refresh]');
    const cancel = dialog.querySelector('[data-billing-cancel]');
    const picker = dialog.querySelector('#billing-offer');
    let selectedCode = '';
    const money = offer => new Intl.NumberFormat('nl-BE', { style: 'currency', currency: offer.currency }).format(offer.cents / 100) + (offer.recurring ? ' per maand' : ' eenmalig');
    function selected() { return current?.offers?.find(offer => offer.code === selectedCode); }
    function selection() {
        const offer = selected();
        dialog.querySelector('#billing-plan').textContent = offer?.name || 'Kies een aanbod';
        dialog.querySelector('#billing-price').textContent = offer ? money(offer) : '';
        dialog.querySelector('#billing-terms').textContent = offer?.terms || 'Je huidige lidmaatschap en credits staan hieronder.';
        controls();
    }
    picker.addEventListener('change', () => { selectedCode = picker.value; selection(); });
    const statuses = { none: 'Geen testabonnement', active: 'Actief', incomplete: 'Betaling nog niet voltooid',
        incomplete_expired: 'Onvoltooide betaling verlopen', past_due: 'Betaling achterstallig', unpaid: 'Onbetaald',
        canceled: 'Beëindigd', paused: 'Gepauzeerd', trialing: 'Proefperiode: geen toegang in deze test', missing: 'Status moet worden gecontroleerd' };
    let current, busy = false, timer, focusTimer, generation = 0, loadError = '', planningPending = false;
    const planning = document.getElementById('planning-credits');
    const planningActive = () => real.account.role === 'ATHLETE' && location.hash === '#planning';
    const visible = () => dialog.open || location.hash === '#shop' || planningActive();
    function renderPlanning() {
        if (!planning || real.account.role !== 'ATHLETE') return;
        planning.hidden = false;
        const balance = document.getElementById('planning-credit-balance');
        balance.textContent = loadError ? 'Je credits konden niet worden geladen. Probeer opnieuw.'
            : planningPending || !current ? 'Lescredits laden…'
                : `${current.credits ?? 0} ${current.credits === 1 ? 'lescredit beschikbaar' : 'lescredits beschikbaar'}`;
        document.getElementById('planning-credit-retry').hidden = !loadError;
        document.getElementById('planning-credit-help').textContent = current?.membershipAccess
            ? 'Binnen je betaalde lidmaatschap gebruik je geen credits. Anders kost een groepsles of Open gym-sessie 1 credit. Annuleer vóór de start om die terug te krijgen.'
            : '1 credit = 1 groepsles of Open gym-sessie. Annuleer vóór de start om je credit terug te krijgen.';
    }
    const date = value => value ? new Intl.DateTimeFormat('nl-BE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Europe/Brussels' }).format(new Date(value)) : '—';
    function controls() {
        subscribe.disabled = busy || !selected()?.available || (current?.membershipAccess && !selected()?.credits);
        picker.disabled = busy;
        cancel.hidden = !current?.canCancel;
        cancel.disabled = busy || !current?.canCancel;
        portal.disabled = busy || !current?.configured || !current.hasCustomer;
        refresh.disabled = busy;
        refresh.textContent = current?.configured ? 'Status bijwerken' : 'Opnieuw laden';
    }
    function render(value) {
        current = value; planningPending = false; details.replaceChildren();
        picker.replaceChildren(new Option('Kies een aanbod', ''));
        for (const offer of value.offers || []) picker.add(new Option(offer.name + ' · ' + money(offer) + (offer.available ? '' : ' · nog niet ingesteld'), offer.code));
        picker.value = selectedCode;
        selection();
        for (const [label, text] of [
            ['Huidig lidmaatschap', value.membershipAccess ? value.plan : 'Geen actief lidmaatschap'],
            ['Status', statuses[value.status] || 'Nog niet bevestigd'],
            ['Beschikbare lescredits', String(value.credits ?? 0)],
            ['Uiterste einddatum', date(value.termEnd)],
            ['Betaalde toegang tot', date(value.periodEnd)],
            ['Opzegging', value.cancelAtPeriodEnd ? 'Gepland op periode-einde' : value.status === 'canceled' ? 'Beëindigd' : 'Niet gepland']
        ]) {
            const term = document.createElement('dt'); term.textContent = label;
            const description = document.createElement('dd'); description.textContent = text;
            details.append(term, description);
        }
        feedback.textContent = value.configured ? 'De bevestigde status komt van de server. Bijwerken controleert deze opnieuw bij Stripe.' : 'Stripe TEST MODE is nog niet ingesteld. Je account en bestaande functies blijven beschikbaar.';
        controls();
        renderShop();
        renderPlanning();
    }
    function renderShop() {
        const list = document.getElementById('product-list');
        if (!list) return;
        list.querySelectorAll('[data-billing-shop], #shop-membership').forEach(node => node.remove());
        const filter = document.getElementById('product-filter').value;
        if (filter && !['Abonnement', 'Beurtenkaart'].includes(filter)) return;
        list.querySelector('.club-empty')?.remove();
        const overview = document.createElement('div'); overview.dataset.billingShop = ''; overview.className = 'billing-shop-status';
        const heading = document.createElement('h3'); heading.textContent = 'Jouw lidmaatschap & credits';
        const status = document.createElement('p'); status.setAttribute('role', 'status');
        status.textContent = loadError || (!current ? 'Status laden…' : (current.membershipAccess ? current.plan + ' · betaald tot ' + date(current.periodEnd) : 'Geen actief lidmaatschap') + ' · ' + (current.credits ?? 0) + ' beschikbare lescredits');
        const manage = document.createElement('button'); manage.type = 'button'; manage.className = 'secondary-button'; manage.textContent = 'Betalingen bekijken';
        manage.addEventListener('click', () => window.capOpenBilling());
        const notice = document.createElement('p'); notice.className = 'sample-notice'; notice.textContent = 'TEST MODE · Geen echte betalingen. Ongebruikte credits vervallen niet.';
        overview.append(heading, status, manage, notice); list.prepend(overview);
        for (const offer of current?.offers || []) {
            if (filter && filter !== (offer.credits ? 'Beurtenkaart' : 'Abonnement')) continue;
            const card = document.createElement('article'); card.dataset.billingShop = ''; card.className = 'club-card';
            const title = document.createElement('h3'); title.textContent = offer.name;
            const price = document.createElement('strong'); price.className = 'club-price'; price.textContent = money(offer);
            const description = document.createElement('p'); description.textContent = offer.terms;
            const action = document.createElement('button'); action.type = 'button'; action.className = 'primary-button';
            action.textContent = offer.available ? 'Bekijk aanbod' : 'Bekijk testaanbod';
            action.addEventListener('click', () => window.capOpenBilling(offer.code));
            card.append(title, price, description, action); list.append(card);
        }
    }
    window.capBilling = { renderShop };
    async function load() {
        const version = ++generation;
        loadError = '';
        planningPending = true; renderPlanning();
        feedback.textContent = 'Abonnementsstatus laden…';
        try { const value = await real.request('/api/billing'); if (version === generation && visible()) render(value); }
        catch (error) { if (version === generation) { planningPending = false; loadError = error.message; feedback.textContent = error.message; renderShop(); renderPlanning(); } }
    }
    async function act(action) {
        if (busy) return;
        busy = true; ++generation; clearTimeout(timer); controls(); feedback.textContent = 'Even geduld…';
        try { await action(); }
        catch (error) { feedback.textContent = error.message; }
        finally { busy = false; controls(); }
    }
    function openStripe(path, host, body = {}) {
        if (busy) return;
        // Open during the click, before awaiting the server, so mobile browsers allow it.
        const tab = window.open('about:blank', '_blank');
        if (!tab) {
            feedback.textContent = 'Je browser blokkeert het nieuwe tabblad. Sta pop-ups toe voor CAP en probeer opnieuw.';
            return;
        }
        tab.opener = null;
        tab.document.title = 'Stripe openen';
        tab.document.body.textContent = 'Stripe wordt geopend. CAP blijft in het oorspronkelijke tabblad beschikbaar.';
        act(async () => {
            try {
                const value = await real.request(path, 'POST', body);
                const url = new URL(value.url);
                if (url.protocol !== 'https:' || url.hostname !== host || url.username || url.password)
                    throw new Error('Geen geldige Stripe TEST-link ontvangen.');
                if (tab.closed) {
                    feedback.textContent = 'Het nieuwe tabblad is gesloten. Klik opnieuw om verder te gaan.';
                    return;
                }
                tab.location.replace(url.href);
                feedback.textContent = 'Stripe is geopend in een nieuw tabblad. Bij terugkeer wordt je status opnieuw geladen.';
            } catch (error) {
                if (!tab.closed) tab.close();
                throw error;
            }
        });
    }
    subscribe.addEventListener('click', () => { if (selected()) openStripe('/api/billing/checkout', 'checkout.stripe.com', { offer: selectedCode }); });
    cancel.addEventListener('click', () => {
        if (busy || !current?.canCancel || !window.confirm('Abonnement opzeggen? Je houdt toegang tot ' + date(current.periodEnd) + '. Daarna stoppen de maandbetalingen.')) return;
        act(async () => render(await real.request('/api/billing/cancel', 'POST', {})));
    });
    portal.addEventListener('click', () => openStripe('/api/billing/portal', 'billing.stripe.com'));
    refresh.addEventListener('click', () => act(async () => {
        if (!current?.configured) await load();
        else render(await real.request('/api/billing/reconcile', 'POST', {}));
    }));
    dialog.querySelector('[data-billing-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { ++generation; clearTimeout(timer); clearTimeout(focusTimer); document.getElementById('account-toggle').focus(); });
    function refreshOnReturn() {
        if (!visible() || busy || document.visibilityState === 'hidden') return;
        clearTimeout(focusTimer);
        focusTimer = setTimeout(() => { if (visible() && !busy) load(); }, 100);
    }
    window.addEventListener('focus', refreshOnReturn);
    document.addEventListener('visibilitychange', refreshOnReturn);
    window.capOpenBilling = code => { selectedCode = typeof code === 'string' ? code : ''; if (!dialog.open) dialog.showModal(); selection(); load(); };
    document.addEventListener('cap:viewchange', () => { if (location.hash === '#shop' || planningActive()) { renderShop(); load(); } });
    document.addEventListener('cap:booking-changed', () => { if (visible()) load(); });
    document.getElementById('planning-credit-retry')?.addEventListener('click', load);
    renderShop();
    renderPlanning();
    if (location.hash === '#shop' || planningActive()) load();
    if (['success', 'cancel', 'return'].includes(returned)) {
        parameters.delete('billing');
        history.replaceState(null, '', location.pathname + (parameters.size ? '?' + parameters : '') + location.hash);
        dialog.querySelector('#billing-return-note').textContent = returned === 'success'
            ? 'Je bent terug van Stripe. Je actuele lidmaatschap en credits staan hieronder. De betaalbevestiging kan enkele seconden duren.'
            : returned === 'cancel' ? 'Je hebt Checkout verlaten. Je kunt later verdergaan met je betaling.'
                : 'Je bent terug van het testportaal. Klik op Status bijwerken om je huidige status op te halen.';
        window.capOpenBilling();
        // Bounded local reads only. A success URL never triggers reconciliation or activation.
        if (returned === 'success') {
            let attempts = 0;
            const poll = async () => {
                if (!dialog.open || busy) return;
                await load();
                if (current?.configured && ++attempts < 5) timer = setTimeout(poll, 2000);
            };
            timer = setTimeout(poll, 2000);
        }
    }
    controls();
});
