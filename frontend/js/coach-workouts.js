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
const primarySubmit = workoutForm.querySelector('[type="submit"]');
const copySubmit = document.querySelector('#save-workout-copy');
const cancelEdit = document.querySelector('#cancel-workout-edit');
function setEditMode(workout = null) {
    editingWorkoutId = workout?.id || null;
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
[['other','Overig'],['full-body','Full body'],['lower-body','Lower body'],['upper-body','Upper body'],['conditioning','Conditioning']].forEach(([value,label]) => typeSelect.add(new Option(label,value)));
typeLabel.append(typeSelect);
const confirmSave = document.createElement('button'); confirmSave.type = 'button'; confirmSave.className = 'primary-button'; confirmSave.textContent = 'Bewaren';
const closeSave = document.createElement('button'); closeSave.type = 'button'; closeSave.className = 'secondary-button'; closeSave.textContent = 'Verder bewerken';
closeSave.addEventListener('click', () => saveDialog.close());
const destinationFieldset = document.querySelector('.workout-destination');
const nameLabel = workoutForm.elements.namedItem('title').closest('label');
const descriptionLabel = workoutForm.elements.namedItem('description').closest('label');
const categoryLabel = categorySelect.closest('label');
saveDialog.append(saveHeading, nameLabel, typeLabel, destinationFieldset, categoryLabel, descriptionLabel, saveStatus, confirmSave, closeSave);
// Maintain form ownership after moving inputs into the dialog.
saveDialog.querySelectorAll('input, select, textarea').forEach(input => input.setAttribute('form', workoutForm.id));
document.body.append(saveDialog);
workoutForm.noValidate = true;
let saveConfirmed = false;
let pendingSubmitter = primarySubmit;
confirmSave.addEventListener('click', () => { saveConfirmed = true; workoutForm.requestSubmit(pendingSubmitter); });
const isAthleteDestination = () => ['athlete', 'private', 'openGym'].includes(destination.value);
const linksSessions = () => destination.value === 'sessions' && document.querySelector('#link-sessions-now').checked;

// Choose a category first, then keep using the shared workout editor.
const categoryChooser = document.createElement('section');
categoryChooser.setAttribute('aria-labelledby', 'workout-category-title');
const categoryHeading = document.createElement('h3');
categoryHeading.id = 'workout-category-title'; categoryHeading.tabIndex = -1;
categoryHeading.textContent = 'Voor wie maak je een workout?';
const categoryTiles = document.createElement('div'); categoryTiles.className = 'home-tiles';
categoryChooser.append(categoryHeading, categoryTiles);
const editorPanel = document.createElement('div'); editorPanel.hidden = true;
workoutForm.before(categoryChooser, editorPanel);
const categoryToolbar = document.createElement('div'); categoryToolbar.className = 'workout-category-toolbar';
const categorySummary = document.createElement('strong'); categorySummary.setAttribute('role', 'status');
const changeCategory = document.createElement('button'); changeCategory.type = 'button';
changeCategory.className = 'secondary-button'; changeCategory.textContent = 'Categorie wijzigen';
categoryToolbar.append(categorySummary, changeCategory);
editorPanel.append(categoryToolbar, workoutForm);
function showCategoryChooser(moveFocus = true) {
    categoryChooser.hidden = false;
    editorPanel.hidden = true;
    if (moveFocus) categoryHeading.focus();
}
function showWorkoutEditor(moveFocus = true) {
    categoryChooser.hidden = true;
    editorPanel.hidden = false;
    categorySummary.textContent = categorySelect.value || (destination.value === 'sessions' ? 'Groepsles' : 'Overig');
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
// General entry links show the chooser without erasing an unfinished draft.
document.querySelectorAll('#sidebar-navigation a[href="#create-workout"], #coach a[href="#create-workout"], .mobile-bottom-nav a[href="#create-workout"], #saved-workouts a[href="#create-workout"]').forEach(link => {
    link.addEventListener('click', () => showCategoryChooser(false));
});

function updateAssignmentOptions() {
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
    const sessions = window.capClub.sessions().filter(session => (!categorySelect.value || session.category === categorySelect.value)
        && (!sessionDate.value || session.date === sessionDate.value));
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
    showWorkoutEditor();
});
document.querySelector('#athletes a[href="#create-workout"]').addEventListener('click', () => {
    setEditMode();
    destination.value = 'athlete'; updateAssignmentOptions();
    showWorkoutEditor(false);
});
const groupWorkoutLink = document.createElement('a');
groupWorkoutLink.href = '#create-workout'; groupWorkoutLink.className = 'secondary-button';
groupWorkoutLink.textContent = 'Workout voor sessies maken';
groupWorkoutLink.addEventListener('click', () => showCategoryChooser(false));
document.querySelector('#group-classes .coach-create-card').append(groupWorkoutLink);
updateAssignmentOptions();

// A starter library, not an exhaustive database. Custom exercise names remain allowed.
const exerciseCatalog = [
    'Air squat', 'Back squat', 'Front squat', 'Goblet squat', 'Bulgarian split squat',
    'Split squat', 'Sumo squat', 'Overhead squat', 'Box squat', 'Jump squat',
    'Pistol squat', 'Hack squat', 'Landmine squat', 'Wall sit', 'Leg press',
    'Forward lunge', 'Reverse lunge', 'Walking lunge', 'Lateral lunge', 'Step-up',
    'Deadlift', 'Romanian deadlift', 'Single-leg Romanian deadlift', 'Sumo deadlift',
    'Trap bar deadlift', 'Hip thrust', 'Single-leg hip thrust', 'Glute bridge',
    'Good morning', 'Leg curl', 'Leg extension', 'Nordic hamstring curl',
    'Standing calf raise', 'Seated calf raise', 'Tibialis raise', 'Cable kickback',
    'Bench press', 'Dumbbell bench press', 'Incline bench press', 'Incline dumbbell press',
    'Push-up', 'Incline push-up', 'Diamond push-up', 'Chest fly', 'Cable chest fly',
    'Chest press', 'Dip', 'Overhead press', 'Dumbbell shoulder press', 'Arnold press',
    'Landmine press', 'Lateral raise', 'Front raise', 'Reverse fly', 'Face pull',
    'Pull-up', 'Chin-up', 'Assisted pull-up', 'Lat pulldown', 'Barbell row',
    'Dumbbell row', 'Seated cable row', 'Chest-supported row', 'Inverted row',
    'Straight-arm pulldown', 'Shrug', 'Back extension', 'Biceps curl', 'Hammer curl',
    'Preacher curl', 'Cable curl', 'Triceps pushdown', 'Overhead triceps extension',
    'Skull crusher', 'Plank', 'Side plank', 'Dead bug', 'Bird dog', 'Crunch',
    'Bicycle crunch', 'Reverse crunch', 'Hanging knee raise', 'Hanging leg raise',
    'Ab wheel rollout', 'Pallof press', 'Cable woodchop', 'Russian twist',
    'Hollow hold', 'Mountain climber', 'Farmer carry', 'Suitcase carry',
    'Kettlebell swing', 'Kettlebell clean', 'Kettlebell snatch', 'Turkish get-up',
    'Clean', 'Clean and jerk', 'Power clean', 'Hang clean', 'Snatch', 'Push press',
    'Thruster', 'Medicine ball slam', 'Medicine ball chest pass', 'Wall ball',
    'Box jump', 'Broad jump', 'Skater jump', 'Pogo jump', 'Burpee', 'Jumping jack',
    'Jump rope', 'Battle ropes', 'Sled push', 'Sled pull', 'Sprint', 'Shuttle run',
    'Jogging', 'Cycling', 'Rowing', 'Ski erg', 'Assault bike', 'Stair climber',
    'High knees', 'Butt kicks', 'Defensive slide', 'Closeout drill', 'Lay-up drill',
    'Dribbling drill', 'Agility ladder', 'Hip flexor stretch', 'Hamstring stretch',
    'Calf stretch', 'Cat-cow', 'Thoracic rotation', 'Ankle mobility', '90/90 hip rotation'
].sort((first, second) => first.localeCompare(second));

let exercisePickerId = 0;

function setupExercisePicker(row) {
    const input = row.querySelector('[name="exercise"]');
    const results = row.querySelector('.exercise-results');
    const count = row.querySelector('.exercise-result-count');
    const picker = row.querySelector('.exercise-picker');
    const dropdown = row.querySelector('.exercise-dropdown');
    const toggle = row.querySelector('.exercise-dropdown-toggle');
    const id = ++exercisePickerId;
    input.id = `exercise-input-${id}`;
    row.querySelector('.exercise-input-label').htmlFor = input.id;
    dropdown.id = `exercise-dropdown-${id}`;
    results.id = `exercise-results-${id}`;
    input.setAttribute('aria-controls', results.id);
    toggle.setAttribute('aria-controls', dropdown.id);

    function setOpen(open) {
        dropdown.hidden = !open;
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Oefeningen sluiten' : 'Oefeningen tonen');
    }

    function filterExercises() {
        const query = input.value.trim().toLocaleLowerCase();
        const matches = exerciseCatalog.filter((name) => name.toLocaleLowerCase().includes(query));
        count.textContent = matches.length
            ? `${matches.length} oefeningen gevonden.`
            : 'Geen match. Je kunt deze naam als eigen oefening gebruiken.';
        results.replaceChildren();
        matches.forEach((name) => {
            const item = document.createElement('li');
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = name;
            button.addEventListener('click', () => {
                input.value = name;
                input.setCustomValidity('');
                filterExercises();
                input.focus();
                count.textContent = `${name} geselecteerd.`;
                setOpen(false);
            });
            item.append(button);
            results.append(item);
        });
        results.scrollTop = 0;
    }

    input.addEventListener('input', () => {
        filterExercises();
        setOpen(true);
    });
    toggle.addEventListener('click', () => {
        const open = dropdown.hidden;
        if (open) filterExercises();
        setOpen(open);
    });
    picker.addEventListener('focusout', (event) => {
        if (!picker.contains(event.relatedTarget)) setOpen(false);
    });
    picker.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            setOpen(false);
            input.focus();
            event.stopPropagation();
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            if (dropdown.hidden) {
                filterExercises();
                setOpen(true);
            }
            const buttons = Array.from(results.querySelectorAll('button'));
            const current = buttons.indexOf(document.activeElement);
            const next = current < 0 ? (event.key === 'ArrowDown' ? 0 : buttons.length - 1)
                : Math.max(0, Math.min(buttons.length - 1, current + (event.key === 'ArrowDown' ? 1 : -1)));
            buttons[next]?.focus();
        }
    });
}

