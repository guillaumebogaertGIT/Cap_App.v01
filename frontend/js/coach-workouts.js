const workoutForm = document.querySelector('#coach-workout-form');
const exerciseFields = document.querySelector('#exercise-fields');
const exerciseTemplate = document.querySelector('#exercise-template');
const addExerciseButton = document.querySelector('#add-exercise');
const destination = document.querySelector('#workout-destination');
const sessionOptions = document.querySelector('#workout-session-options');
const templateSelect = document.querySelector('#workout-template');
const categorySelect = document.querySelector('#workout-category');
const sessionDate = document.querySelector('#workout-session-date');
const athleteDate = document.querySelector('#workout-athlete-date');
const clubToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
// Group the available example athletes by first name, ready for a larger roster.
const athleteSelect = workoutForm.elements.namedItem('athlete');
const athletes = Array.from(athleteSelect.options).map(option => ({ value: option.value, name: option.textContent }));
athleteSelect.replaceChildren();
const athleteLetters = new Map();
athletes.sort((a, b) => a.name.localeCompare(b.name, 'nl-BE')).forEach(athlete => {
    const letter = athlete.name.trim().charAt(0).toUpperCase();
    if (!athleteLetters.has(letter)) {
        const group = document.createElement('optgroup'); group.label = letter;
        athleteLetters.set(letter, group); athleteSelect.append(group);
    }
    athleteLetters.get(letter).append(new Option(athlete.name, athlete.value));
});
athleteDate.min = clubToday(); athleteDate.value = clubToday();
sessionDate.min = clubToday();
let savedTemplates = [];
let templatesLoading = false;
let editingWorkoutId = null;
let editingAssignmentId = null;
const primarySubmit = workoutForm.querySelector('[type="submit"]');
const copySubmit = document.querySelector('#save-workout-copy');
const cancelEdit = document.querySelector('#cancel-workout-edit');
const deleteEditedWorkout = document.querySelector('#delete-edited-workout');
function setEditMode(workout = null) {
    editingAssignmentId = null;
    athleteDate.min = clubToday();
    editingWorkoutId = workout?.id || null;
    document.querySelector('#whole-workout-actions').hidden = !workout;
    document.querySelector('#whole-workout-help').textContent = 'Verwijder de volledige opgeslagen workout, inclusief alle oefeningen en toewijzingen.';
    primarySubmit.textContent = workout ? 'Wijzigingen bewaren' : 'Workout opslaan';
    copySubmit.hidden = !workout; cancelEdit.hidden = !workout;
    destination.disabled = Boolean(workout);
    workoutForm.elements.namedItem('athlete').disabled = Boolean(workout);
    athleteDate.disabled = Boolean(workout);
    document.querySelector('#create-workout-title').textContent = workout ? 'Workout bewerken' : 'Workout maken';
}
async function refreshTemplates() {
    if (templatesLoading) return;
    templatesLoading = true;
    const status = document.querySelector('#workout-template-status');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
        const response = await fetch('http://localhost:8080/api/workouts', { cache: 'no-store', signal: controller.signal });
        if (!response.ok) throw new Error('Load failed');
        const workouts = await response.json();
        if (!Array.isArray(workouts)) throw new Error('Invalid workouts');
        savedTemplates = workouts;
        const selected = templateSelect.value;
        templateSelect.replaceChildren(new Option('Nieuwe workout', ''));
        workouts.forEach(workout => templateSelect.add(new Option(workout.name, workout.id)));
        templateSelect.value = savedTemplates.some(workout => workout.id === selected) ? selected : '';
        status.textContent = workouts.length ? '' : 'Nog geen opgeslagen workouts. Je kunt hieronder een nieuwe maken.';
    } catch {
        status.textContent = 'Opgeslagen workouts niet geladen. Controleer de backend en open Workout maken opnieuw om te vernieuwen.';
    } finally { clearTimeout(timeout); templatesLoading = false; }
}
// Keep building focused on exercises; classification and assignment happen on save.
const saveDialog = document.createElement('dialog'); saveDialog.className = 'club-dialog workout-save-dialog';
saveDialog.setAttribute('aria-labelledby', 'workout-save-title');
const saveHeading = document.createElement('h2'); saveHeading.id = 'workout-save-title'; saveHeading.textContent = 'Workout bewaren';
const saveStatus = document.createElement('p'); saveStatus.setAttribute('role', 'status');
const typeLabel = document.createElement('label'); typeLabel.textContent = 'Workout type';
const typeSelect = document.createElement('select'); typeSelect.name = 'workoutType'; typeSelect.id = 'workout-kind';
typeSelect.add(new Option('Kies een workout type', '')); typeSelect.required = true;
[['other','Overig'],['full-body','Full body'],['lower-body','Lower body'],['upper-body','Upper body'],['conditioning','Conditioning']].forEach(([value,label]) => typeSelect.add(new Option(label,value)));
typeLabel.append(typeSelect);
const confirmSave = document.createElement('button'); confirmSave.type = 'button'; confirmSave.className = 'primary-button'; confirmSave.textContent = 'Bewaren';
const closeSave = document.createElement('button'); closeSave.type = 'button'; closeSave.className = 'secondary-button'; closeSave.textContent = 'Verder bewerken';
closeSave.addEventListener('click', () => saveDialog.close());
const destinationFieldset = document.querySelector('.workout-destination');
const nameLabel = workoutForm.elements.namedItem('title').closest('label');
const workoutName = workoutForm.elements.namedItem('title');
// Keep the name visible before choosing exercises, with the same form ownership.
workoutName.setAttribute('form', workoutForm.id);
document.querySelector('.workout-exercises-button').before(nameLabel);
function requireWorkoutName() {
    workoutName.setCustomValidity(workoutName.value.trim() ? '' : 'Geef je workout eerst een naam.');
    if (workoutName.reportValidity()) return true;
    workoutName.focus();
    return false;
}
document.querySelectorAll('#create-workout a[href="#exercises"]').forEach(link => {
    link.addEventListener('click', event => {
        if (!requireWorkoutName()) event.preventDefault();
    });
});
const descriptionLabel = workoutForm.elements.namedItem('description').closest('label');
const categoryLabel = categorySelect.closest('label');
saveDialog.append(saveHeading, typeLabel, destinationFieldset, categoryLabel, descriptionLabel, saveStatus, confirmSave, closeSave);
// Maintain form ownership after moving inputs into the dialog.
saveDialog.querySelectorAll('input, select, textarea').forEach(input => input.setAttribute('form', workoutForm.id));
document.body.append(saveDialog);
workoutForm.noValidate = true;
let saveConfirmed = false;
let pendingSubmitter = primarySubmit;
confirmSave.addEventListener('click', () => { saveConfirmed = true; workoutForm.requestSubmit(pendingSubmitter); });
const isAthleteDestination = () => ['athlete', 'private', 'openGym'].includes(destination.value);
const linksSessions = () => destination.value === 'sessions' && document.querySelector('#link-sessions-now').checked;
const athleteAgenda = window.capAgenda.mount(athleteDate);
const sessionAgenda = window.capAgenda.mount(sessionDate, { sessions: true, onChange: () => updateAssignmentOptions() });

