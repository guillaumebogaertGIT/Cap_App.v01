// Session-only coach drafts/targets; athlete preview always represents Guillaume.
document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const calculator = window.capNutritionCalculator;
    const form = $('nutrition-calculator-form');
    const targetsForm = $('nutrition-target-form');
    const field = name => form.elements.namedItem(name);
    const raw = form => Object.fromEntries(new FormData(form));
    const format = value => new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(value);
    const stamp = value => new Intl.DateTimeFormat('nl-BE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Brussels' }).format(new Date(value));
    const canEdit = () => window.capAccess.signedIn && previewRole === 'coach';
    const node = (tag, text) => { const element = document.createElement(tag); if (text != null) element.textContent = text; return element; };
    const targetLabels = [['calories', 'Calorieën', 'kcal'], ['proteinG', 'Eiwitten', 'g'], ['carbsG', 'Koolhydraten', 'g'], ['fatG', 'Vetten', 'g'], ['waterMl', 'Water', 'ml']];
    const defaults = { sex: '', age: '', weightKg: '', heightCm: '', activity: '', calorieGoal: 'maintenance', strategy: 'standard', goalKg: '', scaling: 'standard', bodyFatBand: '', muscularity: 'average' };
    const drafts = new Map();
    const plans = new Map();
    const roster = [];
    document.querySelectorAll('#athletes .membership-group').forEach(group => {
        const membership = group.querySelector('summary').firstChild.textContent.trim();
        group.querySelectorAll('.membership-athlete, .athlete-card').forEach(item => {
            const name = item.classList.contains('athlete-card') ? item.querySelector('h3').textContent.trim() : item.querySelectorAll('span')[1].textContent.trim();
            roster.push({ id: name === 'Guillaume' ? 'preview-guillaume' : `example-${name.toLocaleLowerCase('nl-BE').replaceAll(' ', '-')}`, name, membership });
        });
    });
    roster.sort((a, b) => a.name.localeCompare(b.name, 'nl-BE'));
    let selectedId = roster.find(athlete => athlete.id === 'preview-guillaume')?.id || roster[0]?.id;
    let result = null;
    let calculatedInput = null;
    let stale = false;
    const selected = () => roster.find(athlete => athlete.id === selectedId);
    window.capNutritionTargets = {
        get(athleteId) {
            const plan = plans.get(athleteId);
            return window.capAccess.signedIn && plan ? { ...plan, targets: { ...plan.targets } } : null;
        },
        selectedAthlete() { const athlete = selected(); return athlete ? { ...athlete } : null; }
    };
    function fillOptions(name, rows, placeholder) {
        const select = field(name);
        select.replaceChildren();
        if (placeholder) select.add(new Option(placeholder, ''));
        rows.forEach(([value, label]) => select.add(new Option(label, value)));
    }
    fillOptions('activity', calculator.activity, 'Kies activiteit');
    fillOptions('calorieGoal', calculator.calorieGoals);
    fillOptions('strategy', calculator.strategies);
    fillOptions('muscularity', calculator.muscularity);
    function bodyFatOptions(sex, value = '') {
        const select = field('bodyFatBand'); select.replaceChildren(new Option('Kies een categorie', ''));
        (calculator.bodyFat[sex] || []).forEach(([label], index) => select.add(new Option(label, String(index))));
        select.value = value;
    }
    function setFormValues(target, values) {
        target.reset();
        Object.entries(values).forEach(([name, value]) => { const input = target.elements.namedItem(name); if (input) input.value = value ?? ''; });
    }
    function profileInputs() {
        if (selectedId !== 'preview-guillaume') return {};
        const profile = window.capProfile.get();
        const values = {};
        for (const key of ['heightCm', 'weightKg']) if (profile[key]) values[key] = profile[key];
        if (['male', 'female'].includes(profile.sex)) values.sex = profile.sex;
        if (profile.birthDate) {
            const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
            const age = Number(today.slice(0, 4)) - Number(profile.birthDate.slice(0, 4)) - (today.slice(5) < profile.birthDate.slice(5) ? 1 : 0);
            if (age > 0) values.age = String(age);
        }
        return values;
    }
    function remember() {
        if (!selectedId) return;
        drafts.set(selectedId, { inputs: raw(form), targets: raw(targetsForm), result: result ? { ...result } : null, calculatedInput, stale });
    }
    function renderPicker() {
        const query = $('nutrition-athlete-search').value.trim().toLocaleLowerCase('nl-BE');
        const matches = roster.filter(athlete => athlete.name.toLocaleLowerCase('nl-BE').includes(query));
        const select = $('nutrition-athlete-select'); select.replaceChildren(new Option(matches.length ? 'Kies een sporter' : 'Geen sporter gevonden', ''));
        matches.forEach(athlete => select.add(new Option(`${athlete.name} · ${athlete.membership}`, athlete.id)));
        select.value = matches.some(athlete => athlete.id === selectedId) ? selectedId : '';
    }
    function roundedTargets(values) {
        return Object.fromEntries(targetLabels.map(([key]) => [key, ['calories', 'waterMl'].includes(key) ? Math.round(values[key]) : Number(values[key].toFixed(1))]));
    }
    function energyNote(targets) {
        const energy = targets.proteinG * 4 + targets.carbsG * 4 + targets.fatG * 9;
        const difference = energy - targets.calories;
        return `Macro's leveren ${format(energy)} kcal.${Math.abs(difference) > 5 ? ` Verschil met caloriedoel: ${difference > 0 ? '+' : ''}${format(difference)} kcal.` : ''}`;
    }
    function renderCalculation() {
        const details = $('nutrition-calculation-details'); details.replaceChildren();
        if (!result) { details.textContent = 'Nog geen berekening. Handmatige doelen zijn ook mogelijk.'; return; }
        [['Rustverbruik (BMR)', result.bmr, 'kcal'], ['Onderhoud (TDEE)', result.tdee, 'kcal'], ['Rekengewicht', result.effectiveLb / 2.2046, 'kg']].forEach(([label, value, unit]) => {
            const group = node('div'); group.append(node('dt', label), node('dd', `${format(value)} ${unit}`)); details.append(group);
        });
    }
    function refreshTargets() {
        let values;
        try { values = calculator.validateTargets(raw(targetsForm)); } catch { /* Incomplete fields stay unavailable, not zero. */ }
        $('nutrition-energy-note').textContent = values ? energyNote(values) : 'Vul alle vijf dagdoelen in of gebruik de calculator.';
        $('nutrition-save').disabled = !values || stale || !canEdit();
        $('nutrition-save').textContent = selected() ? `Dagdoelen voor ${selected().name} bewaren` : 'Dagdoelen bewaren';
    }
    function refreshInputs() {
        const auto = field('scaling').value === 'auto';
        $('nutrition-body-fat-label').hidden = !auto; field('bodyFatBand').required = auto;
        field('goalKg').required = field('scaling').value !== 'standard';
        $('nutrition-use-profile').hidden = !Object.keys(profileInputs()).length;
    }
    function renderSaved() {
        const plan = plans.get(selectedId);
        $('nutrition-saved-state').textContent = plan ? `Bewaard voor ${plan.name} · ${stamp(plan.savedAt)} · ${plan.origin}` : 'Nog geen dagdoelen bewaard voor deze sporter.';
    }
    function loadAthlete() {
        const draft = drafts.get(selectedId);
        const values = draft?.inputs || { ...defaults, ...profileInputs() };
        bodyFatOptions(values.sex, values.bodyFatBand);
        setFormValues(form, values); setFormValues(targetsForm, draft?.targets || {});
        result = draft?.result || null; calculatedInput = draft?.calculatedInput || null; stale = draft?.stale || false;
        $('nutrition-athlete-name').textContent = selected() ? `${selected().name} · Dagdoelen` : 'Kies een sporter';
        $('nutrition-calculation-status').textContent = stale ? 'Invoer gewijzigd. Bereken opnieuw of kies Handmatig.' : '';
        $('nutrition-save-status').textContent = '';
        refreshInputs(); refreshTargets(); renderCalculation(); renderSaved();
        document.dispatchEvent(new CustomEvent('cap:nutrition-athlete-changed'));
    }
    function renderAthlete() {
        const container = $('athlete-nutrition-targets'); container.replaceChildren();
        const plan = plans.get('preview-guillaume');
        $('athlete-nutrition-status').textContent = plan ? `Van je coach · bijgewerkt ${stamp(plan.savedAt)}` : 'Je coach heeft nog geen dagdoelen voor je bewaard.';
        $('athlete-nutrition-energy-note').textContent = plan ? energyNote(plan.targets) : '';
        if (!plan) return;
        targetLabels.forEach(([key, label, unit]) => {
            const group = node('div'); group.append(node('dt', label), node('dd', `${format(plan.targets[key])} ${unit}`)); container.append(group);
        });
    }
    function markInputsChanged(event) {
        if (event?.target.name === 'sex') bodyFatOptions(field('sex').value);
        refreshInputs();
        if (calculatedInput && JSON.stringify(raw(form)) !== calculatedInput) {
            stale = true; $('nutrition-calculation-status').textContent = 'Invoer gewijzigd. Bereken opnieuw of kies Handmatig voordat je bewaart.';
        }
        $('nutrition-save-status').textContent = ''; refreshTargets(); remember();
    }
    form.addEventListener('input', markInputsChanged);
    form.addEventListener('submit', event => {
        event.preventDefault(); if (!canEdit() || !selected() || !form.reportValidity()) return;
        try {
            result = calculator.calculate(raw(form)); calculatedInput = JSON.stringify(raw(form)); stale = false;
            setFormValues(targetsForm, roundedTargets(result));
            $('nutrition-calculation-status').textContent = 'Dagdoelen berekend. Controleer de waarden rechts of hieronder en bewaar ze.';
            $('nutrition-save-status').textContent = ''; renderCalculation(); refreshTargets(); remember();
        } catch (error) {
            stale = true; $('nutrition-calculation-status').textContent = error.message; refreshTargets(); remember();
        }
    });
    targetsForm.addEventListener('input', () => { $('nutrition-save-status').textContent = ''; refreshTargets(); remember(); });
    $('nutrition-manual').addEventListener('click', () => {
        result = null; calculatedInput = null; stale = false;
        $('nutrition-calculation-status').textContent = 'Handmatige dagdoelen. De calculator wordt niet gebruikt voor deze waarden.';
        renderCalculation(); refreshTargets(); remember(); targetsForm.elements.namedItem('calories').focus();
    });
    targetsForm.addEventListener('submit', event => {
        event.preventDefault(); if (!canEdit() || !selected() || stale || !targetsForm.reportValidity()) return;
        try {
            const targets = calculator.validateTargets(raw(targetsForm));
            const reference = result ? roundedTargets(result) : null;
            const adjusted = reference && targetLabels.some(([key]) => reference[key] !== targets[key]);
            const origin = reference ? adjusted ? 'berekend en aangepast' : 'berekend' : 'handmatig';
            plans.set(selectedId, { athleteId: selectedId, name: selected().name, targets: { ...targets }, origin, savedAt: new Date().toISOString() });
            remember(); renderSaved(); renderAthlete();
            $('nutrition-save-status').textContent = `Dagdoelen bewaard voor ${selected().name} in deze preview.`;
            document.dispatchEvent(new CustomEvent('cap:nutrition-targets-changed'));
        } catch (error) { $('nutrition-save-status').textContent = error.message; }
    });
    $('nutrition-athlete-search').addEventListener('input', renderPicker);
    $('nutrition-athlete-select').addEventListener('change', event => {
        if (!roster.some(athlete => athlete.id === event.target.value)) return;
        remember(); selectedId = event.target.value; loadAthlete();
    });
    $('nutrition-use-profile').addEventListener('click', () => {
        if (!canEdit()) return;
        const values = profileInputs();
        if (values.sex) bodyFatOptions(values.sex);
        Object.entries(values).forEach(([key, value]) => { field(key).value = value; }); markInputsChanged();
    });
    document.addEventListener('cap:profile-changed', refreshInputs);
    document.addEventListener('cap:viewchange', () => {
        if (!window.capAccess.signedIn) {
            drafts.clear(); plans.clear(); result = null; calculatedInput = null; stale = false;
            selectedId = roster.find(athlete => athlete.id === 'preview-guillaume')?.id || roster[0]?.id;
            $('nutrition-athlete-search').value = ''; renderPicker(); loadAthlete(); renderAthlete();
        } else if (location.hash === '#coach-nutrition' && canEdit()) { renderPicker(); refreshInputs(); refreshTargets(); }
        else if (location.hash === '#nutrition') renderAthlete();
    });
    renderPicker(); loadAthlete(); renderAthlete();
});
