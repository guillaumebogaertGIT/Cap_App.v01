const workoutForm = document.querySelector('#coach-workout-form');
const exerciseFields = document.querySelector('#exercise-fields');
const exerciseTemplate = document.querySelector('#exercise-template');
const addExerciseButton = document.querySelector('#add-exercise');
const destination = document.querySelector('#workout-destination');
const sessionOptions = document.querySelector('#workout-session-options');
const templateSelect = document.querySelector('#workout-template');
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
// Choose an audience before building the workout, using the same editor for all destinations.
workoutForm.prepend(document.querySelector('.workout-destination'));
function updateAssignmentOptions() {
    document.querySelector('#workout-session-assignment').hidden = destination.value !== 'sessions';
    document.querySelector('#workout-athlete-assignment').hidden = destination.value !== 'athlete';
    const selected = new Set(Array.from(sessionOptions.querySelectorAll('input:checked'), input => input.value));
    sessionOptions.replaceChildren();
    const sessions = window.capClub.sessions();
    sessions.forEach(session => {
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox'; checkbox.name = 'sessions'; checkbox.value = session.id;
        checkbox.checked = selected.has(session.id);
        const text = document.createElement('span');
        text.textContent = `${session.date} · ${session.start} · ${session.title} · ${session.coach}`;
        label.append(checkbox, text); sessionOptions.append(label);
    });
    if (!sessions.length) {
        const empty = document.createElement('p');
        empty.textContent = 'Nog geen komende sessies. Voeg eerst een les toe in Planning en keer daarna hier terug.';
        sessionOptions.append(empty);
    }
}
destination.addEventListener('change', updateAssignmentOptions);
document.addEventListener('cap:viewchange', () => {
    if (location.hash === '#create-workout') { updateAssignmentOptions(); refreshTemplates(); }
});
document.addEventListener('cap:workout-for-sessions', event => {
    setEditMode();
    destination.value = 'sessions';
    updateAssignmentOptions();
    sessionOptions.querySelectorAll('input').forEach(input => { input.checked = event.detail.includes(input.value); });
});
document.querySelector('#athletes a[href="#create-workout"]').addEventListener('click', () => {
    setEditMode();
    destination.value = 'athlete'; updateAssignmentOptions();
});
const groupWorkoutLink = document.createElement('a');
groupWorkoutLink.href = '#create-workout'; groupWorkoutLink.className = 'secondary-button';
groupWorkoutLink.textContent = 'Workout voor sessies maken';
groupWorkoutLink.addEventListener('click', () => { setEditMode(); destination.value = 'sessions'; updateAssignmentOptions(); });
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
    const rows = exerciseFields.querySelectorAll('.exercise-row');
    rows.forEach((row, index) => {
        row.querySelector('legend').textContent = `Oefening ${index + 1}`;
        const removeButton = row.querySelector('.remove-exercise');
        removeButton.disabled = rows.length === 1;
        removeButton.setAttribute('aria-label', `Oefening ${index + 1} verwijderen`);
    });
}

function addExercise(moveFocus = true) {
    const fragment = exerciseTemplate.content.cloneNode(true);
    const row = fragment.querySelector('.exercise-row');
    setupExercisePicker(row);
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
        row.remove();
        numberExercises();
        addExerciseButton.focus();
        document.querySelector('#exercise-status').textContent = 'Oefening verwijderd.';
    });
    exerciseFields.append(fragment);
    numberExercises();
    if (moveFocus) row.querySelector('input').focus();
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
    workoutForm.elements.namedItem('description').value = workout.description || '';
    exerciseFields.replaceChildren();
    workout.exercises.forEach(exercise => {
        addExercise(false);
        const row = exerciseFields.lastElementChild;
        const values = { exercise: exercise.name, sets: exercise.sets, reps: exercise.reps,
            loadMode: exercise.loadMode || 'none', loadValue: exercise.loadValue,
            restSeconds: exercise.restSeconds, tempo: exercise.tempo };
        Object.entries(values).forEach(([name, value]) => { row.querySelector(`[name="${name}"]`).value = value ?? ''; });
        row.querySelector('[name="loadMode"]').dispatchEvent(new Event('change'));
    });
    workoutForm.querySelectorAll('input, textarea').forEach(input => input.setCustomValidity(''));
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
});