// Choose body focus first; categories and assignments remain independent.
const bodyTypes = [['lower-body', 'Lower body'], ['upper-body', 'Upper body'], ['full-body', 'Full body']];
const typeChooser = document.createElement('section'); typeChooser.setAttribute('aria-labelledby', 'workout-type-title');
const typeHeading = document.createElement('h3'); typeHeading.id = 'workout-type-title'; typeHeading.tabIndex = -1;
typeHeading.textContent = 'Wat voor workout maak je?';
const bodyTiles = document.createElement('div'); bodyTiles.className = 'home-tiles';
typeChooser.append(typeHeading, bodyTiles);
let afterTypeChoice = 'editor';
const categoryChooser = document.createElement('section');
categoryChooser.hidden = true;
categoryChooser.setAttribute('aria-labelledby', 'workout-category-title');
const categoryHeading = document.createElement('h3');
categoryHeading.id = 'workout-category-title'; categoryHeading.tabIndex = -1;
categoryHeading.textContent = 'Voor wie maak je een workout?';
const categoryTiles = document.createElement('div'); categoryTiles.className = 'home-tiles';
categoryChooser.append(categoryHeading, categoryTiles);
const editorPanel = document.createElement('div'); editorPanel.hidden = true;
workoutForm.before(typeChooser, categoryChooser, editorPanel);
const categoryToolbar = document.createElement('div'); categoryToolbar.className = 'workout-category-toolbar';
const categorySummary = document.createElement('strong'); categorySummary.setAttribute('role', 'status');
const changeCategory = document.createElement('button'); changeCategory.type = 'button';
changeCategory.className = 'secondary-button'; changeCategory.textContent = 'Categorie wijzigen';
const changeType = document.createElement('button'); changeType.type = 'button'; changeType.className = 'secondary-button'; changeType.textContent = 'Workout type wijzigen';
changeType.addEventListener('click', () => showTypeChooser(true, 'editor'));
const categoryBack = document.createElement('button'); categoryBack.type = 'button'; categoryBack.className = 'secondary-button'; categoryBack.textContent = 'Workout type wijzigen';
categoryBack.addEventListener('click', () => showTypeChooser());
categoryHeading.after(categoryBack);
categoryToolbar.append(categorySummary, changeType, changeCategory);
// Name the workout after choosing its body focus, before selecting exercises.
editorPanel.append(nameLabel, document.querySelector('.workout-exercises-button'), categoryToolbar, workoutForm);
function showTypeChooser(moveFocus = true, next = 'editor') {
    afterTypeChoice = next;
    typeChooser.hidden = false; categoryChooser.hidden = true; editorPanel.hidden = true;
    if (moveFocus) typeHeading.focus();
}
bodyTypes.forEach(([value, label]) => {
    const tile = document.createElement('button'); tile.type = 'button'; tile.className = 'home-tile'; tile.dataset.workoutType = value;
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', value === 'lower-body' ? 'M8 3h8v7l-2 11m-4-11 2 3-2 8M8 10h8' : value === 'upper-body' ? 'M9 3h6l2 4 4 3-2 4-3-2v9H8v-9l-3 2-2-4 4-3z' : 'M12 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4M5 8h14m-7 0v6m-4 8 4-8 4 8');
    icon.append(path);
    const title = document.createElement('strong'); title.textContent = label;
    const caption = document.createElement('span'); caption.textContent = 'Bewaren bij ' + label;
    tile.append(icon, title, caption);
    tile.addEventListener('click', () => {
        typeSelect.value = value;
        document.dispatchEvent(new CustomEvent('cap:workout-type-changed'));
        if (afterTypeChoice === 'editor') showWorkoutEditor(); else showCategoryChooser();
    });
    bodyTiles.append(tile);
});
typeSelect.addEventListener('change', () => document.dispatchEvent(new CustomEvent('cap:workout-type-changed')));
function showCategoryChooser(moveFocus = true) {
    if (!typeSelect.value) { showTypeChooser(moveFocus); return; }
    typeChooser.hidden = true;
    categoryChooser.hidden = false;
    editorPanel.hidden = true;
    if (moveFocus) categoryHeading.focus();
}
function showWorkoutEditor(moveFocus = true) {
    if (!typeSelect.value) { showTypeChooser(moveFocus, 'editor'); return; }
    typeChooser.hidden = true;
    categoryChooser.hidden = true;
    editorPanel.hidden = false;
    const typeName = Array.from(typeSelect.options).find(option => option.value === typeSelect.value)?.textContent;
    categorySummary.textContent = `${typeName} · ${categorySelect.value || (destination.value === 'sessions' ? 'Groepsles' : 'Overig')}`;
    changeCategory.hidden = Boolean(editingWorkoutId);
    if (moveFocus) document.querySelector('#create-workout-title').focus();
}
changeCategory.addEventListener('click', () => showCategoryChooser());
const workoutChoices = [
    ['CAP HYROX', 'sessions', 'CAP HYROX', 'Training voor jouw HYROX-groep', 'M4 18 9 6l4 8 3-5 4 9M3 21h18'],
    ['CAP Lift & HIT', 'sessions', 'CAP Lift & HIT', 'Kracht en intensiteit', 'M6 5v14M3 8v8m15-11v14m3-11v8M6 12h12'],
    ['CAP Hybrid', 'sessions', 'CAP Hybrid', 'Kracht en conditie combineren', 'M4 12h16m-5-5 5 5-5 5M9 7l-5 5 5 5'],
    ['CAP Move', 'sessions', 'CAP Move', 'Samen in beweging', 'M13 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4M4 12l5-3 4 2 5-2m-9 0-2 7-3 5m7-10 3 5 6 2'],
    ['Specifieke sporter', 'athlete', 'Athlete', 'Persoonlijke workout op naam', 'M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8M4 21v-2a8 8 0 0 1 16 0v2'],
    ['Privétraining', 'private', 'Private', 'Individuele begeleiding', 'M9 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6M3 21v-3a6 6 0 0 1 12 0v3m3-11v6m-3-3h6'],
    ['Overig', 'library', '', 'Een algemene workout maken', 'M4 4h6v6H4zm10 0h6v6h-6zM4 14h6v6H4zm13 0v6m-3-3h6']
];
workoutChoices.forEach(([label, target, category, caption, path]) => {
    const tile = document.createElement('button'); tile.type = 'button'; tile.className = 'home-tile';
    tile.dataset.workoutCategory = category;
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
    const drawing = document.createElementNS('http://www.w3.org/2000/svg', 'path'); drawing.setAttribute('d', path); icon.append(drawing);
    const title = document.createElement('strong'); title.textContent = label;
    const description = document.createElement('span'); description.textContent = caption;
    tile.append(icon, title, description);
    tile.addEventListener('click', () => {
        setEditMode();
        destination.value = target; categorySelect.value = category;
        document.querySelector('#link-sessions-now').checked = false;
        sessionDate.value = '';
        sessionOptions.querySelectorAll('input').forEach(input => { input.checked = false; });
        updateAssignmentOptions();
        showWorkoutEditor();
    });
    categoryTiles.append(tile);
});
// General navigation keeps the current editor/chooser step. Only explicit
// change-type/category actions and save/cancel flows reset that step.

