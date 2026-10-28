// Portion arithmetic keeps full precision; only presentation is rounded.
(() => {
    const keys = ['calories', 'proteinG', 'carbsG', 'fatG'];
    function values(raw) {
        return Object.fromEntries(keys.map(key => {
            const value = Number(raw[key]);
            if (raw[key] == null || String(raw[key]).trim() === '' || !Number.isFinite(value) || value < 0) throw new Error('Vul alle voedingswaarden in met nul of een positief getal.');
            return [key, value];
        }));
    }
    function portion(food, rawAmount) {
        const amount = Number(rawAmount);
        if (!food?.complete || !Number.isFinite(food.baseAmount) || food.baseAmount <= 0) throw new Error('Dit product heeft nog geen volledige voedingswaarden. Kies een ander product of vul het zelf in.');
        if (String(rawAmount ?? '').trim() === '' || !Number.isFinite(amount) || amount <= 0) throw new Error('Vul een positieve hoeveelheid in.');
        const base = values(food);
        const result = Object.fromEntries(keys.map(key => [key, base[key] * amount / food.baseAmount]));
        if (!Object.values(result).every(Number.isFinite)) throw new Error('Deze hoeveelheid is te groot.');
        return result;
    }
    function totals(entries) {
        return entries.reduce((sum, entry) => {
            keys.forEach(key => { sum[key] += entry[key]; }); return sum;
        }, Object.fromEntries(keys.map(key => [key, 0])));
    }
    function validDay(day, latest) {
        return /^\d{4}-\d{2}-\d{2}$/.test(day) && Number.isFinite(Date.parse(day)) && new Date(day).toISOString().slice(0, 10) === day && day <= latest;
    }
    function macroShares(total) {
        const energy = [total.proteinG * 4, total.carbsG * 4, total.fatG * 9];
        const sum = energy.reduce((a, b) => a + b, 0);
        if (!sum) return [0, 0, 0];
        const exact = energy.map(value => value / sum * 100);
        const shares = exact.map(Math.floor);
        const order = exact.map((value, index) => ({ index, remainder: value - shares[index] })).sort((a, b) => b.remainder - a.remainder);
        // Distribute rounding once, keeping the displayed percentages at 100%.
        const remaining = 100 - shares.reduce((a, b) => a + b, 0);
        order.slice(0, remaining).forEach(item => { shares[item.index] += 1; });
        return shares;
    }
    window.capFoodMath = { values, portion, totals, validDay, macroShares };
})();

