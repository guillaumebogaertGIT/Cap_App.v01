// Read-only coach view. Demo mode and athlete accounts never request this endpoint.
document.addEventListener('DOMContentLoaded', () => {
    const real = window.capReal;
    if (real?.account.role !== 'COACH') return;
    const page = document.getElementById('athletes');
    page.querySelector(':scope > .sample-notice')?.remove();
    page.querySelector('header .dashboard-intro').textContent = 'Bekijk en begeleid sporters met een CAP-account.';
    const panel = document.createElement('section');
    panel.className = 'coach-membership'; panel.setAttribute('aria-labelledby', 'coach-membership-title');
    panel.innerHTML = `<h3 id="coach-membership-title">Lidmaatschap & credits</h3>
        <div class="club-toolbar"><label>Sporter<select id="coach-membership-athlete"></select></label>
        <button type="button" class="secondary-button" id="coach-membership-refresh" disabled>Opnieuw laden</button></div>
        <p id="coach-membership-status" role="status" aria-live="polite">Kies een sporter om het lidmaatschap en de credits te bekijken.</p>
        <dl id="coach-membership-details" class="billing-details" hidden></dl>
        <p class="sample-notice">Dit overzicht toont de bewaarde accountgegevens. Alleen bekijken; betalingen beheert de sporter zelf.</p>`;
    page.querySelector('header').after(panel);
    const picker = panel.querySelector('select');
    const refresh = panel.querySelector('button');
    const status = panel.querySelector('[role="status"]');
    const details = panel.querySelector('dl');
    let generation = 0, timer;
    const date = value => value ? window.capDateTime.date(value) : '—';
    const labels = { none: 'Geen lidmaatschap', active: 'Actief', incomplete: 'Betaling niet voltooid',
        incomplete_expired: 'Betaling verlopen', past_due: 'Betaling achterstallig', unpaid: 'Onbetaald',
        canceled: 'Beëindigd', paused: 'Gepauzeerd', trialing: 'Proefperiode', missing: 'Status niet bevestigd' };
    function populate() {
        const selected = picker.value;
        picker.replaceChildren(new Option(real.athletes.length ? 'Kies een sporter' : 'Nog geen sporters', ''));
        for (const athlete of real.athletes) picker.add(new Option(athlete.name, athlete.id));
        picker.value = real.athletes.some(athlete => athlete.id === selected) ? selected : '';
        picker.disabled = !real.athletes.length;
        if (selected && !picker.value) schedule();
    }
    async function load(version, athleteId) {
        try {
            const value = await real.request('/api/billing/athletes/' + encodeURIComponent(athleteId));
            if (version !== generation || picker.value !== athleteId || location.hash !== '#athletes') return;
            if (value.athleteId !== athleteId) throw new Error('De gegevens passen niet bij de geselecteerde sporter. Probeer opnieuw.');
            for (const [label, text] of [
                ['Lidmaatschap', value.status === 'none' ? 'Geen lidmaatschap' : value.plan],
                ['Status', value.membershipAccess ? 'Actief' : value.status === 'active' ? 'Niet actief' : labels[value.status] || 'Niet bevestigd'],
                ['Betaald tot', date(value.paidUntil)],
                ['Beschikbare lescredits', String(value.credits)],
                ['Uiterste einddatum', date(value.termEnd)],
                ['Opzegging', value.cancellationPlanned ? 'Gepland na de betaalde periode' : value.status === 'canceled' ? 'Beëindigd' : 'Niet gepland']
            ]) {
                const term = document.createElement('dt'); term.textContent = label;
                const description = document.createElement('dd'); description.textContent = text;
                details.append(term, description);
            }
            details.hidden = false;
            status.textContent = 'Accountgegevens van ' + (real.athletes.find(athlete => athlete.id === athleteId)?.name || 'de geselecteerde sporter') + '.';
        } catch (error) {
            if (version === generation && picker.value === athleteId) status.textContent = error.message;
        } finally {
            if (version === generation) refresh.disabled = !picker.value;
        }
    }
    function schedule() {
        const version = ++generation, athleteId = picker.value;
        clearTimeout(timer); details.replaceChildren(); details.hidden = true;
        refresh.disabled = !athleteId;
        status.textContent = athleteId ? 'Lidmaatschap en credits laden…' : 'Kies een sporter om het lidmaatschap en de credits te bekijken.';
        if (athleteId && location.hash === '#athletes') timer = setTimeout(() => load(version, athleteId), 100);
    }
    picker.addEventListener('change', schedule);
    refresh.addEventListener('click', schedule);
    document.addEventListener('cap:athletes-changed', populate);
    document.addEventListener('cap:viewchange', () => { if (location.hash === '#athletes') schedule(); else { ++generation; clearTimeout(timer); } });
    window.addEventListener('focus', () => { if (location.hash === '#athletes') schedule(); });
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && location.hash === '#athletes') schedule(); });
    populate();
});