function updateAssignmentOptions() {
    athleteAgenda.box.hidden = !isAthleteDestination() || Boolean(editingWorkoutId);
    sessionAgenda.box.hidden = !linksSessions();
    document.querySelector('#workout-session-assignment').hidden = destination.value !== 'sessions';
    document.querySelector('#workout-athlete-assignment').hidden = !isAthleteDestination();
    athleteDate.required = isAthleteDestination() && !editingWorkoutId;
    athleteDate.disabled = !isAthleteDestination() || Boolean(editingWorkoutId);
    sessionDate.disabled = !linksSessions();
    document.querySelector('#session-link-options').hidden = !linksSessions();
    categoryLabel.hidden = isAthleteDestination();
    categorySelect.required = destination.value === 'sessions';
    if (isAthleteDestination()) categorySelect.value = destination.value === 'private' ? 'Private' : destination.value === 'openGym' ? 'Open gym' : 'Athlete';
    for (const option of categorySelect.options) option.hidden = destination.value === 'sessions' && ['Private','Open gym','Athlete'].includes(option.value);
    if (destination.value === 'sessions' && ['Private','Open gym','Athlete'].includes(categorySelect.value)) categorySelect.value = '';

    const selected = new Set(Array.from(sessionOptions.querySelectorAll('input:checked'), input => input.value));
    sessionOptions.replaceChildren();
    const sessionSelection = sessionAgenda.value();
    const sessions = window.capClub.sessions().filter(session => (!categorySelect.value || session.category === categorySelect.value)
        && (sessionSelection.mode === 'week'
            ? session.date >= sessionSelection.weekStart && session.date <= window.capAgenda.add(sessionSelection.weekStart, 6)
            : session.date === sessionSelection.date));
    sessions.forEach(session => {
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox'; checkbox.name = 'sessions'; checkbox.value = session.id;
        checkbox.checked = selected.has(session.id);
        const text = document.createElement('span');
        text.textContent = `${session.date} · ${session.start} · ${session.category || session.title} · ${session.coach}`;
        label.append(checkbox, text); sessionOptions.append(label);
    });
    if (!sessions.length) {
        const empty = document.createElement('p');
        empty.textContent = 'Nog geen komende sessies. Voeg eerst een les toe in Planning en keer daarna hier terug.';
        sessionOptions.append(empty);
    }
}
destination.addEventListener('change', updateAssignmentOptions);
document.querySelector('#link-sessions-now').addEventListener('change', updateAssignmentOptions);
categorySelect.addEventListener('change', updateAssignmentOptions);
sessionDate.addEventListener('change', updateAssignmentOptions);
document.addEventListener('cap:viewchange', () => {
    if (location.hash === '#create-workout') { updateAssignmentOptions(); refreshTemplates(); }
});
document.addEventListener('cap:workout-for-sessions', event => {
    setEditMode();
    document.querySelector('#link-sessions-now').checked = true;
    destination.value = 'sessions';
    categorySelect.value = '';
    sessionDate.value = '';
    updateAssignmentOptions();
    sessionOptions.querySelectorAll('input').forEach(input => { input.checked = event.detail.includes(input.value); });
    showTypeChooser(true, 'editor');
});
// Athlete assignment now opens Saved Workouts (handled by workouts-api.js).
// Do not bind the retired Create Workout link: it no longer exists in the page.
const groupWorkoutLink = document.createElement('a');
groupWorkoutLink.href = '#create-workout'; groupWorkoutLink.className = 'secondary-button';
groupWorkoutLink.textContent = 'Workout voor sessies maken';
groupWorkoutLink.addEventListener('click', () => showTypeChooser(false));
document.querySelector('#group-classes .coach-create-card').append(groupWorkoutLink);
updateAssignmentOptions();

