// The camera will eventually feed this same barcode lookup; nothing is logged automatically.
document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const panel = $('food-barcode-panel'), form = $('food-barcode-form'), input = $('food-barcode');
    const status = $('food-barcode-status'), result = $('food-barcode-result'), manual = $('food-barcode-manual');
    const submit = form.querySelector('button');
    const allowed = () => window.capAccess.signedIn && previewRole === 'athlete' && Boolean(window.capFoodDiary);
    const node = (tag, text, cls) => { const value = document.createElement(tag); if (text) value.textContent = text; if (cls) value.className = cls; return value; };
    const format = value => value == null ? 'Ontbreekt' : new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(value);
    let generation = 0, controller = null;
    document.addEventListener('cap:food-lookup-reset', () => { cancel(); result.hidden=true; result.replaceChildren(); status.textContent=''; manual.hidden=true; });
    document.addEventListener('cap:food-barcode-selected', event => {
        if(!allowed() || !/^(?:[0-9]{8}|[0-9]{12,14})$/.test(event.detail?.barcode || ''))return;
        input.value=event.detail.barcode;
        input.dispatchEvent(new Event('input',{bubbles:true}));
        form.requestSubmit();
    });
    function cancel() { ++generation; controller?.abort(); controller = null; submit.disabled = false; panel.removeAttribute('aria-busy'); }
    input.addEventListener('input', () => { cancel(); result.hidden = true; result.replaceChildren(); status.textContent = ''; manual.hidden = true; });
    function show(value) {
        result.replaceChildren(); result.hidden = false;
        const heading = node('h5', value.name); heading.tabIndex = -1;
        result.append(heading);
        if (value.brand) result.append(node('p', value.brand));
        result.append(node('p', value.unit ? `Per 100 ${value.unit} · zoals verkocht` : 'Eenheid of voedingswaarden zoals verkocht onbekend.', 'sample-notice'));
        const nutrients = node('dl', null, 'food-barcode-nutrients');
        for (const [label, amount, unit] of [['Energie', value.calories, 'kcal'], ['Eiwitten', value.proteinG, 'g'], ['Koolhydraten', value.carbsG, 'g'], ['Vetten', value.fatG, 'g']]) {
            const row = node('div'); row.append(node('dt', label), node('dd', format(amount) + (amount == null ? '' : ' ' + unit))); nutrients.append(row);
        }
        result.append(nutrients);
        if (value.source === 'personal') result.append(node('p','Eigen product · alleen in jouw account','sample-notice'));
        else {
            const source = node('a', 'Bekijk product op Open Food Facts');
            source.href = 'https://world.openfoodfacts.org/product/' + value.barcode; source.target = '_blank'; source.rel = 'noopener noreferrer';
            result.append(source);
        }
        if (value.complete && ['g', 'ml'].includes(value.unit)) {
            const label = node('label', null, 'food-barcode-confirm');
            const check = document.createElement('input'); check.type = 'checkbox';
            label.append(check, node('span', 'Product, eenheid en voedingswaarden kloppen met mijn verpakking.'));
            const use = node('button', 'Dit product gebruiken', 'primary-button'); use.type = 'button'; use.disabled = true;
            check.addEventListener('change', () => { use.disabled = !check.checked; });
            use.addEventListener('click', () => {
                if (!allowed() || !check.checked) return;
                if (!window.capFoodDiary.selectBarcodeProduct(value)) {
                    status.textContent = 'Wacht tot je dagboek is opgeslagen en probeer opnieuw.'; return;
                }
                result.hidden = true; result.replaceChildren(); manual.hidden = true;
                status.textContent = 'Product geselecteerd. Vul hieronder de hoeveelheid in en voeg je portie toe aan het dagboek.';
            });
            result.append(label, use);
        } else {
            result.append(node('p', 'Dit product heeft geen volledige bruikbare voedingswaarden. Vul de waarden voor je gegeten portie zelf in.', 'sample-notice'));
        }
        manual.hidden = false; heading.focus();
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!allowed() || !form.reportValidity()) return;
        cancel(); const version = generation;
        const barcode = input.value.trim().replace(/[ -]/g, '');
        result.hidden = true; result.replaceChildren(); manual.hidden = true;
        if (!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(barcode)) { status.textContent = 'Vul de 8, 12, 13 of 14 cijfers onder de streepjescode in.'; return; }
        controller = new AbortController(); const activeController = controller;
        const timeout = setTimeout(() => activeController.abort(), 12000);
        submit.disabled = true; panel.setAttribute('aria-busy', 'true'); status.textContent = 'Product opzoeken…';
        try {
            const response = await fetch('http://localhost:8080/api/food-products/' + barcode, { cache: 'no-store', signal: activeController.signal });
            const value = await response.json();
            if (version !== generation || !allowed()) return;
            if (!response.ok) {
                if (response.status === 404) document.dispatchEvent(new CustomEvent('cap:food-product-result',{detail:{barcode,value:null}}));
                throw new Error(value.message || 'Product opzoeken is niet gelukt. Probeer opnieuw.');
            }
            if (value.barcode !== barcode || typeof value.name !== 'string') throw new Error('Het product kon niet worden gelezen. Probeer opnieuw.');
            status.textContent = ''; show(value);
            document.dispatchEvent(new CustomEvent('cap:food-product-result',{detail:{barcode,value}}));
        } catch (error) {
            if (version !== generation || !allowed()) return;
            status.textContent = error instanceof TypeError || error.name === 'AbortError'
                ? 'Geen verbinding met de productdatabase. Probeer opnieuw of vul je product zelf in.' : error.message;
            manual.hidden = false;
        } finally {
            clearTimeout(timeout);
            if (version === generation) { submit.disabled = false; panel.removeAttribute('aria-busy'); controller = null; }
        }
    });
    manual.addEventListener('click', () => {
        if (!allowed()) return;
        const custom = $('food-custom-form'); custom.closest('details').open = true;
        custom.scrollIntoView({ block: 'center', behavior: 'smooth' }); custom.elements.namedItem('name').focus({ preventScroll: true });
    });
    function sync() {
        panel.hidden = !allowed();
        if (!allowed() || location.hash !== '#nutrition') { cancel(); result.hidden = true; result.replaceChildren(); status.textContent = ''; manual.hidden = true; }
    }
    document.addEventListener('cap:viewchange', sync); sync();
});
