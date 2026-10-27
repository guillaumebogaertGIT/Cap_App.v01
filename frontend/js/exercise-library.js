// Browse the same coach catalog as the editor; add several exercises before returning.
(() => {
    const $ = id => document.getElementById(id);
    const catalog = window.capExercisePicker;
    const api = window.capExerciseCatalog;
    const definitionForm = $('exercise-definition-form');
    const definitionFields = ['name', 'region', 'category', 'subcategory', 'instructions', 'notes'];
    let editingId = null;
    let dirty = false;
    let saving = false;
    let loading = false;
    const isCoach = () => previewRole === 'coach' && window.capAccess.signedIn;
    const node = (tag, text, className) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (className) item.className = className;
        return item;
    };
    let limit = 24;
    const filterFields = ['region', 'category', 'subcategory'];
    const filterLabels = { region: "Full body / alle regio's", category: 'Alle categorieën', subcategory: 'Alle bewegingen' };
    const filters = () => ({ ...Object.fromEntries(filterFields.map(field => [field, $(`exercise-filter-${field}`).value])), status: $('exercise-library-state').value });
    function updateFilters() {
        const parents = { status: $('exercise-library-state').value };
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
        $('exercise-library-feedback').textContent = exercise.name + ' toegevoegd. Voeg meer oefeningen toe of ga terug naar je workout om op te slaan.';
    }
    function renderDraft() {
        const draft = window.capWorkoutDraft.summary();
        const choosing = Boolean(draft.name) && $('exercise-library-state').value === 'active';
        $('exercise-library-open').parentElement.hidden = !draft.name;
        document.querySelector('.exercise-library-selection').hidden = !choosing;
        $('exercise-selection-help').hidden = !choosing;
        const typeName = { 'upper-body': 'Upper body', 'lower-body': 'Lower body', 'full-body': 'Full body' }[draft.type] || 'Alle oefeningen';
        const exerciseCount = `${draft.count} ${draft.count === 1 ? 'oefening' : 'oefeningen'}`;
        $('exercise-library-draft').textContent = `${draft.name || 'Naamloze workout'} · ${typeName} · ${exerciseCount} · Nog niet opgeslagen`;
        $('exercise-library-open').textContent = 'Terug naar mijn workout';
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
        const selectingForDraft = Boolean(window.capWorkoutDraft.summary().name) && $('exercise-library-state').value === 'active';
        (selectingForDraft ? matches : []).forEach(exercise => {
            const option = node('option', exercise.name + ' — ' + exercise.category + ' / ' + (exercise.subcategory || exercise.region));
            option.value = exercise.id; select.append(option);
        });
        select.value = matches.some(item => item.id === previous) ? previous : '';
        select.disabled = !matches.length;
        updateSelection();
        const list = $('exercise-library-list'); list.replaceChildren();
        $('exercise-library-count').textContent = !api.ready ? (loading ? 'Oefenbibliotheek laden…' : 'Laad de bibliotheek om oefeningen te bekijken.') : matches.length ? `${Math.min(limit, matches.length)} van ${matches.length} oefeningen · alfabetisch` : 'Geen oefeningen gevonden. Pas je zoekterm, filters of weergave aan.';
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
            const actions = node('div', null, 'exercise-management-actions');
            const edit = node('button', 'Bewerken', 'secondary-button'); edit.type = 'button';
            edit.setAttribute('aria-label', exercise.name + ' bewerken');
            edit.addEventListener('click', () => openDefinition(exercise));
            const archive = node('button', exercise.archived ? 'Herstellen' : 'Archiveren', 'secondary-button'); archive.type = 'button';
            archive.setAttribute('aria-label', exercise.name + (exercise.archived ? ' herstellen' : ' archiveren'));
            archive.addEventListener('click', () => changeArchive(exercise, archive));
            edit.disabled = saving || loading; archive.disabled = saving || loading;
            actions.append(edit, archive);
            const add = node('button', 'Aan workout toevoegen', 'secondary-button'); add.type = 'button';
            add.setAttribute('aria-label', `${exercise.name} toevoegen aan de workout`);
            const draft = window.capWorkoutDraft.summary();
            add.hidden = !draft.name || exercise.archived;
            add.disabled = saving || loading || !catalog.matchesType(exercise, draft.type);
            if (draft.name && !exercise.archived && !catalog.matchesType(exercise, draft.type)) content.append(node('small', 'Buiten je workout type.'));
            add.addEventListener('click', () => addToDraft(exercise));
            actions.append(add); item.append(content, actions); list.append(item);
        });
        $('exercise-library-more').hidden = matches.length <= limit;
    }
    function openDefinition(exercise = null) {
        if (!isCoach() || !api.ready || saving || loading) return;
        if (dirty && !window.confirm('Je onopgeslagen wijzigingen vervangen?')) return;
        editingId = exercise?.id || null;
        definitionForm.reset();
        definitionFields.forEach(field => { definitionForm.elements.namedItem(field).value = exercise?.[field] || ''; });
        $('exercise-definition-title').textContent = exercise ? 'Oefening bewerken' : 'Nieuwe oefening';
        $('exercise-definition-status').textContent = '';
        definitionForm.hidden = false; dirty = false;
        definitionForm.scrollIntoView({ block: 'start' });
        definitionForm.elements.namedItem('name').focus({ preventScroll: true });
    }
    function closeDefinition() {
        if (saving || (dirty && !window.confirm('Onopgeslagen wijzigingen weggooien?'))) return;
        definitionForm.hidden = true; dirty = false; editingId = null;
        $('exercise-new').focus();
    }
    function setSaving(value) {
        saving = value;
        definitionForm.querySelectorAll('input, select, textarea, button').forEach(input => { input.disabled = value; });
        $('exercise-new').disabled = value || loading || !api.ready;
        render();
    }
    async function changeArchive(exercise) {
        if (!isCoach() || saving || loading) return;
        if (!exercise.archived && !window.confirm(`“${exercise.name}” archiveren? Je kunt de oefening later herstellen. Bestaande workouts en historiek blijven behouden.`)) return;
        if (!definitionForm.hidden) {
            $('exercise-library-feedback').textContent = 'Sla je bewerking eerst op of annuleer ze voordat je een oefening archiveert of herstelt.';
            definitionForm.scrollIntoView({ block: 'start' });
            return;
        }
        setSaving(true);
        try {
            await api.archive(exercise.id, !exercise.archived);
            $('exercise-library-feedback').textContent = exercise.name + (exercise.archived ? ' hersteld.' : ' gearchiveerd. Je vindt de oefening onder Gearchiveerde oefeningen.');
        } catch (error) { $('exercise-library-feedback').textContent = error.message; }
        finally { setSaving(false); }
    }
    definitionForm.addEventListener('input', () => { dirty = true; });
    definitionForm.addEventListener('change', () => { dirty = true; });
    definitionForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (!isCoach() || saving || !definitionForm.reportValidity()) return;
        const input = Object.fromEntries(definitionFields.map(field => [field, definitionForm.elements.namedItem(field).value.trim()]));
        setSaving(true);
        $('exercise-definition-status').textContent = 'Opslaan…';
        try {
            const saved = editingId ? await api.update(editingId, input) : await api.create(input);
            definitionForm.hidden = true; dirty = false; editingId = null;
            $('exercise-library-state').value = saved.archived ? 'archived' : 'active';
            filterFields.forEach(field => { $(`exercise-filter-${field}`).value = ''; });
            $('exercise-library-search').value = saved.name;
            updateFilters(); limit = 24; render(); renderDraft(); closeSuggestions();
            $('exercise-library-feedback').textContent = saved.name + ' opgeslagen in de bibliotheek. Bestaande workouts en PR’s behouden hun opgeslagen naam.';
            $('exercise-library-search').focus();
        } catch (error) { $('exercise-definition-status').textContent = error.message; }
        finally { setSaving(false); }
    });
    $('exercise-new').addEventListener('click', () => openDefinition());
    $('exercise-definition-cancel').addEventListener('click', closeDefinition);
    async function loadCatalog() {
        if (loading || saving || !isCoach()) return;
        loading = true; $('exercise-new').disabled = true;
        $('exercise-catalog-status').textContent = 'Oefenbibliotheek laden…'; $('exercise-catalog-retry').hidden = true;
        render();
        try {
            await api.load(true);
            $('exercise-catalog-status').textContent = 'Bibliotheek geladen. Wijzigingen blijven bewaard na vernieuwen.';
        } catch (error) {
            $('exercise-catalog-status').textContent = error.message;
            $('exercise-catalog-retry').hidden = false;
        } finally { loading = false; $('exercise-new').disabled = !api.ready; updateFilters(); renderDraft(); render(); }
    }
    $('exercise-catalog-retry').addEventListener('click', loadCatalog);
    $('exercise-library-state').addEventListener('change', () => {
        updateFilters(); limit = 24; renderDraft(); render(); closeSuggestions();
    });
    document.addEventListener('cap:exercise-catalog-changed', () => {
        const categories = [...new Set(api.entries.map(exercise => exercise.category))].sort((a, b) => a.localeCompare(b, 'nl-BE'));
        $('exercise-categories').replaceChildren(...categories.map(category => { const option = node('option'); option.value = category; return option; }));
        updateFilters(); renderDraft(); render(); closeSuggestions();
    });
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
        if (location.hash === '#exercises' && isCoach()) { renderDraft(); render(); loadCatalog(); }
        if (!isCoach()) { definitionForm.hidden = true; definitionForm.reset(); editingId = null; dirty = false; $('exercise-library-search').value = ''; $('exercise-library-feedback').textContent = ''; limit = 24; filterFields.forEach(field => { $(`exercise-filter-${field}`).value = ''; }); updateFilters(); }
    });
    if (location.hash === '#exercises' && isCoach()) loadCatalog();
})();