// Close open lists even when the user taps a non-focusable area outside them.
document.addEventListener('pointerdown', (event) => {
    document.querySelectorAll('.exercise-picker').forEach((picker) => {
        if (!picker.contains(event.target)) {
            picker.querySelector('.exercise-dropdown').hidden = true;
            const toggle = picker.querySelector('.exercise-dropdown-toggle');
            toggle.setAttribute('aria-expanded', 'false');
            toggle.setAttribute('aria-label', 'Oefeningen tonen');
        }
    });
});

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
        removeButton.disabled = rows.length === 1;
        removeButton.setAttribute('aria-label', `Oefening ${index + 1} verwijderen`);
    });
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
        linked.querySelector('[name="exercise"]').focus();
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
    if (moveFocus) row.querySelector('[name="exercise"]').focus();
    return row;
}

addExerciseButton.addEventListener('click', () => addExercise());
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
    typeSelect.value = workout.workoutType || 'other';
    athleteDate.value = workout.assignedDate || clubToday();
    workoutForm.elements.namedItem('description').value = workout.description || '';
    exerciseFields.replaceChildren();
    workout.exercises.forEach(exercise => {
        const row = addExercise(false);
        const values = { exercise: exercise.name, sets: exercise.sets, reps: exercise.reps,
            loadMode: exercise.loadMode || 'none', loadValue: exercise.loadValue,
            restSeconds: exercise.restSeconds, tempo: exercise.tempo, supersetGroup: exercise.supersetGroup };
        Object.entries(values).forEach(([name, value]) => { row.querySelector(`[name="${name}"]`).value = value ?? ''; });
        row.querySelector('[name="loadMode"]').dispatchEvent(new Event('change'));
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
cancelEdit.addEventListener('click', () => {
    if (!window.confirm('Bewerken annuleren en je invoer wissen?')) return;
    setEditMode(); workoutForm.reset(); exerciseFields.replaceChildren(); addExercise(false); updateAssignmentOptions();
    document.querySelector('#workout-template-status').textContent = '';
    document.querySelector('#exercise-status').textContent = '';
    showCategoryChooser();
});

// Native required/min/max validation handles numbers; also reject whitespace-only names.
workoutForm.addEventListener('input', (event) => {
    event.target.setCustomValidity('');
});
workoutForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!window.capAccess.can('workouts') || previewRole !== 'coach') return;
    if (!saveConfirmed) {
        const invalid = Array.from(exerciseFields.querySelectorAll('input, select')).find(input => !input.checkValidity());
        if (invalid) { invalid.reportValidity(); return; }
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
    if (!isAthleteDestination() && Array.from(exerciseFields.querySelectorAll('[name="loadMode"]')).some(input => input.value === 'percent')) {
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
        assignedDate: isAthleteDestination() ? athleteDate.value || clubToday() : '',
        description: workoutForm.elements.namedItem('description').value.trim(),
        athleteId: isAthleteDestination() ? workoutForm.elements.namedItem('athlete').value : '',
        exercises: Array.from(exerciseFields.querySelectorAll('.exercise-row'), row => ({
            name: row.querySelector('[name="exercise"]').value.trim(),
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
        const response = await fetch(`http://localhost:8080/api/workouts${updating ? `/${encodeURIComponent(editingWorkoutId)}` : ''}`, {
            method: updating ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Opslaan mislukt. Controleer de gegevens en probeer opnieuw.');
        }
        const saved = await response.json();
        let message = updating ? 'Workout bijgewerkt. Geschiedenis en sessieprogramma’s blijven behouden.' : 'Workout opgeslagen in Java.';
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
        document.querySelector('#workout-template-status').textContent = '';
        sessionOptions.querySelectorAll('input').forEach(input => { input.checked = false; });
        updateAssignmentOptions();
        exerciseFields.replaceChildren();
        addExercise(false);
        showCategoryChooser(false);
        status.textContent = '';
        document.querySelector('#coach-workout-status').textContent = message;
        window.location.hash = 'saved-workouts';
        document.dispatchEvent(new CustomEvent('cap:workouts-saved'));
    } catch (error) {
        status.textContent = `${error.message} Je invoer blijft behouden. Bij een verbindingsfout: controleer eerst de bibliotheek voordat je opnieuw opslaat.`;
    } finally { submit.disabled = false; copySubmit.disabled = false; cancelEdit.disabled = false; confirmSave.disabled = false; closeSave.disabled = false; }
});

addExercise(false);
if (location.hash === '#create-workout') refreshTemplates();
