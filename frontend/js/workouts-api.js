(() => {
    const $ = (selector) => document.querySelector(selector);
    let loading = false;
    let pending = false;
    let visibleLimit = 6;
    let coachType = 'all';
    let coachCategory = 'all';
    const libraryCategories = [['all', 'Alle workouts'], ['CAP HYROX', 'CAP HYROX'], ['CAP Lift & HIT', 'CAP Lift & HIT'],
        ['CAP Hybrid', 'CAP Hybrid'], ['CAP Move', 'CAP Move'], ['Athlete', 'Specifieke sporter'], ['Private', 'Privétraining'], ['Open gym', 'Open gym'], ['', 'Overig']];
    const library = $('#coach-workout-library');
    const categoryChooser = document.createElement('div');
    const chooserTitle = document.createElement('h3'); chooserTitle.id = 'library-category-title'; chooserTitle.tabIndex = -1;
    chooserTitle.textContent = 'Kies een categorie';
    const categoryTiles = document.createElement('div'); categoryTiles.className = 'home-tiles';
    categoryTiles.setAttribute('role', 'group'); categoryTiles.setAttribute('aria-labelledby', chooserTitle.id);
    categoryChooser.append(chooserTitle, categoryTiles);
    const libraryPanel = document.createElement('div'); libraryPanel.hidden = true;
    library.before(categoryChooser, libraryPanel);
    const toolbar = document.createElement('div'); toolbar.className = 'workout-category-toolbar';
    const selectionTitle = document.createElement('h3'); selectionTitle.tabIndex = -1;
    const back = document.createElement('button'); back.type = 'button'; back.className = 'secondary-button'; back.textContent = 'Alle categorieën';
    toolbar.append(selectionTitle, back);
    const searchLabel = document.createElement('label'); searchLabel.className = 'library-search'; searchLabel.textContent = 'Zoek een workout';
    const coachSearch = document.createElement('input'); coachSearch.type = 'search'; coachSearch.placeholder = 'Naam, beschrijving of oefening';
    searchLabel.append(coachSearch);
    libraryPanel.append(toolbar, searchLabel, library);
    const categoryCounts = new Map();
    const bodyTypeCounts = new Map();
    const bodyHeading = document.createElement('h3'); bodyHeading.textContent = 'Kies een workout type';
    const bodyTiles = document.createElement('div'); bodyTiles.className = 'home-tiles';
    chooserTitle.before(bodyHeading, bodyTiles);
    chooserTitle.textContent = 'Of kies een groep';
    [['lower-body', 'Lower body'], ['upper-body', 'Upper body'], ['full-body', 'Full body']].forEach(([type, label]) => {
        const tile = document.createElement('button'); tile.type = 'button'; tile.className = 'home-tile'; tile.dataset.workoutType = type;
        const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', 'M3 6h7l2 2h9v13H3zM3 6V3h7l2 3'); icon.append(path);
        const title = document.createElement('strong'); title.textContent = label;
        const count = document.createElement('span'); count.textContent = 'Nog niet geladen'; bodyTypeCounts.set(type, count);
        tile.append(icon, title, count);
        tile.addEventListener('click', () => {
            coachCategory = 'all'; coachType = type; coachSearch.value = '';
            categoryChooser.hidden = true; libraryPanel.hidden = false;
            filterCoachTypes(); selectionTitle.focus();
        });
        bodyTiles.append(tile);
    });
    function showLibraryCategories(moveFocus = true) {
        categoryChooser.hidden = false; libraryPanel.hidden = true;
        if (moveFocus) chooserTitle.focus();
    }
    back.addEventListener('click', () => showLibraryCategories());
    libraryCategories.forEach(([value, label]) => {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'home-tile';
        const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', value === 'all' ? 'M3 3h7v7H3zm11 0h7v7h-7zM3 14h7v7H3zm11 0h7v7h-7z' : 'M3 6h7l2 2h9v13H3zM3 6V3h7l2 3');
        icon.append(path);
        const title = document.createElement('strong'); title.textContent = label;
        const count = document.createElement('span'); count.textContent = 'Nog niet geladen'; categoryCounts.set(value, count);
        button.append(icon, title, count);
        button.addEventListener('click', () => {
            coachCategory = value; coachType = 'all'; coachSearch.value = '';
            selectionTitle.textContent = label;
            categoryChooser.hidden = true; libraryPanel.hidden = false;
            filterCoachTypes(); selectionTitle.focus();
        });
        categoryTiles.append(button);
    });
    coachSearch.addEventListener('input', filterCoachTypes);
    document.querySelectorAll('a[href="#saved-workouts"]').forEach(link => link.addEventListener('click', () => {
        quickAssign = false;
        quickAssignNotice.textContent = '';
        showLibraryCategories(false);
    }));
    function updateCategoryCounts(workouts) {
        bodyTypeCounts.forEach((element, type) => {
            const count = workouts.filter(workout => workout.workoutType === type).length;
            element.textContent = `${count} ${count === 1 ? 'workout' : 'workouts'}`;
        });
        libraryCategories.forEach(([value]) => {
            const count = value === 'all' ? workouts.length : workouts.filter(workout => (workout.category || '') === value).length;
            categoryCounts.get(value).textContent = `${count} ${count === 1 ? 'workout' : 'workouts'}`;
        });
    }
    function filterCoachTypes() {
        const typeName = { 'lower-body': 'Lower body', 'upper-body': 'Upper body', 'full-body': 'Full body', conditioning: 'Conditioning', other: 'Overig' }[coachType];
        const categoryName = libraryCategories.find(([value]) => value === coachCategory)?.[1] || 'Alle workouts';
        selectionTitle.textContent = typeName ? `${typeName}${coachCategory === 'all' ? '' : ' · ' + categoryName}` : categoryName;
        const query = coachSearch.value.trim().toLocaleLowerCase('nl-BE');
        let total = 0;
        document.querySelectorAll('#coach-workout-library .workout-category').forEach(group => {
            const entries = Array.from(group.querySelectorAll('[data-workout-type]'));
            let count = 0;
            entries.forEach(entry => {
                entry.hidden = (coachType !== 'all' && entry.dataset.workoutType !== coachType) || !entry.dataset.search.includes(query);
                if (!entry.hidden) count++;
            });
            const selectedCategory = coachCategory === 'all' || group.dataset.categoryKey === coachCategory;
            group.hidden = count === 0 || !selectedCategory;
            if (selectedCategory) total += count;
            if (selectedCategory) group.open = true;
            group.querySelector('summary').textContent = group.dataset.category + ' (' + count + ')';
        });
        document.querySelector('#coach-type-empty').hidden = total > 0;
        document.querySelectorAll('#coach-type-filters button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.type === coachType)));
    }
    const typeFilters = document.createElement('div'); typeFilters.id = 'coach-type-filters'; typeFilters.className = 'workout-type-filters';
    typeFilters.setAttribute('role', 'group'); typeFilters.setAttribute('aria-label', 'Filter op workout type');
    [['all','Alles'],['full-body','Full body'],['lower-body','Lower body'],['upper-body','Upper body'],['conditioning','Conditioning'],['other','Overig']].forEach(([type,label]) => {
        const button = document.createElement('button'); button.type='button'; button.className='secondary-button'; button.textContent=label; button.dataset.type=type;
        button.setAttribute('aria-pressed', String(type === coachType));
        button.addEventListener('click', () => { coachType=type; filterCoachTypes(); }); typeFilters.append(button);
    });
    const typeEmpty = document.createElement('p'); typeEmpty.id='coach-type-empty'; typeEmpty.hidden=true; typeEmpty.textContent='Nog geen workouts voor deze selectie.';
    library.before(typeFilters, typeEmpty);

    function filterLibrary() {
        const query = $('#workout-search').value.trim().toLocaleLowerCase('nl-BE');
        const cards = Array.from($('#api-workouts').children);
        const matches = cards.filter(card => card.dataset.search.includes(query));
        cards.forEach(card => { card.hidden = true; });
        matches.slice(0, visibleLimit).forEach(card => { card.hidden = false; });
        $('#show-more-workouts').hidden = matches.length <= visibleLimit;
        $('#workout-results-count').textContent = matches.length
            ? `${Math.min(visibleLimit, matches.length)} van ${matches.length} workouts`
            : 'Geen workouts gevonden.';
    }
    const node = (tag, text) => { const item = document.createElement(tag); item.textContent = text; return item; };
    let viewedWeek = window.capAgenda.monday(window.capAgenda.today());
    const weekToolbar = node('div', ''); weekToolbar.className = 'agenda-week-toolbar';
    const previousWeek = node('button', '←'); previousWeek.type = 'button'; previousWeek.className = 'secondary-button'; previousWeek.setAttribute('aria-label', 'Vorige week');
    const nextWeek = node('button', '→'); nextWeek.type = 'button'; nextWeek.className = 'secondary-button'; nextWeek.setAttribute('aria-label', 'Volgende week');
    const weekTitle = node('strong', '');
    weekToolbar.append(previousWeek, weekTitle, nextWeek); $('#api-workouts').before(weekToolbar);
    const browseWeek = offset => { viewedWeek = window.capAgenda.add(viewedWeek, offset); $('#assigned-workout-period').value = window.capAgenda.add(viewedWeek, 6) < window.capAgenda.today() ? 'past' : 'upcoming'; loadWorkouts(); };
    previousWeek.addEventListener('click', () => browseWeek(-7)); nextWeek.addEventListener('click', () => browseWeek(7));
    const scheduleLabel = workout => workout.weekStart
        ? `Week ${window.capAgenda.range(workout.weekStart)}${workout.assignedDate ? ` · Gepland: ${window.capAgenda.label(workout.assignedDate)}` : ' · Zelf een dag kiezen'}`
        : workout.assignedDate;
    function planDay(workout) {
        if (!window.capAccess.signedIn || window.capTraining.isActive(workout.id)) return;
        const dialog = document.createElement('dialog'); dialog.className = 'club-dialog'; dialog.setAttribute('aria-label', 'Workout op een dag plannen');
        const form = document.createElement('form'); form.className = 'club-form';
        const label = node('label', 'Trainingsdag'); const input = document.createElement('input'); input.type = 'date'; input.required = true;
        input.value = workout.assignedDate || (workout.weekStart > window.capAgenda.today() ? workout.weekStart : window.capAgenda.today()); label.append(input);
        const title = node('h2', workout.name); form.append(title, label);
        const feedback = node('p', ''); feedback.setAttribute('role', 'status');
        const save = node('button', 'Dag bewaren'); save.type = 'submit'; save.className = 'primary-button';
        const flexible = node('button', 'Dag vrijlaten'); flexible.type = 'button'; flexible.className = 'secondary-button';
        const close = node('button', 'Sluiten'); close.type = 'button'; close.className = 'secondary-button'; close.addEventListener('click', () => dialog.close());
        form.append(feedback, save, flexible, close); dialog.append(form); document.body.append(dialog);
        window.capAgenda.mount(input, { dayOnly: true, fixedWeek: workout.weekStart });
        let saving = false;
        async function persist(date) {
            if (saving || !window.capAccess.signedIn) return;
            saving = true;
            const controls = Array.from(form.querySelectorAll('button, input'), item => [item, item.disabled]);
            controls.forEach(([item]) => { item.disabled = true; });
            try {
                await changeAssignment(workout.assignmentId, 'PUT', { date }, '/schedule');
                dialog.close(); await loadWorkouts();
            } catch (error) { feedback.textContent = error.message; }
            finally { saving = false; controls.forEach(([item, disabled]) => { item.disabled = disabled; }); }
        }
        form.addEventListener('submit', event => { event.preventDefault(); if (form.reportValidity()) persist(input.value); });
        flexible.addEventListener('click', () => persist(''));
        dialog.addEventListener('cancel', event => { if (saving) event.preventDefault(); });
        dialog.addEventListener('close', () => dialog.remove()); dialog.showModal();
    }
    let quickAssign = false;
    let reopenAthleteWorkouts = false;
    const quickAssignNotice = node('p', '');
    quickAssignNotice.setAttribute('role', 'status');
    categoryChooser.before(quickAssignNotice);
    $('#assign-athlete-workout').addEventListener('click', () => {
        quickAssign = true;
        coachCategory = 'all'; coachType = 'all'; coachSearch.value = '';
        categoryChooser.hidden = true; libraryPanel.hidden = false;
        filterCoachTypes();
        quickAssignNotice.textContent = 'Kies een workout en plan deze in de agenda voor een week of een dag.';
    });
    function assignToAthlete(workout) {
        if (previewRole !== 'coach') return;
        openAssignment(workout, true);
    }
    const athleteWorkoutsDialog = document.createElement('dialog');
    athleteWorkoutsDialog.className = 'club-dialog';
    athleteWorkoutsDialog.setAttribute('aria-labelledby', 'athlete-workouts-title');
    athleteWorkoutsDialog.innerHTML = `<h2 id="athlete-workouts-title">Workouts van Guillaume</h2>
        <div id="coach-athlete-workouts"><p>Workouts worden geladen. Probeer opnieuw zodra Spring Boot verbonden is.</p></div>
        <button type="button" class="secondary-button">Sluiten</button>`;
    document.body.append(athleteWorkoutsDialog);
    athleteWorkoutsDialog.querySelector('button').addEventListener('click', () => athleteWorkoutsDialog.close());
    $('#review-athlete-workouts').addEventListener('click', () => {
        if (previewRole === 'coach') athleteWorkoutsDialog.showModal();
    });
    let assigningWorkout = null;
    const assignmentDialog = document.createElement('dialog'); assignmentDialog.className = 'club-dialog';
    assignmentDialog.setAttribute('aria-labelledby', 'assign-workout-title');
    assignmentDialog.innerHTML = `<form id="assign-saved-workout-form" class="club-form">
        <h2 id="assign-workout-title">Workout toewijzen</h2><p id="assign-workout-name"></p>
        <label>Toewijzen aan<select id="assignment-target"><option value="athlete">Sporter</option><option value="sessions">Specifieke groepssessies</option></select></label>
        <label>Sporter<select name="athleteId" required><option value="preview-guillaume">Guillaume (voorbeeldsporter)</option></select></label>
        <label>Datum / week kiezen<input name="date" id="assignment-date" type="date" required></label><p class="sample-notice">Kies een flexibele week of een vaste dag. Groepsworkouts worden aan specifieke sessies gekoppeld.</p>
        <div id="assignment-session-options" class="workout-session-options" hidden></div>
        <p id="assign-workout-status" role="status"></p>
        <button type="submit" class="primary-button">Toewijzen</button><button type="button" class="secondary-button" id="close-workout-assignment">Annuleren</button>
    </form>`;
    document.body.append(assignmentDialog);
    const assignmentForm = $('#assign-saved-workout-form');
    let returnToAthlete = false;
    const assignmentAgenda = window.capAgenda.mount($('#assignment-date'), { onChange: () => updateAssignmentTargets() });
    function openAssignment(workout, fromAthlete = false) {
        assigningWorkout = workout; returnToAthlete = fromAthlete;
        assignmentAgenda.reset();
        $('#assignment-target').value = 'athlete'; updateAssignmentTargets();
        $('#assign-workout-name').textContent = workout.name;
        $('#assign-workout-status').textContent = '';
        assignmentDialog.showModal();
    }
    let assignmentSaving = false;
    function updateAssignmentTargets() {
        const forSessions = $('#assignment-target').value === 'sessions';
        assignmentAgenda.setGroup(forSessions);
        assignmentForm.elements.namedItem('athleteId').closest('label').hidden = forSessions;
        const list = $('#assignment-session-options'); list.hidden = !forSessions; list.replaceChildren();
        if (!forSessions || !assigningWorkout) return;
        const selection = assignmentAgenda.value();
        const matches = window.capClub.sessions().filter(session => (selection.mode === 'week'
            ? session.date >= selection.weekStart && session.date <= window.capAgenda.add(selection.weekStart, 6)
            : session.date === selection.date)
            && (!assigningWorkout.category || session.category === assigningWorkout.category));
        matches.forEach(session => {
            const label = node('label', ''); const input = document.createElement('input');
            input.type = 'checkbox'; input.name = 'sessionIds'; input.value = session.id;
            label.append(input, node('span', `${session.date} · ${session.start} · ${session.category || session.title}`)); list.append(label);
        });
        if (!matches.length) list.append(node('p', 'Geen passende sessies op deze datum. Kies een andere datum of voeg eerst een sessie toe in Planning.'));
    }
    $('#assignment-target').addEventListener('change', updateAssignmentTargets);
    $('#assignment-date').addEventListener('change', updateAssignmentTargets);
    $('#close-workout-assignment').addEventListener('click', () => assignmentDialog.close());
    assignmentDialog.addEventListener('cancel', event => { if (assignmentSaving) event.preventDefault(); });
    assignmentForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (assignmentSaving || !assigningWorkout || previewRole !== 'coach' || !window.capAccess.can('workouts')) return;
        if (window.capTraining.isActive(assigningWorkout.id)) { $('#assign-workout-status').textContent = 'Rond de lopende training eerst af.'; return; }
        assignmentSaving = true;
        const controls = Array.from(assignmentForm.querySelectorAll('button, input, select'), item => [item, item.disabled]);
        controls.forEach(([item]) => { item.disabled = true; });
        $('#assign-workout-status').textContent = 'Workout toewijzen…';
        try {
            if ($('#assignment-target').value === 'sessions') {
                const ids = Array.from(document.querySelectorAll('#assignment-session-options input:checked'), input => input.value);
                if (!ids.length) throw new Error('Selecteer minstens één specifieke sessie.');
                window.capClub.assignWorkout(ids, assigningWorkout);
                assignmentDialog.close();
                $('#coach-workout-status').textContent = `Workout gekoppeld aan ${ids.length} gekozen sessie(s). Andere sessies blijven ongewijzigd.`;
                return;
            }
            const response = await fetch(`http://localhost:8080/api/workouts/${encodeURIComponent(assigningWorkout.id)}/assignment`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(8000),
                body: JSON.stringify({ athleteId: assignmentForm.elements.namedItem('athleteId').value,
                    date: assignmentAgenda.value().date,
                    weekStart: assignmentAgenda.value().mode === 'week' ? assignmentAgenda.value().weekStart : null })
            });
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.message || 'Toewijzen mislukt.');
            }
            assignmentDialog.close();
            const confirmation = assignmentAgenda.value().mode === 'week'
                ? `Workout “${assigningWorkout.name}” toegewezen voor de week ${window.capAgenda.range(assignmentAgenda.value().date)}.`
                : `Workout “${assigningWorkout.name}” ingepland op ${assignmentAgenda.value().date}.`;
            $('#coach-workout-status').textContent = confirmation;
            if (returnToAthlete) { quickAssign = false; $('#athlete-action-status').textContent = confirmation; location.hash = 'athletes'; }
            await loadWorkouts();
            retry.focus();
        } catch (error) { $('#assign-workout-status').textContent = `${error.message} Controleer de toewijzing voordat je opnieuw probeert.`; }
        finally {
            assignmentSaving = false;
            controls.forEach(([item, disabled]) => { item.disabled = disabled; });
        }
    });
    async function deleteWorkout(workout, button) {
        if (!window.capAccess.can('workouts') || previewRole !== 'coach') return;
        const status = $('#coach-library-load-status');
        if (window.capTraining.isActive(workout.id)) {
            status.textContent = 'Deze workout heeft een lopende training. Rond die eerst af of verwijder de lopende training.';
            return;
        }
        if (!window.confirm(`Workout "${workout.name}" definitief verwijderen? Ook de toewijzing wordt verwijderd. Afgeronde trainingen en PRs blijven bewaard.`)) return;
        button.disabled = true;
        button.textContent = 'Verwijderen…';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        try {
            const response = await fetch(`http://localhost:8080/api/workouts/${encodeURIComponent(workout.id)}`, {
                method: 'DELETE', signal: controller.signal
            });
            if (!response.ok && response.status !== 404) throw new Error('Delete failed');
            const refreshed = await loadWorkouts();
            if (refreshed) status.textContent = `Workout "${workout.name}" verwijderd.`;
            retry.focus();
        } catch {
            status.textContent = 'Verwijderen kon niet worden bevestigd. Vernieuw de bibliotheek om te controleren en probeer opnieuw.';
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            button.textContent = 'Workout verwijderen';
        }
    }
    function details(workout, onRemove = null) {
        const content = document.createElement('div');
        content.append(node('h4', workout.name));
        if (workout.description) content.append(node('p', workout.description));
        content.append(window.capWorkoutView.render(workout.exercises, onRemove));
        return content;
    }
    async function loadWorkouts() {
        if (loading) { pending = true; return; }
        loading = true;
        $('#reload-workouts').disabled = true;
        $('#api-workouts').setAttribute('aria-busy', 'true');
        $('#workouts-status').textContent = 'Workouts laden…';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        try {
            const responses = await Promise.all(['/api/workouts', '/api/workout-assignments', '/api/training'].map(path => fetch(`http://localhost:8080${path}`, { signal: controller.signal, cache: 'no-store' })));
            if (responses.some(response => !response.ok)) throw new Error('Load failed');
            const [workouts, assignments, history] = await Promise.all(responses.map(response => response.json()));
            const completed = new Set(history.map(session => session.assignmentId).filter(Boolean));
            if (!Array.isArray(assignments)) throw new Error('Invalid assignments');
            if (!Array.isArray(workouts) || !workouts.every(w => typeof w.name === 'string' && Array.isArray(w.exercises))) throw new Error('Invalid data');
            const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
            const allPersonal = assignments.map(a => ({ ...a.planned, assignmentId: a.assignmentId, note: a.note || '', weekStart: a.weekStart || '' })).filter(w => w.athleteId === 'preview-guillaume');
            const personalWorkouts = allPersonal.filter(w => w.weekStart ? w.weekStart === viewedWeek : w.assignedDate >= viewedWeek && w.assignedDate <= window.capAgenda.add(viewedWeek, 6))
                .sort((a, b) => (a.assignedDate || a.weekStart).localeCompare(b.assignedDate || b.weekStart));
            weekTitle.textContent = `Workouts deze week · ${window.capAgenda.range(viewedWeek)}`;
            $('#api-workouts').replaceChildren();
            $('#coach-workout-library').replaceChildren();
            personalWorkouts.forEach(workout => {
                const card = document.createElement('details'); card.className = 'workout-option compact-workout';
                card.dataset.search = `${workout.name} ${workout.description} ${workout.exercises.map(e => e.name).join(' ')}`.toLocaleLowerCase('nl-BE');
                const summary = node('summary', `${workout.name} · ${scheduleLabel(workout)}${completed.has(workout.assignmentId) ? ' · Afgerond' : ''}`);
                const available = !workout.weekStart || !completed.has(workout.assignmentId) && workout.weekStart <= today && window.capAgenda.add(workout.weekStart, 6) >= today;
                card.append(summary);
                card.append(details(workout));
                const select = node('button', 'Bekijk workout'); select.type = 'button'; select.className = 'primary-button';
                select.addEventListener('click', () => {
                    const viewer = document.createElement('dialog'); viewer.className = 'club-dialog workout-viewer';
                    viewer.setAttribute('aria-label', workout.name);
                    const start = node('button', 'Start training'); start.type = 'button'; start.className = 'primary-button';
                    start.hidden = !available;
                    start.addEventListener('click', () => { viewer.close(); window.capTraining.start(workout); });
                    const close = node('button', 'Sluiten'); close.type = 'button'; close.className = 'secondary-button';
                    close.addEventListener('click', () => viewer.close());
                    viewer.append(details(workout), start, close); document.body.append(viewer);
                    viewer.addEventListener('close', () => { viewer.remove(); select.focus(); });
                    viewer.showModal();
                });
                card.append(select);
                $('#api-workouts').append(card);
                const start = node('button', 'Start training'); start.type = 'button'; start.className = 'secondary-button';
                start.hidden = !available;
                start.addEventListener('click', () => window.capTraining.start(workout)); card.append(start);
                if (workout.weekStart && !completed.has(workout.assignmentId) && window.capAgenda.add(workout.weekStart, 6) >= today) {
                    const plan = node('button', workout.assignedDate ? 'Dag wijzigen' : 'Dag kiezen'); plan.type = 'button'; plan.className = 'secondary-button';
                    plan.addEventListener('click', () => planDay(workout)); card.append(plan);
                }
            });
            const categoryLists = new Map();
            ['', 'CAP HYROX', 'CAP Hybrid', 'CAP Move', 'CAP Lift & HIT', 'Private', 'Open gym', 'Athlete'].forEach(category => {
                const group = document.createElement('details'); group.className = 'saved-workout workout-category'; group.dataset.category = category || 'Algemeen / persoonlijk'; group.dataset.categoryKey = category;
                group.append(node('summary', category || 'Algemeen / persoonlijk'));
                const list = document.createElement('div'); group.append(list); categoryLists.set(category, { group, list, count: 0 });
            });
            workouts.forEach(workout => {
                const entry = document.createElement('details'); entry.className = 'saved-workout'; entry.dataset.workoutType = workout.workoutType || 'other';
                entry.dataset.search = `${workout.name} ${workout.description || ''} ${workout.exercises.map(exercise => exercise.name).join(' ')}`.toLocaleLowerCase('nl-BE');
                const edit = node('button', 'Bewerken'); edit.type = 'button'; edit.className = 'secondary-button';
                edit.setAttribute('aria-label', `Workout ${workout.name} bewerken`);
                edit.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    document.dispatchEvent(new CustomEvent('cap:edit-workout', { detail: workout }));
                });
                entry.append(node('summary', workout.name), details(workout));
                entry.querySelector('summary').addEventListener('click', event => {
                    if (!quickAssign) return;
                    event.preventDefault();
                    assignToAthlete(workout);
                });
                if (workout.athleteId && workout.assignedDate) entry.append(node('p', `Toegewezen aan Guillaume · ${workout.assignedDate}`));
                const remove = node('button', 'Workout verwijderen');
                remove.type = 'button'; remove.className = 'secondary-button delete-workout';
                remove.setAttribute('aria-label', `Workout ${workout.name} verwijderen`);
                remove.addEventListener('click', () => deleteWorkout(workout, remove));
                entry.append(remove);
                entry.append(edit);
                const assign = node('button', 'Toewijzen'); assign.type = 'button'; assign.className = 'secondary-button';
                assign.setAttribute('aria-label', `Workout ${workout.name} toewijzen`);
                assign.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    openAssignment(workout);

                });
                entry.append(assign);
                if (quickAssign) {
                    edit.hidden = true; remove.hidden = true; assign.hidden = true;
                    entry.querySelector('summary').setAttribute('aria-label', `${workout.name} vandaag toewijzen aan Guillaume`);
                }
                const category = categoryLists.get(workout.category || '') || categoryLists.get('');
                category.list.prepend(entry); category.count++;
            });
            categoryLists.forEach(({group, count}, category) => {
                group.querySelector('summary').textContent = `${category || 'Algemeen / persoonlijk'} (${count})`;
                if (count) $('#coach-workout-library').append(group);
            });
            updateCategoryCounts(workouts);
            filterCoachTypes();
            filterLibrary();
            const assigned = allPersonal.find(w => w.assignedDate === today || w.weekStart && w.weekStart <= today && window.capAgenda.add(w.weekStart, 6) >= today && !completed.has(w.assignmentId));
            const athleteWorkouts = $('#coach-athlete-workouts');
            athleteWorkouts.replaceChildren();
            if (!allPersonal.length) athleteWorkouts.append(node('p', 'Nog geen workouts toegewezen.'));
            [...allPersonal].sort((a, b) => (b.assignedDate || b.weekStart).localeCompare(a.assignedDate || a.weekStart)).forEach(workout => {
                const entry = document.createElement('details'); entry.className = 'saved-workout';
                const preview = details(workout, async (index, button) => {
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    if (workout.exercises.length === 1) {
                        feedback.textContent = 'Dit is de laatste oefening. Gebruik Volledige workout verwijderen om de volledige workout te verwijderen.';
                        return;
                    }
                    const exercises = workout.exercises.filter((_, i) => i !== index).map(exercise => ({ ...exercise }));
                    exercises.forEach(exercise => {
                        if (exercise.supersetGroup && exercises.filter(item => item.supersetGroup === exercise.supersetGroup).length < 2) exercise.supersetGroup = '';
                    });
                    assignmentSaving = true; button.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'PUT', { ...workout, exercises });
                        workout.exercises = exercises;
                        await loadWorkouts();
                        Array.from($('#coach-athlete-workouts').children).find(item => item.dataset.assignmentId === workout.assignmentId)?.setAttribute('open', '');
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; button.disabled = false; }
                });
                entry.dataset.assignmentId = workout.assignmentId;
                entry.append(node('summary', `${workout.name} · ${scheduleLabel(workout)}`));
                const actions = node('div', ''); actions.className = 'athlete-actions';
                const edit = node('button', 'Bewerken'); edit.type = 'button'; edit.className = 'secondary-button';
                const remove = node('button', 'Volledige workout verwijderen'); remove.type = 'button'; remove.className = 'secondary-button';
                const feedback = node('p', ''); feedback.setAttribute('role', 'status');
                edit.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    document.dispatchEvent(new CustomEvent('cap:edit-assignment', { detail: workout }));
                    if (location.hash === '#create-workout') athleteWorkoutsDialog.close();
                });
                remove.addEventListener('click', async () => {
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    if (!window.confirm(`Workout “${workout.name}” · ${scheduleLabel(workout)} verwijderen bij Guillaume? Het opgeslagen voorbeeld blijft behouden.`)) return;
                    assignmentSaving = true; remove.disabled = true; edit.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'DELETE');
                        await loadWorkouts();
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; remove.disabled = false; edit.disabled = false; }
                });
                const wholeWorkout = node('section', ''); wholeWorkout.className = 'whole-workout-actions';
                const scope = node('div', '');
                scope.append(node('h3', 'Volledige workout'), node('p', 'Verwijder deze volledige workout voor deze datum, inclusief alle oefeningen.'));
                wholeWorkout.append(scope, remove);
                actions.append(edit);
                if (workout.weekStart && !completed.has(workout.assignmentId) && window.capAgenda.add(workout.weekStart, 6) >= today) {
                    const plan = node('button', workout.assignedDate ? 'Dag wijzigen' : 'Dag plannen'); plan.type = 'button'; plan.className = 'secondary-button';
                    plan.addEventListener('click', () => planDay(workout)); actions.append(plan);
                }
                entry.append(wholeWorkout, preview);
                const notePanel = document.createElement('details');
                notePanel.append(node('summary', workout.note ? 'Notitie bekijken / wijzigen' : 'Notitie toevoegen'));
                const form = document.createElement('form'); form.className = 'club-form';
                const label = node('label', 'Notitie voor deze workout');
                const textarea = document.createElement('textarea'); textarea.rows = 3; textarea.maxLength = 2000; textarea.value = workout.note;
                label.append(textarea);
                const save = node('button', 'Notitie bewaren'); save.type = 'submit'; save.className = 'secondary-button';
                form.append(label, save); notePanel.append(form);
                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    assignmentSaving = true; save.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'PUT', { note: textarea.value }, '/note');
                        notePanel.querySelector('summary').textContent = textarea.value.trim() ? 'Notitie bekijken / wijzigen' : 'Notitie toevoegen';
                        feedback.textContent = 'Notitie bewaard.';
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; save.disabled = false; }
                });
                entry.append(actions, notePanel, feedback);
                athleteWorkouts.append(entry);
            });
            $('#home-workout-meta').textContent = assigned ? 'Workout voor vandaag klaar' : 'Jouw trainingen';
            $('#athlete-assignment-status').textContent = assigned ? `${assigned.weekStart ? 'Deze week' : 'Vandaag'}: ${assigned.name}` : 'Geen workout klaar voor vandaag of deze week.';
            $('#athlete-list-assignment').textContent = assigned ? `${assigned.name} · ${scheduleLabel(assigned)}` : 'Geen workout klaar voor vandaag of deze week.';
            $('#coach-library-empty').hidden = workouts.length > 0;
            $('#workouts-status').textContent = personalWorkouts.length ? 'Jouw workouts voor deze week. Kies eventueel een dag of open een workout om te trainen.' : 'Geen persoonlijke workouts in deze week. Gebruik de pijlen voor een andere week. Geboekte groepssessies verschijnen hierboven.';
            $('#coach-library-load-status').textContent = '';
            if (reopenAthleteWorkouts && previewRole === 'coach' && location.hash === '#athletes') {
                reopenAthleteWorkouts = false;
                if (!athleteWorkoutsDialog.open) athleteWorkoutsDialog.showModal();
            }
            return true;
        } catch {
            const message = 'Workouts konden niet worden vernieuwd. Controleer Spring Boot en probeer opnieuw. Eerder geladen gegevens kunnen verouderd zijn.';
            $('#workouts-status').textContent = message;
            $('#coach-library-load-status').textContent = message;
        } finally {
            clearTimeout(timeout); loading = false;
            $('#reload-workouts').disabled = false;
            $('#api-workouts').setAttribute('aria-busy', 'false');
            if (pending) { pending = false; loadWorkouts(); }
        }
    }
    const status = node('p', ''); status.id = 'coach-library-load-status'; status.setAttribute('role', 'status');
    async function changeAssignment(id, method, body, suffix = '') {
        const response = await fetch(`http://localhost:8080/api/workout-assignments/${encodeURIComponent(id)}${suffix}`, {
            method, headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(8000)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Bewaren mislukt. Controleer Spring Boot en probeer opnieuw.');
        }
    }
    document.addEventListener('cap:assignment-saved', () => {
        reopenAthleteWorkouts = true;
        loadWorkouts();
    });
    const retry = node('button', 'Bibliotheek vernieuwen'); retry.type = 'button'; retry.className = 'secondary-button';
    retry.addEventListener('click', loadWorkouts);
    categoryChooser.before(status, retry);
    $('#reload-workouts').addEventListener('click', loadWorkouts);
    $('#assigned-workout-period').addEventListener('change', () => {
        viewedWeek = window.capAgenda.monday(window.capAgenda.today());
        if ($('#assigned-workout-period').value === 'past') viewedWeek = window.capAgenda.add(viewedWeek, -7);
        loadWorkouts();
    });
    $('#workout-search').addEventListener('input', () => { visibleLimit = 6; filterLibrary(); });
    $('#show-more-workouts').addEventListener('click', () => { visibleLimit += 6; filterLibrary(); });
    document.addEventListener('cap:workouts-saved', () => { showLibraryCategories(false); loadWorkouts(); });
    document.addEventListener('cap:viewchange', () => {
        if (location.hash !== '#saved-workouts') {
            quickAssign = false;
            quickAssignNotice.textContent = '';
        }
        if (window.capAccess.signedIn && ['dashboard', 'saved-workouts', 'workouts', 'athletes', ''].includes(location.hash.slice(1))) loadWorkouts();
    });
})();
