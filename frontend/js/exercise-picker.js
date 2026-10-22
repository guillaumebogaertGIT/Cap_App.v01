// Shared catalog and one search window for all workout cards.
(() => {
    const database = window.capExerciseDatabase;
    const fields = Object.keys(database.source.columns);
    const normalize = value => String(value || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('nl-BE').replace(/[^a-z0-9]+/g, ' ').trim();
    const grouped = new Map();
    database.exercises.forEach(exercise => {
        const key = JSON.stringify(fields.map(field => exercise[field]));
        if (grouped.has(key)) grouped.get(key).sourceRows.push(exercise.sourceRow);
        else grouped.set(key, { ...exercise, sourceRows: [exercise.sourceRow], search: normalize([exercise.name, exercise.region, exercise.category, exercise.subcategory, exercise.notes].join(' ')) });
    });
    const catalog = Array.from(grouped.values()).sort((a, b) => a.name.localeCompare(b.name, 'nl-BE'));
    const regionForType = type => ({ 'upper-body': 'Upper Body', 'lower-body': 'Lower Body' })[type] || '';
    const matchesType = (exercise, type) => !regionForType(type) || exercise.region === regionForType(type);
    const currentType = () => document.getElementById('workout-kind')?.value || '';
    const typeLabel = () => ({ 'upper-body': 'Upper body', 'lower-body': 'Lower body', 'full-body': 'Full body' })[currentType()] || 'Alle oefeningen';
    function search(query, filters = {}) {
        const words = normalize(query).split(' ').filter(Boolean);
        return catalog.filter(exercise => words.every(word => exercise.search.includes(word)) && ['region', 'category', 'subcategory'].every(field => !filters[field] || exercise[field] === filters[field]));
    }
    const searchForType = (query, type = currentType()) => search(query, { region: regionForType(type) });
    const node = (tag, text, className) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (className) item.className = className;
        return item;
    };
    const dialog = node('dialog', null, 'club-dialog exercise-search-dialog');
    dialog.setAttribute('aria-labelledby', 'exercise-search-title');
    const header = node('div', null, 'health-overview-heading');
    const heading = node('h2', 'Oefening kiezen'); heading.id = 'exercise-search-title';
    const close = node('button', 'Sluiten', 'secondary-button'); close.type = 'button'; header.append(heading, close);
    const scope = node('p', '', 'sample-notice');
    const label = node('label', 'Zoek op naam, beweging of materiaal', 'library-search');
    const query = node('input'); query.type = 'search'; query.maxLength = 100; query.autocomplete = 'off'; query.spellcheck = false; label.append(query);
    const shortcuts = node('div', null, 'exercise-quick-search'); shortcuts.setAttribute('aria-label', 'Snel zoeken');
    const count = node('p', '', 'sample-notice'); count.setAttribute('role', 'status');
    const results = node('ul', null, 'exercise-search-results'); results.setAttribute('aria-label', 'Gevonden oefeningen');
    const more = node('button', 'Meer oefeningen tonen', 'secondary-button'); more.type = 'button';
    const custom = node('details', null, 'exercise-custom'); custom.append(node('summary', 'Eigen oefeningnaam gebruiken'));
    const customHelp = node('p', 'Typ de naam in het zoekvak hierboven.', 'sample-notice');
    const useCustom = node('button', 'Deze naam gebruiken', 'secondary-button'); useCustom.type = 'button'; custom.append(customHelp, useCustom);
    dialog.append(header, scope, label, shortcuts, count, results, more, custom); document.body.append(dialog);
    const controllers = new WeakMap();
    let active = null;
    let limit = 12;
    function choose(name) {
        if (!active?.row.isConnected) { dialog.close(); return; }
        active.input.value = name; active.input.setCustomValidity(''); active.input.dispatchEvent(new Event('change')); dialog.close();
    }
    function render() {
        const matches = searchForType(query.value);
        scope.textContent = `${typeLabel()} · oefeningen uit de coachbibliotheek`;
        count.textContent = matches.length ? `${Math.min(limit, matches.length)} van ${matches.length} oefeningen` : 'Geen match binnen dit workout type. Probeer een andere zoekterm.';
        results.replaceChildren();
        matches.slice(0, limit).forEach(exercise => {
            const item = node('li'); const button = node('button'); button.type = 'button';
            button.append(node('strong', exercise.name), node('small', [exercise.category, exercise.subcategory].filter(Boolean).join(' · ')));
            button.addEventListener('click', () => choose(exercise.name)); item.append(button); results.append(item);
        });
        more.hidden = matches.length <= limit;
        const name = query.value.trim();
        const known = catalog.find(exercise => normalize(exercise.name) === normalize(name));
        useCustom.disabled = !name || name.length > 100 || Boolean(known && !matchesType(known, currentType()));
        customHelp.textContent = known && !matchesType(known, currentType()) ? 'Deze oefening hoort bij een ander workout type.' : name ? `Eigen naam: ${name}` : 'Typ de naam in het zoekvak hierboven.';
    }
    function open(row) {
        const controller = controllers.get(row);
        if (!controller || !row.isConnected) return;
        active = controller; limit = 12; query.value = ''; custom.open = false;
        query.placeholder = currentType() === 'upper-body' ? 'Bijv. press, row of triceps' : currentType() === 'lower-body' ? 'Bijv. squat, deadlift of lunge' : 'Bijv. squat, press of row';
        shortcuts.replaceChildren();
        const terms = currentType() === 'upper-body' ? ['Press', 'Row', 'Pulldown', 'Triceps'] : currentType() === 'lower-body' ? ['Squat', 'Deadlift', 'Lunge', 'Leg curl'] : ['Squat', 'Press', 'Row', 'Biceps'];
        terms.forEach(term => {
            const button = node('button', term, 'secondary-button'); button.type = 'button';
            button.addEventListener('click', () => { query.value = term; limit = 12; render(); query.focus(); }); shortcuts.append(button);
        });
        render(); dialog.showModal(); query.focus();
    }
    function setup(row) {
        const input = row.querySelector('[name="exercise"]'); const button = row.querySelector('.exercise-choice');
        const caption = row.querySelector('.exercise-choice-caption'); const name = row.querySelector('.exercise-choice-name'); const warning = row.querySelector('.exercise-scope-note');
        const refresh = () => {
            name.textContent = input.value || 'Oefening kiezen';
            const exercise = catalog.find(item => item.name === input.value);
            caption.textContent = exercise ? [exercise.category, exercise.subcategory].filter(Boolean).join(' · ') : input.value ? 'Eigen oefening · wijzigen' : `Zoeken in ${typeLabel()}`;
            warning.hidden = !exercise || matchesType(exercise, currentType());
            warning.textContent = warning.hidden ? '' : `Deze oefening valt buiten ${typeLabel()}. Kies een andere of wijzig het workout type.`;
            button.setAttribute('aria-label', input.value ? `${input.value} wijzigen` : 'Oefening kiezen');
        };
        controllers.set(row, { row, input, button, refresh }); button.addEventListener('click', () => open(row)); input.addEventListener('change', refresh); refresh();
    }
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { const target = active; active = null; if (target?.row.isConnected) target.button.focus(); });
    query.addEventListener('input', () => { limit = 12; render(); });
    query.addEventListener('keydown', event => { if (event.key === 'ArrowDown') { event.preventDefault(); results.querySelector('button')?.focus(); } });
    more.addEventListener('click', () => { const firstNew = limit; limit += 12; render(); results.querySelectorAll('button')[firstNew]?.focus(); });
    useCustom.addEventListener('click', () => {
        if (useCustom.disabled) return;
        const known = catalog.find(exercise => normalize(exercise.name) === normalize(query.value));
        choose(known ? known.name : query.value.trim());
    });
    function refreshType() {
        document.querySelectorAll('#exercise-fields .exercise-row').forEach(row => controllers.get(row)?.refresh()); if (dialog.open) render();
    }
    document.addEventListener('cap:workout-type-changed', refreshType);
    document.addEventListener('cap:viewchange', () => { if (dialog.open && (location.hash !== '#create-workout' || !window.capAccess.signedIn)) dialog.close(); });
    window.capExercisePicker = { setup, open, search, searchForType, matchesType, regionForType, catalog, refreshType };
})();