// Native required/min/max validation handles numbers; also reject whitespace-only names.
workoutForm.addEventListener('input', (event) => {
    event.target.setCustomValidity('');
});
workoutForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!window.capAccess.can('workouts') || previewRole !== 'coach') return;
    if (!workoutForm.reportValidity()) return;
    const submit = workoutForm.querySelector('[type="submit"]');
    if (submit.disabled) return;
    const status = document.querySelector('#exercise-status');
    const updating = Boolean(editingWorkoutId) && event.submitter !== copySubmit;
    if (updating && window.capTraining.isActive(editingWorkoutId)) {
        status.textContent = 'Rond de lopende training eerst af voordat je deze workout wijzigt.';
        return;
    }
    const sessionIds = Array.from(sessionOptions.querySelectorAll('input:checked'), input => input.value);
    if (destination.value === 'sessions' && (!sessionIds.length || sessionIds.some(id => !window.capClub.sessions().some(session => session.id === id)))) {
        status.textContent = 'Kies minstens één beschikbare sessie. Controleer de planning.';
        return;
    }
    if (destination.value !== 'athlete' && Array.from(exerciseFields.querySelectorAll('[name="loadMode"]')).some(input => input.value === 'percent')) {
        status.textContent = 'Een percentage van een PR vereist een persoonlijke toewijzing. Kies voor deze workout een vast gewicht of geen doelgewicht.';
        return;
    }
    submit.disabled = true;
    copySubmit.disabled = true;
    cancelEdit.disabled = true;
    status.textContent = 'Workout opslaan…';
    const payload = {
        name: workoutForm.elements.namedItem('title').value.trim(),
        description: workoutForm.elements.namedItem('description').value.trim(),
        athleteId: destination.value === 'athlete' ? workoutForm.elements.namedItem('athlete').value : '',
        exercises: Array.from(exerciseFields.querySelectorAll('.exercise-row'), row => ({
            name: row.querySelector('[name="exercise"]').value.trim(),
            sets: Number(row.querySelector('[name="sets"]').value),
            reps: Number(row.querySelector('[name="reps"]').value),
            restSeconds: row.querySelector('[name="restSeconds"]').value === '' ? null : Number(row.querySelector('[name="restSeconds"]').value),
            tempo: row.querySelector('[name="tempo"]').value.trim(),
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
        if (destination.value === 'sessions') {
            try {
                window.capClub.assignWorkout(sessionIds, saved);
                message = `Workout opgeslagen en gekoppeld aan ${sessionIds.length} sessie(s) in de lokale planning.`;
            } catch {
                message = 'Workout opgeslagen in Java, maar de sessietoewijzing is mislukt. Het programma is niet bijgewerkt. Controleer browseropslag en de planning.';
            }
        }
        workoutForm.reset();
        setEditMode();
        document.querySelector('#workout-template-status').textContent = '';
        sessionOptions.querySelectorAll('input').forEach(input => { input.checked = false; });
        updateAssignmentOptions();
        exerciseFields.replaceChildren();
        addExercise(false);
        status.textContent = '';
        document.querySelector('#coach-workout-status').textContent = message;
        window.location.hash = 'dashboard';
        document.dispatchEvent(new CustomEvent('cap:workouts-saved'));
    } catch (error) {
        status.textContent = `${error.message} Je invoer blijft behouden. Bij een verbindingsfout: controleer eerst de bibliotheek voordat je opnieuw opslaat.`;
    } finally { submit.disabled = false; copySubmit.disabled = false; cancelEdit.disabled = false; }
});

addExercise(false);
if (location.hash === '#create-workout') refreshTemplates();