const setupExercisePicker = window.capExercisePicker.setup;

function numberExercises() {
    const rows = Array.from(exerciseFields.querySelectorAll('.exercise-row'));
    const groups = new Map();
    const wrappers = [];
    rows.forEach(row => {
        const group = row.querySelector('[name="supersetGroup"]').value;
        let wrapper = group ? groups.get(group) : null;
        if (!wrapper) {
            wrapper = document.createElement('div');
            wrapper.className = group ? 'exercise-group superset-strip' : 'exercise-group';
            if (group) { groups.set(group, wrapper); wrapper.setAttribute('role', 'group'); wrapper.setAttribute('aria-label', `Superset ${group}`); }
            wrappers.push(wrapper);
        }
        wrapper.append(row);
    });
    exerciseFields.replaceChildren(...wrappers);
    const ordered = Array.from(exerciseFields.querySelectorAll('.exercise-row'));
    ordered.forEach((row, index) => {
        const group = row.querySelector('[name="supersetGroup"]').value;
        row.querySelector('legend').textContent = `Oefening ${index + 1}${group ? ` · Superset ${group}` : ''}`;
        row.classList.toggle('superset-exercise', Boolean(group));
        const removeButton = row.querySelector('.remove-exercise');
        removeButton.disabled = false;
        removeButton.setAttribute('aria-label', `Oefening ${index + 1} verwijderen`);
    });
}

