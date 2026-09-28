const workoutForm = document.querySelector('#coach-workout-form');
const exerciseFields = document.querySelector('#exercise-fields');
const exerciseTemplate = document.querySelector('#exercise-template');
const addExerciseButton = document.querySelector('#add-exercise');

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
    submit.disabled = true;
    status.textContent = 'Workout opslaan…';
    const payload = {
        name: workoutForm.elements.namedItem('title').value.trim(),
        description: workoutForm.elements.namedItem('description').value.trim(),
        athleteId: workoutForm.elements.namedItem('athlete').value,
        exercises: Array.from(exerciseFields.querySelectorAll('.exercise-row'), row => ({
            name: row.querySelector('[name="exercise"]').value.trim(),
            sets: Number(row.querySelector('[name="sets"]').value),
            reps: Number(row.querySelector('[name="reps"]').value),
            loadMode: row.querySelector('[name="loadMode"]').value,
            loadValue: row.querySelector('[name="loadValue"]').value === '' ? null : Number(row.querySelector('[name="loadValue"]').value)
        }))
    };
    try {
        const response = await fetch('http://localhost:8080/api/workouts', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Opslaan mislukt. Controleer de gegevens en probeer opnieuw.');
        }
        await response.json();
        workoutForm.reset();
        exerciseFields.replaceChildren();
        addExercise(false);
        status.textContent = '';
        document.querySelector('#coach-workout-status').textContent = 'Workout opgeslagen in Java.';
        window.location.hash = 'dashboard';
        document.dispatchEvent(new CustomEvent('cap:workouts-saved'));
    } catch (error) {
        status.textContent = `${error.message} Je invoer blijft behouden. Bij een verbindingsfout: controleer eerst de bibliotheek voordat je opnieuw opslaat.`;
    } finally { submit.disabled = false; }
});

addExercise(false);
