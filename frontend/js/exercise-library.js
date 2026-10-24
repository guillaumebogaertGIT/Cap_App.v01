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
    const filterFields = ['region', 'category', 'subcategory'];
    const filterLabels = { region: "Full body / alle regio's", category: 'Alle categorieën', subcategory: 'Alle bewegingen' };
    const filters = () => Object.fromEntries(filterFields.map(field => [field, $(`exercise-filter-${field}`).value]));
    function updateFilters() {
        const parents = {};
        filterFields.forEach(field => {
            const select = $(`exercise-filter-${field}`);
            const previous = select.value;
            const values = [...new Set(catalog.search('', parents).map(item => item[field]).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'nl-BE'));
            const all = node('option', filterLabels[field]); all.value = '';
            select.replaceChildren(all);
            values.forEach(value => { const option = node('option', value); option.value = value; select.append(option); });
            select.value = values.includes(previous) ? previous : '';
            parents[field] = select.value;
        });
    }
    function updateSelection() {
        const exercise = catalog.catalog.find(item => item.id === $('exercise-library-select').value);
        const draft = window.capWorkoutDraft.summary();
        const compatible = exercise && draft.name && catalog.matchesType(exercise, draft.type);
        $('exercise-library-add-selected').disabled = !compatible;
        $('exercise-selection-help').textContent = !draft.name
            ? 'Ga naar Workout maken en geef je workout eerst een naam.'
            : exercise && !compatible
            ? 'Deze oefening valt buiten je workout type. Wijzig het workout type om ze toe te voegen.'
            : "De keuzelijst volgt je filters en zoekterm. Full body toont alle regio's uit het Excelbestand.";
    }
    function addToDraft(exercise) {
        if (!window.capWorkoutDraft.addFromCatalog(exercise.id)) {
            $('exercise-library-feedback').textContent = 'Toevoegen niet gelukt. Controleer je workout type en het maximum van 100 oefeningen.';
            return;
        }
        renderDraft();
        $('exercise-library-feedback').textContent = exercise.name + ' toegevoegd. Kies verder of bekijk je workout. Sla daarna je workout op.';
    }
    function renderDraft() {
        const draft = window.capWorkoutDraft.summary();
        const typeName = { 'upper-body': 'Upper body', 'lower-body': 'Lower body', 'full-body': 'Full body' }[draft.type] || 'Alle oefeningen';
        $('exercise-library-draft').textContent = `${typeName} · ${draft.editing ? 'Bewerken' : 'Workout in opbouw'}: ${draft.name || 'Naamloos'} · ${draft.count} oefeningen`;
        $('exercise-library-open').textContent = `Workout bekijken (${draft.count})`;
    }
    function closeSuggestions() {
        $('exercise-library-suggestions').hidden = true;
        $('exercise-library-search').setAttribute('aria-expanded', 'false');
    }
    function renderSuggestions() {
        const input = $('exercise-library-search');
        const list = $('exercise-library-suggestions');
        const matches = input.value.trim() ? catalog.search(input.value, filters()).slice(0, 8) : [];
        list.replaceChildren();
        matches.forEach(exercise => {
            const item = node('li');
            const button = node('button'); button.type = 'button';
            button.append(node('strong', exercise.name), node('small', [exercise.region, exercise.category, exercise.subcategory].filter(Boolean).join(' · ')));
            button.addEventListener('click', () => {
                input.value = exercise.name;
                limit = 24; render();
                $('exercise-library-select').value = exercise.id;
                updateSelection(); closeSuggestions();
                $('exercise-library-select').focus();
            });
            item.append(button); list.append(item);
        });
        list.hidden = !matches.length;
        input.setAttribute('aria-expanded', String(Boolean(matches.length)));
    }
    function render() {
        const matches = catalog.search($('exercise-library-search').value, filters());
        const select = $('exercise-library-select');
        const previous = select.value;
        const placeholder = node('option', matches.length ? 'Kies een oefening (' + matches.length + ')' : 'Geen oefeningen gevonden'); placeholder.value = '';
        select.replaceChildren(placeholder);
        matches.forEach(exercise => {
            const option = node('option', exercise.name + ' — ' + exercise.category + ' / ' + (exercise.subcategory || exercise.region));
            option.value = exercise.id; select.append(option);
        });
        select.value = matches.some(item => item.id === previous) ? previous : '';
        select.disabled = !matches.length;
        updateSelection();
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
            const draft = window.capWorkoutDraft.summary();
            add.disabled = !draft.name || !catalog.matchesType(exercise, draft.type);
            if (add.disabled) content.append(node('small', !draft.name ? 'Geef je workout eerst een naam in Workout maken.' : 'Buiten je workout type. Wijzig het workout type om toe te voegen.'));
            add.addEventListener('click', () => addToDraft(exercise));
            item.append(content, add); list.append(item);
        });
        $('exercise-library-more').hidden = matches.length <= limit;
    }
    updateFilters();
    filterFields.forEach(field => $(`exercise-filter-${field}`).addEventListener('change', () => {
        updateFilters(); limit = 24; render(); closeSuggestions();
    }));
    $('exercise-library-select').addEventListener('change', updateSelection);
    $('exercise-library-add-selected').addEventListener('click', () => {
        const exercise = catalog.catalog.find(item => item.id === $('exercise-library-select').value);
        if (exercise && !$('exercise-library-add-selected').disabled) addToDraft(exercise);
    });
    $('exercise-library-search').addEventListener('input', () => { limit = 24; render(); renderSuggestions(); });
    $('exercise-library-search').addEventListener('focus', renderSuggestions);
    document.querySelector('.exercise-autocomplete').addEventListener('focusout', event => {
        if (!event.currentTarget.contains(event.relatedTarget)) closeSuggestions();
    });
    document.querySelector('.exercise-autocomplete').addEventListener('keydown', event => {
        const buttons = [...$('exercise-library-suggestions').querySelectorAll('button')];
        const index = buttons.indexOf(document.activeElement);
        if (event.key === 'Escape') { closeSuggestions(); $('exercise-library-search').focus(); closeSuggestions(); }
        if (event.key === 'ArrowDown' && !$('exercise-library-suggestions').hidden && buttons.length) {
            event.preventDefault(); buttons[Math.min(index + 1, buttons.length - 1)].focus();
        }
        if (event.key === 'ArrowUp' && index >= 0) {
            event.preventDefault(); (index ? buttons[index - 1] : $('exercise-library-search')).focus();
        }
    });
    $('exercise-library-more').addEventListener('click', () => {
        const firstNew = limit; limit += 24; render();
        $('exercise-library-list').children[firstNew]?.querySelector('button')?.focus();
    });
    $('exercise-library-open').addEventListener('click', () => window.capWorkoutDraft.open());
    document.addEventListener('cap:viewchange', () => {
        closeSuggestions();
        if (location.hash === '#exercises' && previewRole === 'coach' && window.capAccess.signedIn) { renderDraft(); render(); }
        if (!window.capAccess.signedIn) { $('exercise-library-search').value = ''; $('exercise-library-feedback').textContent = ''; limit = 24; filterFields.forEach(field => { $(`exercise-filter-${field}`).value = ''; }); updateFilters(); }
    });
})();