document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const math = window.capFoodMath;
    const foods = window.capFoodDatabase.foods;
    const athleteId = window.capReal?.account.id || 'preview-guillaume';
    const meals = [['breakfast', 'Ontbijt'], ['snack', 'Tussendoor'], ['lunch', 'Middagmaal'], ['dinner', 'Avondmaal'], ['bedtime', 'Voor slapen'], ['pre', 'Pre-workout'], ['post', 'Post-workout']];
    const metrics = [['calories', 'Calorieën', 'kcal'], ['proteinG', 'Eiwitten', 'g'], ['carbsG', 'Koolhydraten', 'g'], ['fatG', 'Vetten', 'g']];
    const real = window.capReal;
    const entries = real ? new Map() : window.capStorage.map('food-entries');
    // Recipes stay browser-local, separated by account from the demo recipes.
    const recipeKey = real ? `saved-meals-${athleteId}` : 'saved-meals';
    const storedMeals = window.capStorage.read(recipeKey, []);
    const savedMeals = Array.isArray(storedMeals) ? storedMeals : [];
    let removed = null;
    let busy = false;
    let loadVersion = 0;
    const loadStates = new Map();
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const format = value => new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(value);
    const normalize = value => String(value || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('nl-BE');
    const canLog = () => window.capAccess.signedIn && previewRole === 'athlete';
    const node = (tag, text, cls) => { const item = document.createElement(tag); if (text != null) item.textContent = text; if (cls) item.className = cls; return item; };
    const product = () => foods.find(food => food.id === $('food-product').value);
    const coachAthlete = () => window.capNutritionTargets.selectedAthlete();
    if (real) $('food-diary-storage-note').textContent = 'Je dagboek wordt in je account bewaard. Je coach kan het bekijken.';
    const dayKey = (id, date) => `${id}/${date}`;
    async function refreshDiary() {
        if (!real || !window.capAccess.signedIn || busy) return;
        const prefix = previewRole === 'coach' ? 'coach-food' : 'athlete-food';
        const id = prefix === 'coach-food' ? coachAthlete()?.id : athleteId;
        const date = $(prefix + '-date').value;
        const version = ++loadVersion;
        if (!id || !math.validDay(date, today())) { render(); return; }
        const key = dayKey(id, date);
        loadStates.set(key, 'Dagboek laden...'); render();
        try {
            const rows = await real.request(`/api/food-diary?date=${encodeURIComponent(date)}${prefix === 'coach-food' ? `&athleteId=${encodeURIComponent(id)}` : ''}`);
            if (version !== loadVersion || !window.capAccess.signedIn) return;
            entries.set(id, [...(entries.get(id) || []).filter(entry => entry.date !== date), ...rows]);
            loadStates.set(key, '');
        } catch (error) {
            if (version !== loadVersion) return;
            loadStates.set(key, error.message);
        }
        render();
    }
    async function mutate(action) {
        if (busy || !canLog()) return false;
        busy = true; ++loadVersion;
        document.querySelectorAll('#food-entry-form button, #food-custom-form button, #food-saved-meals button, #athlete-food-entries button, #food-undo').forEach(button => { button.disabled = true; });
        try { await action(); return true; }
        finally {
            busy = false;
            document.querySelectorAll('#food-entry-form button, #food-custom-form button, #food-undo').forEach(button => { button.disabled = false; });
            render(); renderSavedMeals();
            refreshDiary();
        }
    }
    meals.forEach(([value, label]) => $('food-meal').add(new Option(label, value)));
    ['athlete-food-date', 'coach-food-date'].forEach(id => { $(id).value = today(); $(id).max = today(); });
    function renderProducts() {
        const query = normalize($('food-search').value).trim().split(/\s+/).filter(Boolean);
        const selected = $('food-product').value;
        const matches = foods.filter(food => query.every(word => normalize(food.name).includes(word))).sort((a, b) => a.name.localeCompare(b.name, 'nl-BE'));
        $('food-product').replaceChildren(new Option(matches.length ? 'Kies een product' : 'Geen product gevonden', ''));
        matches.forEach(food => {
            const option = new Option(`${food.name}${food.complete ? '' : ' · voedingswaarden ontbreken'}`, food.id);
            option.disabled = !food.complete; $('food-product').add(option);
        });
        $('food-product').value = matches.some(food => food.id === selected && food.complete) ? selected : '';
        previewPortion();
    }
    function previewPortion() {
        const food = product();
        $('food-unit').textContent = food ? `(${food.unit})` : '';
        try {
            const values = math.portion(food, $('food-amount').value);
            renderChart(values);
            $('food-portion-preview').textContent = `${format(values.calories)} kcal · ${format(values.proteinG)} g eiwit · ${format(values.carbsG)} g koolhydraten · ${format(values.fatG)} g vet`;
        } catch {
            renderChart({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 });
            $('food-portion-preview').textContent = food ? `Bronwaarden per ${format(food.baseAmount)} ${food.unit}. Vul in hoeveel je hebt gegeten.` : 'Kies een product. Ontbrekende producten kun je hieronder zelf invullen.';
        }
    }
    function renderChart(total) {
        const chart = $('food-portion-chart'); chart.replaceChildren();
        const shares = math.macroShares(total);
        const ring = node('div', null, 'macro-ring');
        const hasMacros = shares.some(Boolean);
        ring.setAttribute('style', hasMacros ? `background: conic-gradient(var(--macro-protein) 0% ${shares[0]}%, var(--macro-carbs) ${shares[0]}% ${shares[0] + shares[1]}%, var(--macro-fat) ${shares[0] + shares[1]}% 100%)` : 'background: var(--border)');
        ring.setAttribute('aria-hidden', 'true');
        const center = node('div', null, 'macro-ring-center');
        center.append(node('strong', format(total.calories)), node('span', 'kcal')); ring.append(center);
        const legend = node('div', null, 'macro-legend');
        legend.append(node('h4', 'Deze portie'), node('p', `${format(total.calories)} kcal`, 'sample-notice'));
        [['Eiwitten', 'protein'], ['Koolhydraten', 'carbs'], ['Vetten', 'fat']].forEach(([label, color], index) => {
            const row = node('div', null, 'macro-legend-row');
            const dot = node('span', null, `macro-dot macro-dot-${color}`); dot.setAttribute('aria-hidden', 'true');
            row.append(dot, node('span', label), node('strong', `${shares[index]}%`)); legend.append(row);
        });
        legend.append(node('p', hasMacros ? 'Percentage van macrocalorieën: eiwit en koolhydraten 4 kcal/g, vet 9 kcal/g.' : 'Kies een product en vul de hoeveelheid in.', 'sample-notice'));
        chart.append(ring, legend);
    }
    function renderView(prefix, id) {
        const date = $(prefix + '-date').value;
        const totalsContainer = $(prefix + '-totals'); const list = $(prefix + '-entries');
        totalsContainer.replaceChildren(); list.replaceChildren();
        if (!math.validDay(date, today())) { $(prefix + '-status').textContent = 'Kies een geldige datum tot en met vandaag.'; return; }
        if (real && id && loadStates.get(dayKey(id, date)) !== '') {
            $(prefix + '-status').textContent = loadStates.get(dayKey(id, date)) || 'Open deze pagina om het dagboek te laden.';
            return;
        }
        if (!id) { $(prefix + '-status').textContent = 'Kies eerst een sporter.'; return; }
        const dayEntries = (entries.get(id) || []).filter(entry => entry.date === date);
        const dayTotals = math.totals(dayEntries);
        const targets = window.capNutritionTargets.get(id)?.targets;
        $(prefix + '-status').textContent = `${dayEntries.length} ${dayEntries.length === 1 ? 'product' : 'producten'} gelogd${targets ? ' · vergelijking met huidige dagdoelen' : ' · nog geen dagdoelen van de coach'}.`;
        metrics.forEach(([key, label, unit]) => {
            const group = node('div'); group.dataset.nutrient = key; group.append(node('dt', label), node('dd', `${format(dayTotals[key])} ${unit}`));
            if (targets) {
                const difference = targets[key] - dayTotals[key];
                group.append(node('small', `Doel ${format(targets[key])} ${unit} · ${format(Math.abs(difference))} ${unit} ${difference >= 0 ? 'over' : 'boven doel'}`));
            }
            if (prefix === 'athlete-food') {
                const color = { proteinG: 'protein', carbsG: 'carbs', fatG: 'fat', calories: 'calories' }[key];
                const track = node('div', null, `macro-bar macro-bar-${color}`);
                const fill = node('span');
                const goal = targets?.[key];
                const percent = goal > 0 ? Math.min(100, dayTotals[key] / goal * 100) : 0;
                fill.setAttribute('style', `width: ${percent}%`); track.append(fill);
                track.setAttribute('aria-hidden', 'true'); group.append(track);
                if (!(goal > 0)) group.append(node('small', 'Geen positief dagdoel ingesteld.'));
            }
            totalsContainer.append(group);
        });
        meals.forEach(([mealId, label]) => {
            const mealEntries = dayEntries.filter(entry => entry.meal === mealId);
            if (!mealEntries.length) return;
            const group = node('section', null, 'food-meal-group'); group.append(node('h4', label));
            mealEntries.forEach(entry => {
                const row = node('div', null, 'food-diary-row');
                const description = node('div'); description.append(node('strong', entry.name), node('small', `${format(entry.amount)} ${entry.unit} · ${format(entry.calories)} kcal · E ${format(entry.proteinG)} / KH ${format(entry.carbsG)} / V ${format(entry.fatG)} g`));
                row.append(description);
                if (prefix === 'athlete-food') {
                    const remove = node('button', 'Verwijderen', 'secondary-button'); remove.type = 'button';
                    remove.setAttribute('aria-label', `${entry.name} uit dagboek verwijderen`);
                    remove.addEventListener('click', async () => {
                        if (!canLog()) return;
                        try {
                            await mutate(async () => {
                                if (real) await real.request(`/api/food-diary/${entry.id}`, 'DELETE');
                                removed = entry; entries.set(athleteId, (entries.get(athleteId) || []).filter(item => item.id !== entry.id));
                            });
                            $('food-undo').focus();
                        } catch (error) { $('food-entry-feedback').textContent = error.message; }
                    }); row.append(remove);
                }
                group.append(row);
            });
            if (prefix === 'athlete-food') {
                const savePanel = node('details', null, 'saved-workout');
                savePanel.append(node('summary', 'Bewaren als vaste maaltijd'));
                const form = node('form', null, 'saved-meal-form');
                const label = node('label', 'Naam van de maaltijd');
                const name = node('input'); name.type = 'text'; name.required = true; name.maxLength = 80; name.placeholder = 'Bijv. Overnight oats';
                label.append(name);
                const save = node('button', 'Maaltijd bewaren', 'secondary-button'); save.type = 'submit';
                const feedback = node('p'); feedback.setAttribute('role', 'status');
                form.append(label, save, feedback); savePanel.append(form); group.append(savePanel);
                form.addEventListener('submit', event => {
                    event.preventDefault(); if (!canLog() || !form.reportValidity()) return;
                    const title = name.value.trim();
                    if (!title || title.length > 80) { feedback.textContent = 'Vul een naam in (maximaal 80 tekens).'; return; }
                    if (savedMeals.some(item => normalize(item.name) === normalize(title))) { feedback.textContent = 'Deze naam bestaat al. Kies een andere naam.'; return; }
                    // Copy ingredient amounts and values; future diary changes cannot alter the recipe.
                    savedMeals.push({ id: crypto.randomUUID(), name: title, ingredients: mealEntries.map(({ id, date, meal, athleteId, ...ingredient }) => ({ ...ingredient })) });
                    renderSavedMeals(); feedback.textContent = `${title} bewaard. Je kunt deze nu opnieuw toevoegen via Opgeslagen maaltijd toevoegen.`;
                });
            }
            list.append(group);
        });
        if (!dayEntries.length) list.append(node('p', 'Nog niets gelogd op deze dag.', 'sample-notice'));
    }
    function render() {
        renderView('athlete-food', athleteId);
        const coach = coachAthlete(); $('coach-food-athlete').textContent = coach?.name || 'Kies een sporter';
        renderView('coach-food', coach?.id);
        $('food-undo').hidden = !removed || removed.date !== $('athlete-food-date').value;
    }
    function renderSavedMeals() {
        window.capStorage.write(recipeKey, savedMeals);
        const container = $('food-saved-meals'); container.replaceChildren();
        if (!savedMeals.length) container.append(node('p', 'Je hebt nog geen vaste maaltijden bewaard.', 'sample-notice'));
        savedMeals.forEach(recipe => {
            const row = node('div', null, 'food-diary-row'); const description = node('div');
            description.append(node('strong', recipe.name), node('small', `${recipe.ingredients.length} ingrediënten · ${format(math.totals(recipe.ingredients).calories)} kcal`));
            const ingredients = node('details'); ingredients.append(node('summary', 'Ingrediënten'));
            recipe.ingredients.forEach(item => ingredients.append(node('p', `${item.name} · ${format(item.amount)} ${item.unit}`)));
            description.append(ingredients); row.append(description);
            const add = node('button', 'Toevoegen', 'secondary-button'); add.type = 'button'; add.setAttribute('aria-label', `${recipe.name} toevoegen`);
            add.addEventListener('click', async () => {
                if (!canLog()) return;
                try {
                    // One diary portion retains its ingredient snapshot for later reuse.
                    if (await addEntry({ ...math.totals(recipe.ingredients), name: recipe.name, amount: 1, unit: 'maaltijd', foodId: null, ingredients: recipe.ingredients.map(item => ({ ...item })) }))
                        $('food-saved-feedback').textContent = `${recipe.name} toegevoegd aan de gekozen datum en maaltijd.`;
                } catch (error) { $('food-saved-feedback').textContent = error.message; }
            });
            const remove = node('button', 'Verwijderen', 'secondary-button'); remove.type = 'button'; remove.setAttribute('aria-label', `${recipe.name} uit vaste maaltijden verwijderen`);
            remove.addEventListener('click', () => { if (!canLog()) return; savedMeals.splice(savedMeals.indexOf(recipe), 1); renderSavedMeals(); });
            row.append(add, remove); container.append(row);
        });
    }
    async function addEntry(values) {
        if (!canLog()) return;
        const date = $('athlete-food-date').value; const meal = $('food-meal').value;
        if (!math.validDay(date, today())) throw new Error('Kies een geldige datum tot en met vandaag.');
        if (!meals.some(([key]) => key === meal)) throw new Error('Kies een maaltijd.');
        return mutate(async () => {
            const input = { ...values, id: crypto.randomUUID(), date, meal };
            const entry = real ? await real.request('/api/food-diary', 'POST', input) : { ...input, athleteId };
            entries.set(athleteId, [...(entries.get(athleteId) || []), entry]);
            $('food-entry-feedback').textContent = `${values.name} toegevoegd aan je dagboek${real ? ' en bewaard in je account' : ''}.`;
        });
    }
    $('food-entry-form').addEventListener('submit', async event => {
        event.preventDefault(); if (!canLog() || !event.currentTarget.reportValidity()) return;
        try {
            const food = product(); const amount = Number($('food-amount').value);
            const values = math.portion(food, $('food-amount').value);
            if (await addEntry({ ...values, name: food.name, foodId: food.id, amount, unit: food.unit })) {
                $('food-amount').value = ''; previewPortion();
            }
        } catch (error) { $('food-entry-feedback').textContent = error.message; }
    });
    $('food-custom-form').addEventListener('submit', async event => {
        event.preventDefault(); if (!canLog() || !event.currentTarget.reportValidity()) return;
        try {
            const raw = Object.fromEntries(new FormData(event.currentTarget)); const name = raw.name.trim();
            if (!name || name.length > 120) throw new Error('Vul een product of portie in (maximaal 120 tekens).');
            const form = event.currentTarget;
            if (await addEntry({ ...math.values(raw), name, foodId: null, amount: 1, unit: 'portie' })) form.reset();
        } catch (error) { $('food-entry-feedback').textContent = error.message; }
    });
    $('food-undo').addEventListener('click', async () => {
        if (!canLog() || !removed) return;
        try {
            await mutate(async () => {
                const restored = real ? await real.request('/api/food-diary', 'POST', removed) : removed;
                entries.set(athleteId, [...(entries.get(athleteId) || []), restored]); removed = null;
            });
        } catch (error) { $('food-entry-feedback').textContent = error.message; }
    });
    $('food-search').addEventListener('input', renderProducts);
    $('food-product').addEventListener('change', () => { $('food-amount').value = ''; previewPortion(); });
    $('food-amount').addEventListener('input', previewPortion);
    ['athlete-food-date', 'coach-food-date'].forEach(id => $(id).addEventListener('change', () => { $('food-entry-feedback').textContent = ''; render(); refreshDiary(); }));
    document.addEventListener('cap:nutrition-athlete-changed', () => { render(); refreshDiary(); });
    document.addEventListener('cap:nutrition-targets-changed', render);
    document.addEventListener('cap:viewchange', () => {
        ['athlete-food-date', 'coach-food-date'].forEach(id => { $(id).max = today(); });
        if (!window.capAccess.signedIn) {
            if (real) { ++loadVersion; entries.clear(); loadStates.clear(); }
            $('food-saved-feedback').textContent = '';
            removed = null; $('food-search').value = ''; $('food-product').value = ''; $('food-meal').value = meals[0][0]; $('food-amount').value = ''; $('food-custom-form').reset();
            $('food-entry-feedback').textContent = ''; $('athlete-food-date').value = today(); $('coach-food-date').value = today(); renderProducts();
        }
        render();
        if (['#nutrition', '#coach-nutrition'].includes(location.hash)) refreshDiary();
    });
    window.addEventListener('focus', () => { if (['#nutrition', '#coach-nutrition'].includes(location.hash)) refreshDiary(); });
    renderProducts(); render(); renderSavedMeals(); refreshDiary();
});
