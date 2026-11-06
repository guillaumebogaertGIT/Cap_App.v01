// One product combobox: local matches immediately, Belgian-market matches after a pause.
document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id), api = window.capFoodDiary;
    if (!api) return;
    const input = $('food-search'), amount = $('food-amount'), select = $('food-product');
    const label = input.closest('label'), wrapper = document.createElement('div'); wrapper.className = 'food-autocomplete';
    label.before(wrapper); wrapper.append(label);
    const list = document.createElement('div'); list.id = 'food-suggestions'; list.className = 'food-suggestions';
    list.setAttribute('role', 'listbox'); list.setAttribute('aria-label', 'Producten'); list.hidden = true; wrapper.append(list);
    const actions=document.createElement('div');actions.className='editor-actions';
    const search=document.createElement('button');search.type='button';search.className='secondary-button food-search-action';search.textContent='Zoek supermarktproducten';
    const more=document.createElement('button');more.type='button';more.className='secondary-button food-search-action';more.textContent='Meer supermarktproducten tonen';more.hidden=true;
    actions.append(search,$('food-scan-start'),more);wrapper.append(actions);
    const status = document.createElement('p'); status.id = 'food-search-status'; status.className = 'sample-notice'; status.setAttribute('role', 'status');
    const manual = document.createElement('button'); manual.type = 'button'; manual.className = 'secondary-button'; manual.textContent = 'Product zelf invullen'; manual.hidden = true;
    wrapper.after(status, manual);
    select.closest('label').hidden = true; select.required = false; amount.disabled = !select.value;
    input.required = true; input.maxLength = 80; input.setAttribute('role', 'combobox'); input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-controls', list.id); input.setAttribute('aria-expanded', 'false'); input.setAttribute('aria-describedby', status.id);
    input.placeholder = 'Bijv. yoghurt, Boni of brood';
    document.querySelector('.food-portion-layout').after($('food-barcode-panel'));
    const allowed = () => window.capAccess.signedIn && location.hash === '#nutrition';
    const cache = new Map();
    let generation = 0, controller = null, timer = null, lastRemoteAt = 0, options = [], active = -1, wantsOpen = false;
    function cancel() { ++generation; clearTimeout(timer); controller?.abort(); controller = null; search.disabled=false;more.disabled=false; }
    function close() { wantsOpen = false; list.hidden = true;more.hidden=true; input.setAttribute('aria-expanded', 'false'); input.removeAttribute('aria-activedescendant'); active = -1; }
    function move(index) {
        if (!options.length) return;
        active = (index + options.length) % options.length;
        [...list.children].forEach((row,i) => { row.setAttribute('aria-selected', String(i === active)); row.classList.toggle('is-active', i === active); });
        input.setAttribute('aria-activedescendant', list.children[active].id); list.children[active].scrollIntoView({ block: 'nearest' });
    }
    function render(remote = []) {
        const local = api.searchProducts(input.value);
        const ids = new Set(local.map(food => food.id));
        options = [...local.map(food => ({ ...food, local: true })), ...remote.filter(food => !ids.has('off:' + food.barcode) && !ids.has('personal:' + food.barcode))];
        list.replaceChildren(); active = -1; input.removeAttribute('aria-activedescendant');
        options.forEach((food,index) => {
            const row = document.createElement('button'); row.type = 'button'; row.className = 'food-suggestion'; row.id = 'food-suggestion-' + index;
            row.setAttribute('role', 'option'); row.setAttribute('aria-selected', 'false'); row.tabIndex = -1;
            const name = document.createElement('strong'); name.textContent = food.name;
            const source = document.createElement('small'); source.textContent = food.local ? (food.source === 'personal' ? 'Mijn product' : 'Bibliotheek') + ' · ' + (food.complete ? food.unit : 'waarden aanvullen') : [food.brand, 'Belgisch marktproduct'].filter(Boolean).join(' · ');
            row.append(name,source); row.addEventListener('mousedown', event => event.preventDefault()); row.addEventListener('click', () => choose(food)); list.append(row);
        });
        const show = wantsOpen && allowed() && document.activeElement === input && options.length > 0;
        list.hidden = !show; input.setAttribute('aria-expanded', String(show));
        more.hidden=!(wantsOpen&&allowed()&&document.activeElement===input&&cache.get(input.value.trim().toLocaleLowerCase('nl-BE'))?.hasMore);
    }
    function fallback(message) { status.textContent = message; manual.hidden = false; }
    async function choose(food) {
        if (!allowed()) return;
        cancel(); close(); manual.hidden = true;
        if (food.local) {
            if (food.source === 'personal' && !food.complete) { window.capPersonalFoods?.edit(food); return; }
            if (!api.selectProduct(food.id)) fallback('Wacht tot je dagboek is opgeslagen en probeer opnieuw.');
            return;
        }
        const version = generation; input.value = food.name; input.setCustomValidity('Wacht tot de productgegevens geladen zijn.');
        status.textContent = 'Voedingswaarden laden…';
        const request = new AbortController(); controller = request;
        const timeout = setTimeout(() => request.abort(), 12000);
        try {
            const response = await fetch('http://localhost:8080/api/food-products/' + encodeURIComponent(food.barcode), { cache: 'no-store', signal: request.signal });
            const value = await response.json();
            if (version !== generation || !allowed()) return;
            if (!response.ok) throw new Error(value.message || 'Product niet beschikbaar. Kies een ander product.');
            if (value.barcode !== food.barcode || !value.complete || !['g','ml'].includes(value.unit)) throw new Error('Dit product mist bruikbare voedingswaarden. Kies een ander product of vul je portie zelf in.');
            if (!api.selectBarcodeProduct(value)) throw new Error('Wacht tot je dagboek is opgeslagen en probeer opnieuw.');
        } catch (error) {
            if (version !== generation || !allowed()) return;
            input.setCustomValidity('Kies een ander product of vul je portie zelf in.');
            fallback(error instanceof TypeError || error.name === 'AbortError' ? 'Geen verbinding met de productdatabase. Probeer opnieuw of gebruik de barcode.' : error.message);
        } finally { clearTimeout(timeout); if (version === generation) controller = null; }
    }
    function schedule(page=1) {
        const query = input.value.trim();
        if (!allowed() || query.length < 3) { status.textContent = 'Typ minstens 3 tekens om ook Belgische supermarktproducten te zoeken.'; return; }
        const cached = cache.get(query.toLocaleLowerCase('nl-BE'));
        if (page===1 && cached && Date.now() - cached.time < 600000) { render(cached.products); status.textContent = `${cached.products.length} supermarktproducten geladen. Kies een product of toon meer resultaten.`; return; }
        const version = generation;
        const delay=Math.max(800,lastRemoteAt+8500-Date.now());
        search.disabled=true;more.disabled=true;
        status.textContent = delay>1000?'Zoekopdracht staat klaar. Even wachten op de productdatabase…':page>1?'Meer supermarktproducten zoeken…':'Belgische producten zoeken…';
        // Preserve the provider budget: pause typing, at most one new query per 8.5 seconds.
        timer = setTimeout(async () => {
            if (version !== generation || !allowed()) return;
            lastRemoteAt = Date.now();
            const request = new AbortController(); controller = request;
            const timeout = setTimeout(() => request.abort(), 12000);
            try {
                const response = await fetch('http://localhost:8080/api/food-products/search?' + new URLSearchParams({ query, page }), { cache: 'no-store', signal: request.signal });
                const value = await response.json();
                if (version !== generation || !allowed()) return;
                if (!response.ok) throw new Error(value.message || 'Online zoeken is tijdelijk niet beschikbaar.');
                if (!Array.isArray(value.products)) throw new Error('De producten konden niet worden gelezen.');
                const combined=new Map((page>1?cached?.products||[]:[]).map(food=>[food.barcode,food]));
                value.products.forEach(food=>combined.set(food.barcode,food));
                const products=[...combined.values()];
                cache.set(query.toLocaleLowerCase('nl-BE'), { time: Date.now(), products, page, hasMore:value.hasMore===true });
                while (cache.size > 50) cache.delete(cache.keys().next().value);
                const scroll=list.scrollTop;render(products);if(page>1)list.scrollTop=scroll;
                status.textContent = products.length ? `${products.length} supermarktproducten geladen. Kies een product${value.hasMore?' of toon meer resultaten':''}. Controleer de waarden met je verpakking.` : 'Geen supermarktproducten gevonden. Typ een volledige productnaam of merk, of gebruik de barcode.';
            } catch (error) {
                if (version !== generation || !allowed()) return;
                status.textContent = error instanceof TypeError || error.name === 'AbortError' ? 'Online zoeken niet bereikbaar. Bibliotheekproducten en de barcode blijven beschikbaar.' : error.message;
            } finally { clearTimeout(timeout); if (version === generation) {controller = null;search.disabled=false;more.disabled=false;} }
        }, delay);
    }
    function searchNow(page=1){if(!allowed())return;cancel();wantsOpen=true;input.focus({preventScroll:true});schedule(page);}
    search.addEventListener('click',()=>searchNow());
    more.addEventListener('mousedown',event=>event.preventDefault());
    more.addEventListener('click',()=>searchNow((cache.get(input.value.trim().toLocaleLowerCase('nl-BE'))?.page||1)+1));
    input.addEventListener('input', () => {
        cancel(); wantsOpen = true; manual.hidden = true; select.value = ''; amount.value = ''; amount.disabled = true;
        input.setCustomValidity('Kies een product uit de lijst.'); select.dispatchEvent(new Event('change'));
        render(); schedule();
    });
    input.addEventListener('focus', () => { if (allowed()) { wantsOpen = true; render(cache.get(input.value.trim().toLocaleLowerCase('nl-BE'))?.products || []); } });
    input.addEventListener('keydown', event => {
        if (event.key === 'ArrowDown' && !list.hidden) { event.preventDefault(); move(active + 1); }
        else if (event.key === 'ArrowUp' && !list.hidden) { event.preventDefault(); move(active < 0 ? options.length - 1 : active - 1); }
        else if (event.key === 'Enter') { event.preventDefault(); if (!list.hidden && active >= 0) choose(options[active]);else searchNow(); }
        else if (event.key === 'Escape') { event.preventDefault(); close(); }
    });
    wrapper.addEventListener('focusout',()=>setTimeout(()=>{if(!wrapper.contains(document.activeElement))close();},0));
    document.addEventListener('click', event => { if (!wrapper.contains(event.target)) close(); });
    document.addEventListener('cap:food-product-selected', event => {
        cancel(); close(); manual.hidden = true; input.setCustomValidity(''); amount.disabled = false;
        amount.placeholder = event.detail.unit === 'g' ? 'Bijv. 150' : event.detail.unit === 'ml' ? 'Bijv. 250' : 'Bijv. 1';
        status.textContent = 'Vul de hoeveelheid in. Controleer de voedingswaarden met je verpakking.';
    });
    manual.addEventListener('click', () => { const custom = $('food-custom-form'); custom.closest('details').open = true; custom.elements.namedItem('name').focus(); });
    document.addEventListener('cap:viewchange', () => { if (!allowed()) { cancel(); close(); } });
    document.addEventListener('cap:food-catalogue-changed', () => { if (allowed() && document.activeElement === input) render(cache.get(input.value.trim().toLocaleLowerCase('nl-BE'))?.products || []); });
});