// Optional per-set targets preserve the compact shared-target editor for older workouts.
function setupSetPlan(row) {
    const details = document.createElement('details'); details.className = 'exercise-options';
    const summary = document.createElement('summary'); summary.textContent = 'Gewicht en herhalingen per set';
    const label = document.createElement('label');
    const enabled = document.createElement('input'); enabled.type = 'checkbox';
    label.append(enabled, document.createTextNode(' Verschillende doelen per set'));
    const list = document.createElement('div'); list.className = 'set-target-list';
    details.append(summary, label, list); row.querySelector('.exercise-options').before(details);
    const count = row.querySelector('[name="sets"]');
    const defaults = () => ({ reps: Number(row.querySelector('[name="reps"]').value), loadMode: row.querySelector('[name="loadMode"]').value, loadValue: row.querySelector('[name="loadValue"]').value });
    let draft = [];
    function read() {
        return [...list.children].map(item => ({ reps: Number(item.querySelector('[name="setReps"]').value), loadMode: item.querySelector('[name="setLoadMode"]').value, loadValue: item.querySelector('[name="setLoadValue"]').value === '' ? null : Number(item.querySelector('[name="setLoadValue"]').value) }));
    }
    function render() {
        list.replaceChildren(); list.hidden = !enabled.checked;
        row.querySelector('[name="loadMode"]').disabled = enabled.checked;
        row.querySelector('[name="loadValue"]').disabled = enabled.checked || row.querySelector('[name="loadMode"]').value === 'none';
        row.querySelector('[name="reps"]').disabled = enabled.checked;
        if (!enabled.checked) return;
        const size = Math.min(100, Math.max(1, Number(count.value) || 1));
        for (let index = 0; index < size; index++) {
            const set = draft[index] || defaults();
            const item = document.createElement('div'); item.className = 'set-target-row';
            const title = document.createElement('strong'); title.textContent = 'Set ' + (index + 1); item.append(title);
            item.insertAdjacentHTML('beforeend', '<label>Reps<input name="setReps" type="number" min="1" max="1000" step="1" required inputmode="numeric"></label><label>Doel<select name="setLoadMode"><option value="none">Vrij gewicht</option><option value="fixed">Kg</option><option value="percent">% van rep-PR</option></select></label><label>Kg / %<input name="setLoadValue" type="number" min="0" max="2000" step="0.01" inputmode="decimal"></label>');
            item.querySelector('[name="setReps"]').value = set.reps;
            const mode = item.querySelector('[name="setLoadMode"]'); mode.value = set.loadMode || 'none';
            const value = item.querySelector('[name="setLoadValue"]'); value.value = set.loadValue ?? '';
            const sync = () => { value.disabled = mode.value === 'none'; value.required = !value.disabled; value.min = mode.value === 'percent' ? '0.01' : '0'; value.max = mode.value === 'percent' ? '200' : '2000'; };
            mode.addEventListener('change', sync); sync(); list.append(item);
        }
    }
    enabled.addEventListener('change', () => { if (!enabled.checked) draft = read(); render(); });
    count.addEventListener('change', () => { if (enabled.checked) { draft = read(); render(); } });
    row.capSetPlan = { read: () => enabled.checked ? read() : null, load(sets) { if (!sets?.length) return; draft = sets; enabled.checked = true; details.open = true; render(); } };
    render();
}

function addExercise(moveFocus = true) {
    const fragment = exerciseTemplate.content.cloneNode(true);
    const row = fragment.querySelector('.exercise-row');
    setupExercisePicker(row);
    row.querySelector('.add-superset').addEventListener('click', () => {
        let group = row.querySelector('[name="supersetGroup"]').value;
        if (!group) {
            const used = new Set(Array.from(exerciseFields.querySelectorAll('[name="supersetGroup"]'), input => input.value));
            group = ['A', 'B', 'C', 'D', 'E', 'F'].find(letter => !used.has(letter));
            if (!group) { document.querySelector('#exercise-status').textContent = 'Je kunt maximaal zes supersets maken.'; return; }
            row.querySelector('[name="supersetGroup"]').value = group;
        }
        const linked = addExercise(false);
        linked.querySelector('[name="supersetGroup"]').value = group;
        row.after(linked);
        numberExercises();
        window.capExercisePicker.open(linked);
        document.querySelector('#exercise-status').textContent = 'Superset-oefening toegevoegd. Wissel de gekoppelde oefeningen af.';
    });
    const loadMode = row.querySelector('[name="loadMode"]');
    const loadValue = row.querySelector('[name="loadValue"]');
    function updateLoadInput() {
        const unspecified = loadMode.value === 'none';
        loadValue.disabled = unspecified;
        loadValue.required = !unspecified;
        loadValue.placeholder = unspecified ? 'Niet van toepassing' : loadMode.value === 'fixed' ? 'Bijvoorbeeld: 60' : 'Bijvoorbeeld: 90';
        if (unspecified) { loadValue.value = ''; loadValue.setCustomValidity(''); }
    }
    loadMode.addEventListener('change', updateLoadInput);
    updateLoadInput();
    setupSetPlan(row);
    row.querySelector('.remove-exercise').addEventListener('click', () => {
        const group = row.querySelector('[name="supersetGroup"]').value;
        row.remove();
        if (group) {
            const remaining = Array.from(exerciseFields.querySelectorAll('[name="supersetGroup"]')).filter(input => input.value === group);
            if (remaining.length === 1) remaining[0].value = '';
        }
        numberExercises();
        addExerciseButton.focus();
        document.querySelector('#exercise-status').textContent = 'Oefening verwijderd.';
    });
    exerciseFields.append(fragment);
    numberExercises();
    if (moveFocus) window.capExercisePicker.open(row);
    return row;
}

