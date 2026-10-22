// Browse the same coach catalog as the editor; add several exercises before returning.
(() => {
    const $ = id => document.getElementById(id);
    const catalog = window.capExercisePicker;
    const node = (tag, text, className) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (className) item.className = className;
        return item;
    };
    let limit = 24;
    function renderDraft() {
        const draft = window.capWorkoutDraft.summary();
        const typeName = { 'upper-body': 'Upper body', 'lower-body': 'Lower body', 'full-body': 'Full body' }[draft.type] || 'Alle oefeningen';
        $('exercise-library-draft').textContent = `${typeName} · ${draft.editing ? 'Bewerken' : 'Workout in opbouw'}: ${draft.name || 'Naamloos'} · ${draft.count} oefeningen`;
        $('exercise-library-open').textContent = `Workout bekijken (${draft.count})`;
    }
    function render() {
        const matches = catalog.searchForType($('exercise-library-search').value);
        const list = $('exercise-library-list'); list.replaceChildren();
        $('exercise-library-count').textContent = matches.length ? `${Math.min(limit, matches.length)} van ${matches.length} oefeningen · alfabetisch` : 'Geen oefeningen gevonden. Zoek op een andere naam of categorie.';
        matches.slice(0, limit).forEach(exercise => {
            const item = node('li', null, 'exercise-library-item');
            const content = node('div');
            content.append(node('strong', exercise.name), node('small', [exercise.region, exercise.category, exercise.subcategory].filter(Boolean).join(' · ')));
            const details = node('details', null, 'exercise-library-details');
            details.append(node('summary', 'Details'));
            // Build metadata only when requested, keeping the initial list light.
            details.addEventListener('toggle', () => {
                if (!details.open || details.children.length > 1) return;
                const values = node('dl', null, 'exercise-source-values');
                Object.entries(window.capExerciseDatabase.source.columns).filter(([field]) => field !== 'name').forEach(([field, label]) => {
                    const group = node('div');
                    group.append(node('dt', label), node('dd', exercise[field] == null || exercise[field] === '' ? 'Nog niet ingevuld' : String(exercise[field])));
                    values.append(group);
                });
                details.append(values);
            });
            content.append(details);
            const add = node('button', '+ Toevoegen', 'secondary-button'); add.type = 'button';
            add.setAttribute('aria-label', `${exercise.name} toevoegen aan de workout`);
            add.addEventListener('click', () => {
                if (!window.capWorkoutDraft.addFromCatalog(exercise.id)) {
                    $('exercise-library-feedback').textContent = 'Toevoegen niet gelukt. Open de coachpreview en controleer of je workout maximaal 100 oefeningen bevat.';
                    return;
                }
                renderDraft();
                $('exercise-library-feedback').textContent = `${exercise.name} toegevoegd. Je kunt verder kiezen of je workout bekijken. Bewaar daarna de workout om de wijzigingen op te slaan.`;
            });
            item.append(content, add); list.append(item);
        });
        $('exercise-library-more').hidden = matches.length <= limit;
    }
    $('exercise-library-search').addEventListener('input', () => { limit = 24; render(); });
    $('exercise-library-more').addEventListener('click', () => {
        const firstNew = limit; limit += 24; render();
        $('exercise-library-list').children[firstNew]?.querySelector('button')?.focus();
    });
    $('exercise-library-open').addEventListener('click', () => window.capWorkoutDraft.open());
    document.addEventListener('cap:viewchange', () => {
        if (location.hash === '#exercises' && previewRole === 'coach' && window.capAccess.signedIn) { renderDraft(); render(); }
        if (!window.capAccess.signedIn) { $('exercise-library-search').value = ''; $('exercise-library-feedback').textContent = ''; limit = 24; }
    });
})();
