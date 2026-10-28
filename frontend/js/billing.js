// Real account billing only. The design preview never contacts Stripe.
document.addEventListener('DOMContentLoaded', () => {
    const real = window.capReal;
    if (!real) return;
    const parameters = new URLSearchParams(location.search);
    const returned = parameters.get('billing');
    const dialog = document.createElement('dialog');
    dialog.id = 'billing-dialog'; dialog.className = 'club-dialog';
    dialog.setAttribute('aria-labelledby', 'billing-title');
    dialog.innerHTML = `<div class="session-dialog-header"><h2 id="billing-title">Lidmaatschap & betalingen</h2>
        <button type="button" class="secondary-button session-dialog-close" data-billing-close aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p class="sample-notice"><strong>TEST MODE</strong> · Geen echte betalingen. Gebruik uitsluitend Stripe-testkaarten.</p>
        <h3 id="billing-plan">CAP Test Membership</h3><p><strong id="billing-price">€ 10 per maand</strong> · tijdelijke testprijs, geen echt CAP-aanbod.</p>
        <p>Geen proefperiode. Opzeggen gaat in aan het einde van de betaalde periode. Je kunt altijd blijven inloggen; de bestaande functies blijven beschikbaar.</p>
        <p id="billing-return-note" role="status"></p>
        <dl id="billing-details" class="billing-details"></dl>
        <p id="billing-feedback" role="status" aria-live="polite"></p>
        <p class="sample-notice">Stripe opent in een nieuw tabblad. CAP blijft hier open.</p>
        <div class="billing-actions"><button type="button" class="primary-button" data-billing-subscribe>Testabonnement starten</button>
        <button type="button" class="secondary-button" data-billing-portal>Abonnement beheren</button>
        <button type="button" class="secondary-button" data-billing-refresh>Status bijwerken</button></div>`;
    document.body.append(dialog);
    const feedback = dialog.querySelector('#billing-feedback');
    const details = dialog.querySelector('#billing-details');
    const subscribe = dialog.querySelector('[data-billing-subscribe]');
    const portal = dialog.querySelector('[data-billing-portal]');
    const refresh = dialog.querySelector('[data-billing-refresh]');
    const statuses = { none: 'Geen testabonnement', active: 'Actief', incomplete: 'Betaling nog niet voltooid',
        incomplete_expired: 'Onvoltooide betaling verlopen', past_due: 'Betaling achterstallig', unpaid: 'Onbetaald',
        canceled: 'Beëindigd', paused: 'Gepauzeerd', trialing: 'Proefperiode: geen toegang in deze test', missing: 'Status moet worden gecontroleerd' };
    let current, busy = false, timer, focusTimer, generation = 0;
    const date = value => value ? new Intl.DateTimeFormat('nl-BE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Europe/Brussels' }).format(new Date(value)) : '—';
    function controls() {
        subscribe.disabled = busy || !current?.configured || !['none', 'canceled', 'incomplete_expired'].includes(current.status);
        portal.disabled = busy || !current?.configured || !current.hasCustomer;
        refresh.disabled = busy;
        refresh.textContent = current?.configured ? 'Status bijwerken' : 'Opnieuw laden';
    }
    function render(value) {
        current = value; details.replaceChildren();
        if (returned === 'success' && value.membershipAccess)
            dialog.querySelector('#billing-return-note').textContent = 'Je testabonnement is bevestigd door de server.';
        dialog.querySelector('#billing-plan').textContent = value.plan;
        dialog.querySelector('#billing-price').textContent = new Intl.NumberFormat('nl-BE', { style: 'currency', currency: value.currency }).format(value.monthlyCents / 100) + ' per maand';
        for (const [label, text] of [
            ['Status', statuses[value.status] || 'Nog niet bevestigd'],
            ['Testlidmaatschap', value.membershipAccess ? 'Actief' : 'Niet actief'],
            [value.cancelAtPeriodEnd ? 'Toegang tot' : 'Periode-einde / verlenging', date(value.periodEnd)],
            ['Opzegging', value.cancelAtPeriodEnd ? 'Gepland op periode-einde' : value.status === 'canceled' ? 'Beëindigd' : 'Niet gepland']
        ]) {
            const term = document.createElement('dt'); term.textContent = label;
            const description = document.createElement('dd'); description.textContent = text;
            details.append(term, description);
        }
        feedback.textContent = value.configured ? 'De bevestigde status komt van de server. Bijwerken controleert deze opnieuw bij Stripe.' : 'Stripe TEST MODE is nog niet ingesteld. Je account en bestaande functies blijven beschikbaar.';
        controls();
    }
    async function load() {
        const version = ++generation;
        feedback.textContent = 'Abonnementsstatus laden…';
        try { const value = await real.request('/api/billing'); if (version === generation && dialog.open) render(value); }
        catch (error) { if (version === generation) feedback.textContent = error.message; }
    }
    async function act(action) {
        if (busy) return;
        busy = true; ++generation; clearTimeout(timer); controls(); feedback.textContent = 'Even geduld…';
        try { await action(); }
        catch (error) { feedback.textContent = error.message; }
        finally { busy = false; controls(); }
    }
    function openStripe(path, host) {
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
                const value = await real.request(path, 'POST', {});
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
    subscribe.addEventListener('click', () => openStripe('/api/billing/checkout', 'checkout.stripe.com'));
    portal.addEventListener('click', () => openStripe('/api/billing/portal', 'billing.stripe.com'));
    refresh.addEventListener('click', () => act(async () => {
        if (!current?.configured) await load();
        else render(await real.request('/api/billing/reconcile', 'POST', {}));
    }));
    dialog.querySelector('[data-billing-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { ++generation; clearTimeout(timer); clearTimeout(focusTimer); document.getElementById('account-toggle').focus(); });
    function refreshOnReturn() {
        if (!dialog.open || busy || document.visibilityState === 'hidden') return;
        clearTimeout(focusTimer);
        focusTimer = setTimeout(() => { if (dialog.open && !busy) load(); }, 100);
    }
    window.addEventListener('focus', refreshOnReturn);
    document.addEventListener('visibilitychange', refreshOnReturn);
    window.capOpenBilling = () => { if (!dialog.open) dialog.showModal(); load(); };
    if (['success', 'cancel', 'return'].includes(returned)) {
        parameters.delete('billing');
        history.replaceState(null, '', location.pathname + (parameters.size ? '?' + parameters : '') + location.hash);
        dialog.querySelector('#billing-return-note').textContent = returned === 'success'
            ? 'Je bent terug van Stripe. We wachten op de bevestigde abonnementsstatus; deze pagina activeert zelf niets.'
            : returned === 'cancel' ? 'Je hebt Checkout verlaten. Er is via deze pagina geen lidmaatschap geactiveerd. Je kunt later verdergaan.'
                : 'Je bent terug van het testportaal. Klik op Status bijwerken om je huidige status op te halen.';
        window.capOpenBilling();
        // Bounded local reads only. A success URL never triggers reconciliation or activation.
        if (returned === 'success') {
            let attempts = 0;
            const poll = async () => {
                if (!dialog.open || busy) return;
                await load();
                if (!current?.membershipAccess && current?.configured && ++attempts < 5) timer = setTimeout(poll, 2000);
            };
            timer = setTimeout(poll, 2000);
        }
    }
    controls();
});