addExerciseButton.addEventListener('click', () => {
    if (requireWorkoutName()) addExercise();
});
function populateWorkout(workout, edit = false) {
    const hasDraft = workoutForm.elements.namedItem('title').value.trim() || workoutForm.elements.namedItem('description').value.trim()
        || Array.from(exerciseFields.querySelectorAll('[name="exercise"]')).some(input => input.value.trim());
    if (hasDraft && !window.confirm('De huidige invoer vervangen door deze workout?')) return;
    setEditMode(edit ? workout : null);
    if (edit) {
        destination.value = workout.athleteId ? 'athlete' : 'library';
        updateAssignmentOptions();
    }
    workoutForm.elements.namedItem('title').value = workout.name;
    categorySelect.value = workout.category || '';
    if (edit || !typeSelect.value) typeSelect.value = workout.workoutType || 'other';
    athleteDate.value = workout.assignedDate || clubToday();
    workoutForm.elements.namedItem('description').value = workout.description || '';
    exerciseFields.replaceChildren();
    workout.exercises.forEach(exercise => {
        const row = addExercise(false);
        const values = { exercise: exercise.name, sets: exercise.sets, reps: exercise.reps,
            loadMode: exercise.loadMode || 'none', loadValue: exercise.loadValue,
            restSeconds: exercise.restSeconds, tempo: exercise.tempo, supersetGroup: exercise.supersetGroup };
        Object.entries(values).forEach(([name, value]) => { row.querySelector(`[name="${name}"]`).value = value ?? ''; });
        row.querySelector('[name="exercise"]').dispatchEvent(new Event('change'));
        row.querySelector('[name="loadMode"]').dispatchEvent(new Event('change'));
        row.capSetPlan.load(exercise.setPlan);
    });
    workoutForm.querySelectorAll('input, textarea').forEach(input => input.setCustomValidity(''));
    numberExercises();
    updateAssignmentOptions();
    showWorkoutEditor(false);
    document.querySelector('#workout-template-status').textContent = edit
        ? 'Je bewerkt de opgeslagen workout. De bestaande toewijzing blijft behouden. Afgeronde trainingen en gekopieerde sessieprogramma’s blijven ongewijzigd.'
        : 'Kopie geladen. Pas de workout aan en sla op. PR-percentages kun je voor een groepssessie vervangen door een vast gewicht of geen specifiek percentage.';
    return true;
}
document.querySelector('#load-workout-template').addEventListener('click', () => {
    const workout = savedTemplates.find(item => item.id === templateSelect.value);
    if (!workout) { document.querySelector('#workout-template-status').textContent = 'Kies eerst een opgeslagen workout.'; return; }
    populateWorkout(workout);
});
document.addEventListener('cap:edit-workout', event => {
    if (previewRole !== 'coach') return;
    if (window.capTraining.isActive(event.detail.id)) {
        document.querySelector('#coach-library-load-status').textContent = 'Rond de lopende training eerst af of verwijder die voordat je deze workout bewerkt.';
        return;
    }
    if (populateWorkout(event.detail, true)) location.hash = 'create-workout';
});
document.addEventListener('cap:edit-assignment', event => {
    if (previewRole !== 'coach' || window.capTraining.isActive(event.detail.id)) return;
    if (!populateWorkout(event.detail, true)) return;
    editingAssignmentId = event.detail.assignmentId;
    document.querySelector('#whole-workout-help').textContent = 'Verwijder de volledige workout voor deze datum bij Guillaume, inclusief alle oefeningen. Het opgeslagen voorbeeld blijft behouden.';
    copySubmit.hidden = true;
    athleteDate.min = event.detail.assignedDate;
    document.querySelector('#create-workout-title').textContent = 'Workout van Guillaume bewerken';
    document.querySelector('#workout-template-status').textContent = 'Je wijzigt alleen deze gedateerde toewijzing. Het opgeslagen voorbeeld en andere datums blijven behouden.';
    location.hash = 'create-workout';
});
cancelEdit.addEventListener('click', () => {
    if (!window.confirm('Bewerken annuleren en je invoer wissen?')) return;
    setEditMode(); workoutForm.reset(); exerciseFields.replaceChildren(); updateAssignmentOptions();
    document.querySelector('#workout-template-status').textContent = '';
    document.querySelector('#exercise-status').textContent = '';
    showCategoryChooser();
});
deleteEditedWorkout.addEventListener('click', async () => {
    if (!editingWorkoutId || deleteEditedWorkout.disabled || primarySubmit.disabled
        || previewRole !== 'coach' || !window.capAccess.can('workouts')) return;
    const feedback = document.querySelector('#exercise-status');
    if (window.capTraining.isActive(editingWorkoutId)) {
        feedback.textContent = 'Rond de lopende training eerst af voordat je deze workout verwijdert.'; return;
    }
    const assignmentId = editingAssignmentId;
    const message = assignmentId
        ? 'De volledige workout voor deze datum verwijderen bij Guillaume? Het opgeslagen voorbeeld en afgeronde trainingen blijven behouden.'
        : 'Deze volledige workout uit de bibliotheek verwijderen, inclusief de toewijzingen? Afgeronde trainingen en PRs blijven behouden.';
    if (!window.confirm(message)) return;
    [deleteEditedWorkout, primarySubmit, copySubmit, cancelEdit].forEach(button => { button.disabled = true; });
    feedback.textContent = 'Workout verwijderen…';
    try {
        const path = assignmentId ? 'workout-assignments' : 'workouts';
        const response = await fetch(`http://localhost:8080/api/${path}/${encodeURIComponent(assignmentId || editingWorkoutId)}`, {
            method: 'DELETE', signal: AbortSignal.timeout(8000)
        });
        if (!response.ok && response.status !== 404) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Verwijderen mislukt.');
        }
        saveDialog.close(); workoutForm.reset(); setEditMode(); exerciseFields.replaceChildren();
        updateAssignmentOptions(); showCategoryChooser(false); feedback.textContent = '';
        document.querySelector(assignmentId ? '#athlete-action-status' : '#coach-workout-status').textContent = 'Volledige workout verwijderd.';
        location.hash = assignmentId ? 'athletes' : 'saved-workouts';
        document.dispatchEvent(new CustomEvent(assignmentId ? 'cap:assignment-saved' : 'cap:workouts-saved'));
    } catch (error) {
        feedback.textContent = `${error.message} Controleer het overzicht voordat je opnieuw probeert. Je invoer blijft behouden.`;
    } finally {
        [deleteEditedWorkout, primarySubmit, copySubmit, cancelEdit].forEach(button => { button.disabled = false; });
    }
});

