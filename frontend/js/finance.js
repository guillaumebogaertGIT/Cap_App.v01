// Financial data is fetched only for the separately authorized real coach account.
document.addEventListener('DOMContentLoaded', () => {
    const real = window.capReal;
    if (real?.account.role !== 'COACH' || !real.permissions?.reviewFinance) return;
    const page = document.getElementById('finance');
    const form = page.querySelector('form');
    const month = page.querySelector('#finance-month');
    const year = page.querySelector('#finance-year');
    const status = page.querySelector('[role="status"]');
    const results = page.querySelector('#finance-results');
    const list = page.querySelector('ul');
    const button = form.querySelector('button');
    const parts = Object.fromEntries(new Intl.DateTimeFormat('nl-BE', {
        timeZone: 'Europe/Brussels', year: 'numeric', month: '2-digit'
    }).formatToParts(new Date()).map(part => [part.type, part.value]));
    ['Januari', 'Februari', 'Maart', 'April', 'Mei', 'Juni', 'Juli', 'Augustus', 'September', 'Oktober', 'November', 'December']
        .forEach((label, index) => month.add(new Option(label, String(index + 1).padStart(2, '0'))));
    month.value = parts.month; year.value = parts.year;
    const money = cents => new Intl.NumberFormat('nl-BE', { style: 'currency', currency: 'EUR' }).format(cents / 100);
    let generation = 0;
    function clear() { ++generation; results.hidden = true; list.replaceChildren(); button.disabled = false; }
    async function load() {
        clear();
        if (!form.reportValidity()) return;
        const version = generation, selected = year.value + '-' + month.value;
        status.textContent = 'Testbetalingen controleren bij Stripe…'; button.disabled = true;
        try {
            const report = await real.request('/api/finance?month=' + encodeURIComponent(selected));
            if (version !== generation || page.hidden) return;
            if (!report.testMode || report.month !== selected) throw new Error('Het overzicht past niet bij de gekozen maand. Probeer opnieuw.');
            for (const key of ['received', 'refunded', 'remaining']) page.querySelector('#finance-' + key).textContent = money(report[key + 'Cents']);
            for (const payment of report.payments) {
                const item = document.createElement('li');
                const name = document.createElement('strong'); name.textContent = payment.athlete;
                const date = document.createElement('span'); date.textContent = window.capDateTime.dateTime(payment.date);
                const amount = document.createElement('span'); amount.textContent = money(payment.receivedCents);
                const note = document.createElement('span');
                note.textContent = payment.refundedCents ? money(payment.refundedCents) + ' terugbetaald' : 'Geen terugbetaling';
                if (payment.disputed) note.textContent += ' · Betwist: controleer Stripe';
                item.append(name, date, amount, note); list.append(item);
            }
            results.hidden = false;
            status.textContent = (report.payments.length ? report.payments.length + ' ontvangen testbetalingen.' : 'Geen ontvangen testbetalingen in deze maand.')
                + ' Gecontroleerd: ' + window.capDateTime.dateTime(report.checkedAt) + '.'
                + (report.excludedCurrencyCount ? ' ' + report.excludedCurrencyCount + ' betalingen in andere valuta niet meegerekend.' : '');
        } catch (error) {
            if (version === generation) status.textContent = error.message + ' Kies Overzicht laden om opnieuw te proberen.';
        } finally { if (version === generation) button.disabled = false; }
    }
    form.addEventListener('submit', event => { event.preventDefault(); load(); });
    form.addEventListener('input', () => { clear(); status.textContent = 'Kies Overzicht laden voor de geselecteerde maand.'; });
    document.addEventListener('cap:viewchange', () => {
        if (!page.hidden) load(); else clear();
    });
    if (!page.hidden) load();
});