// Native required/min/max validation handles numbers; also reject whitespace-only names.
workoutForm.addEventListener('input', (event) => {
    event.target.setCustomValidity('');
});
workoutName.addEventListener('input', () => workoutName.setCustomValidity(''));
workoutForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!window.capAccess.can('workouts') || previewRole !== 'coach') return;
    if (!requireWorkoutName()) return;
    const rows = Array.from(exerciseFields.querySelectorAll('.exercise-row'));
    if (!rows.length) {
        saveConfirmed = false;
        document.querySelector('#exercise-status').textContent = 'Voeg eerst een oefening toe met +.';
        addExerciseButton.focus(); return;
    }
    const unnamed = rows.find(row => !row.querySelector('[name="exercise"]').value.trim());
    if (unnamed) {
        saveConfirmed = false;
        if (saveDialog.open) saveDialog.close();
        window.capExercisePicker.open(unnamed); return;
    }
    if (!saveConfirmed) {
        const invalid = Array.from(exerciseFields.querySelectorAll('input, select')).find(input => !input.checkValidity());
        if (invalid) {
            const options = invalid.closest('details'); if (options) options.open = true;
            invalid.reportValidity(); return;
        }
        if (event.submitter === copySubmit && editingWorkoutId) {
            setEditMode();
            if (!athleteDate.value || athleteDate.value < clubToday()) athleteDate.value = clubToday();
        }
        pendingSubmitter = event.submitter || primarySubmit;
        saveStatus.textContent = '';
        updateAssignmentOptions();
        saveDialog.showModal();
        return;
    }
    saveConfirmed = false;
    if (!workoutForm.reportValidity()) return;
    const submit = workoutForm.querySelector('[type="submit"]');
    if (submit.disabled) return;
    const status = saveStatus;
    const updating = Boolean(editingWorkoutId) && event.submitter !== copySubmit;
    if (updating && window.capTraining.isActive(editingWorkoutId)) {
        status.textContent = 'Rond de lopende training eerst af voordat je deze workout wijzigt.';
        return;
    }
    const sessionIds = Array.from(sessionOptions.querySelectorAll('input:checked'), input => input.value);
    if (linksSessions() && (!sessionIds.length || sessionIds.some(id => !window.capClub.sessions().some(session => session.id === id)))) {
        status.textContent = 'Kies minstens één beschikbare sessie. Controleer de planning.';
        return;
    }
    if (!isAthleteDestination() && Array.from(exerciseFields.querySelectorAll('[name="loadMode"], [name="setLoadMode"]')).some(input => !input.disabled && input.value === 'percent')) {
        status.textContent = 'Een percentage van een PR vereist een persoonlijke toewijzing. Kies voor deze workout een vast gewicht of geen doelgewicht.';
        return;
    }
    submit.disabled = true;
    copySubmit.disabled = true;
    cancelEdit.disabled = true; confirmSave.disabled = true; closeSave.disabled = true;
    status.textContent = 'Workout opslaan…';
    const payload = {
        name: workoutForm.elements.namedItem('title').value.trim(),
        category: categorySelect.value,
        workoutType: typeSelect.value,
        assignedDate: isAthleteDestination() && athleteAgenda.value().mode === 'day' ? athleteDate.value || clubToday() : '',
        description: workoutForm.elements.namedItem('description').value.trim(),
        athleteId: isAthleteDestination() ? workoutForm.elements.namedItem('athlete').value : '',
        exercises: Array.from(exerciseFields.querySelectorAll('.exercise-row'), row => ({
            name: row.querySelector('[name="exercise"]').value.trim(),
            setPlan: row.capSetPlan.read(),
            sets: Number(row.querySelector('[name="sets"]').value),
            reps: Number(row.querySelector('[name="reps"]').value),
            restSeconds: row.querySelector('[name="restSeconds"]').value === '' ? null : Number(row.querySelector('[name="restSeconds"]').value),
            tempo: row.querySelector('[name="tempo"]').value.trim(),
            supersetGroup: row.querySelector('[name="supersetGroup"]').value,
            loadMode: row.querySelector('[name="loadMode"]').value,
            loadValue: row.querySelector('[name="loadValue"]').value === '' ? null : Number(row.querySelector('[name="loadValue"]').value)
        }))
    };
    try {
        const assignmentEdit = updating && editingAssignmentId;
        const weeklyCreate = !updating && isAthleteDestination() && athleteAgenda.value().mode === 'week';
        const response = await fetch(assignmentEdit
            ? `http://localhost:8080/api/workout-assignments/${encodeURIComponent(editingAssignmentId)}`
            : `http://localhost:8080/api/workouts${updating ? `/${encodeURIComponent(editingWorkoutId)}` : weeklyCreate ? `?weekStart=${athleteAgenda.value().weekStart}` : ''}`, {
            method: updating ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Opslaan mislukt. Controleer de gegevens en probeer opnieuw.');
        }
        const saved = await response.json();
        const wasAssignmentEdit = Boolean(assignmentEdit);
        let message = updating ? 'Workout bijgewerkt. Geschiedenis en sessieprogramma’s blijven behouden.' : 'Workout opgeslagen in Java.';
        if (weeklyCreate) message = `Workout bewaard bij Workouts deze week · ${window.capAgenda.range(athleteAgenda.value().date)}.`;
        if (linksSessions()) {
            try {
                window.capClub.assignWorkout(sessionIds, saved);
                message = `Workout opgeslagen en gekoppeld aan ${sessionIds.length} sessie(s) in de lokale planning.`;
            } catch {
                message = 'Workout opgeslagen in Java, maar de sessietoewijzing is mislukt. Het programma is niet bijgewerkt. Controleer browseropslag en de planning.';
            }
        }
        saveDialog.close();
        workoutForm.reset();
        setEditMode();
        athleteDate.value = clubToday();
        athleteAgenda.reset(); sessionAgenda.reset();
        document.querySelector('#workout-template-status').textContent = '';
        sessionOptions.querySelectorAll('input').forEach(input => { input.checked = false; });
        updateAssignmentOptions();
        exerciseFields.replaceChildren();
        showCategoryChooser(false);
        status.textContent = '';
        document.querySelector('#coach-workout-status').textContent = message;
        window.location.hash = wasAssignmentEdit ? 'athletes' : 'saved-workouts';
        document.dispatchEvent(new CustomEvent(wasAssignmentEdit ? 'cap:assignment-saved' : 'cap:workouts-saved'));
    } catch (error) {
        status.textContent = `${error.message} Je invoer blijft behouden. Bij een verbindingsfout: controleer eerst de bibliotheek voordat je opnieuw opslaat.`;
    } finally { submit.disabled = false; copySubmit.disabled = false; cancelEdit.disabled = false; confirmSave.disabled = false; closeSave.disabled = false; }
});

// The library adds to this draft; nothing is saved until the coach uses Bewaren.
window.capWorkoutDraft = {
    summary() {
        return {
            count: Array.from(exerciseFields.querySelectorAll('[name="exercise"]')).filter(input => input.value.trim()).length,
            name: workoutForm.elements.namedItem('title').value.trim(),
            type: typeSelect.value,
            editing: Boolean(editingWorkoutId)
        };
    },
    addFromCatalog(id) {
        if (previewRole !== 'coach' || !window.capAccess.can('workouts')) return false;
        if (!workoutName.value.trim() || !workoutName.checkValidity()) return false;
        const exercise = window.capExercisePicker.catalog.find(item => item.id === id);
        if (!exercise || !window.capExercisePicker.matchesType(exercise, typeSelect.value)) return false;
        const rows = Array.from(exerciseFields.querySelectorAll('.exercise-row'));
        let row = rows.find(item => !item.querySelector('[name="exercise"]').value.trim() && !item.querySelector('[name="supersetGroup"]').value);
        if (!row && rows.length >= 100) return false;
        row = row || addExercise(false);
        const input = row.querySelector('[name="exercise"]');
        input.value = exercise.name; input.setCustomValidity(''); input.dispatchEvent(new Event('change'));
        return true;
    },
    open() { updateAssignmentOptions(); showWorkoutEditor(false); }
};
if (location.hash === '#create-workout') refreshTemplates();
