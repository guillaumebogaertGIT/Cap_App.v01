(() => {
    const api = window.capAccount,
        $ = (id) => document.getElementById(id);
    const node = (tag, text, cls) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (cls) item.className = cls;
        return item;
    };
    const button = (text, action) => {
        const item = node('button', text, 'secondary-button');
        item.type = 'button';
        item.addEventListener('click', () => api.run(action));
        return item;
    };
    let owner = null,
        library = [],
        assignments = [],
        people = [],
        sessions = [],
        sessionWorkouts = [],
        history = [],
        records = [],
        active = null,
        catalog = [],
        draftExercises = [],
        editId = null,
        editAssignmentId = null,
        generation = 0;
    let progressTimer = null,
        progressChain = Promise.resolve();
    const coach = () => owner?.role === 'COACH';
    const today = () =>
        new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    $('real-workouts').innerHTML = `
        <section id="real-builder" hidden aria-labelledby="real-builder-title"><h2 id="real-builder-title">Workout maken</h2>
        <form id="real-workout-form" class="club-form"><label>Naam workout<input name="name" required maxlength="100"></label><label>Beschrijving<textarea name="description" maxlength="1000" rows="2"></textarea></label>
        <div class="club-form-grid"><label>Workout type<select name="workoutType"><option value="other">Overig</option><option value="lower-body">Lower body</option><option value="upper-body">Upper body</option><option value="full-body">Full body</option><option value="conditioning">Conditioning</option></select></label><label>Groep<select name="category"><option value="">Algemeen</option><option>CAP HYROX</option><option>CAP Hybrid</option><option>CAP Move</option><option>CAP Lift &amp; HIT</option><option value="Private">Privé</option><option>Open gym</option><option value="Athlete">Persoonlijk</option></select></label></div>
        <div class="club-form-grid"><label>Direct toewijzen aan sporter (optioneel)<select name="athleteId"><option value="">Alleen opslaan in bibliotheek</option></select></label><label>Dag voor directe toewijzing<input name="assignedDate" type="date"></label></div><p class="sample-notice">Percentages gebruiken de PR van de gekozen sporter bij hetzelfde aantal herhalingen. Een groepsles gebruikt vaste gewichten.</p>
        <datalist id="real-exercise-names"></datalist><div id="real-editor-exercises"></div><button id="real-add-exercise" class="secondary-button" type="button">+ Oefening toevoegen</button>
        <div class="editor-actions"><button type="submit" class="primary-button">Workout bewaren</button><button id="real-new-workout" type="button" class="secondary-button">Nieuwe workout</button></div></form></section>
        <section aria-labelledby="real-library-title"><h2 id="real-library-title">Workouts</h2><div id="real-workout-list"></div></section>
        <section id="real-active-panel" hidden aria-labelledby="real-active-title"><h2 id="real-active-title" tabindex="-1">Lopende training</h2><p id="real-training-timer"></p><p class="sample-notice">Vul alleen uitgevoerde sets in. 0 kg betekent geen toegevoegd gewicht. Je voortgang wordt automatisch bewaard. Gebruik Voortgang bewaren voordat je dit scherm verlaat.</p><p id="real-progress-status" role="status"></p><div id="real-training-body"></div></section>
        <section aria-labelledby="real-history-title"><h2 id="real-history-title">Trainingshistorie &amp; records</h2><label id="real-history-athlete-label" hidden>Sporter<select id="real-history-athlete"><option value="">Kies een sporter</option></select></label><div id="real-records"></div><div id="real-history"></div></section>
        <dialog id="real-assign-dialog" class="club-dialog" aria-labelledby="real-assign-title"><form id="real-assign-form" class="club-form"><h2 id="real-assign-title">Workout toewijzen</h2><input name="id" type="hidden"><label>Bestemming<select name="destination"><option value="athlete">Persoonlijke workout</option><option value="session">Geplande les</option></select></label><div id="real-personal-fields"><label>Sporter<select name="athleteId"></select></label><label>Planning<select name="schedule"><option value="day">Vaste dag</option><option value="week">Flexibele week</option></select></label><label>Datum (bij een week: maandag)<input name="date" type="date"></label></div><label id="real-session-field" hidden>Les<select name="sessionId"></select></label><button type="submit" class="primary-button">Toewijzen</button><button id="real-close-assign" type="button" class="secondary-button">Sluiten</button></form></dialog>`;
    const form = $('real-workout-form'),
        assignForm = $('real-assign-form');
    async function mutate(path, method, body) {
        const csrf = await api.request('/csrf');
        return api.request(path, {
            method,
            headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
            body: body == null ? undefined : JSON.stringify(body),
        });
    }
    function route() {
        let view = ['workouts', 'invitations', 'exercises'].includes(location.hash.slice(1))
            ? location.hash.slice(1)
            : 'planning';
        if (!coach() && ['invitations', 'exercises'].includes(view)) view = 'planning';
        $('real-planning').hidden = view !== 'planning';
        $('real-workouts').hidden = view !== 'workouts';
        $('coach-invitations').hidden = !coach() || view !== 'invitations';
        $('real-invitations-link').hidden = !coach();
        document.querySelectorAll('.real-tabs a').forEach((link) => {
            if (link.hash === '#' + view) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    }
    window.addEventListener('hashchange', route);
    function numeric(labelText, value, min, max, step, onInput) {
        const label = node('label', labelText),
            input = document.createElement('input');
        input.type = 'number';
        input.min = min;
        input.max = max;
        input.step = step;
        input.value = value ?? '';
        input.inputMode = step === '1' ? 'numeric' : 'decimal';
        input.addEventListener('input', () =>
            onInput(input.value === '' ? null : Number(input.value)),
        );
        label.append(input);
        return label;
    }
    function editor() {
        const container = $('real-editor-exercises');
        container.replaceChildren();
        draftExercises.forEach((exercise, index) => {
            const box = node('fieldset', null, 'real-exercise-row');
            box.append(node('legend', 'Oefening ' + (index + 1)));
            const label = node('label', 'Oefening');
            const name = document.createElement('input');
            name.required = true;
            name.maxLength = 100;
            name.setAttribute('list', 'real-exercise-names');
            name.value = exercise.name;
            name.addEventListener('input', () => (exercise.name = name.value));
            label.append(name);
            box.append(label);
            const options = node('div', null, 'club-form-grid');
            options.append(
                numeric(
                    'Rust (seconden)',
                    exercise.restSeconds,
                    0,
                    3600,
                    '1',
                    (value) => (exercise.restSeconds = value),
                ),
            );
            const tempoLabel = node('label', 'Tempo');
            const tempo = document.createElement('input');
            tempo.maxLength = 40;
            tempo.value = exercise.tempo || '';
            tempo.addEventListener('input', () => (exercise.tempo = tempo.value));
            tempoLabel.append(tempo);
            options.append(tempoLabel);
            const supersetLabel = node('label', 'Superset');
            const superset = document.createElement('select');
            superset.add(new Option('Geen', ''));
            ['A', 'B', 'C', 'D', 'E', 'F'].forEach((letter) =>
                superset.add(new Option(letter, letter)),
            );
            superset.value = exercise.supersetGroup || '';
            superset.addEventListener('change', () => (exercise.supersetGroup = superset.value));
            supersetLabel.append(superset);
            options.append(supersetLabel);
            box.append(options);
            exercise.setPlan.forEach((set, setIndex) => {
                const row = node('div', null, 'real-set-target');
                row.append(
                    node('strong', 'Set ' + (setIndex + 1)),
                    numeric('Reps', set.reps, 1, 1000, '1', (value) => (set.reps = value)),
                );
                const modeLabel = node('label', 'Gewicht');
                const mode = document.createElement('select');
                [
                    ['none', 'Geen doel'],
                    ['fixed', 'Kg'],
                    ['percent', '% van PR'],
                ].forEach(([value, text]) => mode.add(new Option(text, value)));
                mode.value = set.loadMode || 'none';
                modeLabel.append(mode);
                row.append(modeLabel);
                const load = numeric(
                    'Kg / percentage',
                    set.loadValue,
                    0,
                    2000,
                    '0.01',
                    (value) => (set.loadValue = value),
                );
                load.hidden = mode.value === 'none';
                mode.addEventListener('change', () => {
                    set.loadMode = mode.value;
                    load.hidden = mode.value === 'none';
                    if (mode.value === 'none') set.loadValue = null;
                });
                row.append(load);
                row.append(
                    button('Verwijder set', async () => {
                        exercise.setPlan.splice(setIndex, 1);
                        editor();
                    }),
                );
                box.append(row);
            });
            box.append(
                button('+ Set', async () => {
                    if (exercise.setPlan.length < 100) {
                        exercise.setPlan.push({ reps: 10, loadMode: 'none', loadValue: null });
                        editor();
                    }
                }),
                button('Verwijder oefening', async () => {
                    draftExercises.splice(index, 1);
                    editor();
                }),
            );
            container.append(box);
        });
    }
    $('real-add-exercise').addEventListener('click', () => {
        if (!form.elements.name.reportValidity()) return;
        if (draftExercises.length < 100) {
            draftExercises.push({
                name: '',
                setPlan: [{ reps: 10, loadMode: 'none', loadValue: null }],
                restSeconds: null,
                tempo: '',
                supersetGroup: '',
            });
            editor();
        }
    });
    $('real-new-workout').addEventListener('click', () => {
        if (draftExercises.length && !confirm('De huidige invoer wissen voor een nieuwe workout?'))
            return;
        editId = null;
        editAssignmentId = null;
        draftExercises = [];
        form.reset();
        editor();
    });
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        api.run(async () => {
            if (
                !draftExercises.length ||
                draftExercises.some((exercise) => !exercise.setPlan.length)
            )
                throw new Error('Voeg minstens één oefening en één set per oefening toe.');
            const values = Object.fromEntries(new FormData(form));
            const exercises = draftExercises.map((exercise) => ({
                ...exercise,
                sets: exercise.setPlan.length,
                reps: exercise.setPlan[0].reps,
                loadMode: exercise.setPlan[0].loadMode,
                loadValue: exercise.setPlan[0].loadValue,
            }));
            const path = editAssignmentId
                ? '/api/workout-assignments/' + editAssignmentId
                : '/api/workouts' + (editId ? '/' + editId : '');
            await mutate(path, editId || editAssignmentId ? 'PUT' : 'POST', {
                ...values,
                exercises,
                athleteId: form.elements.athleteId.value,
                assignedDate: form.elements.assignedDate.value,
            });
            editId = null;
            editAssignmentId = null;
            draftExercises = [];
            form.reset();
            editor();
            await refresh();
            api.message('Workout bewaard. Wijs hem toe aan een sporter of les via Toewijzen.');
        });
    });
    function openAssign(workout) {
        assignForm.reset();
        assignForm.elements.namedItem('id').value = workout.id;
        assignForm.elements.date.value = today();
        assignForm.elements.athleteId.replaceChildren();
        people
            .filter((person) => person.role === 'ATHLETE')
            .forEach((person) =>
                assignForm.elements.athleteId.add(new Option(person.name, person.id)),
            );
        assignForm.elements.sessionId.replaceChildren();
        sessions
            .filter((session) => session.type !== 'Open gym' && session.date >= today())
            .forEach((session) =>
                assignForm.elements.sessionId.add(
                    new Option(
                        `${window.capDateTime.date(session.date)} ${session.start.slice(0, 5)} · ${session.title}`,
                        session.id,
                    ),
                ),
            );
        let error = $('real-assign-error');
        if (!error) {
            error = node('p');
            error.id = 'real-assign-error';
            error.setAttribute('role', 'alert');
            assignForm.append(error);
        }
        error.textContent = '';
        assignDestination();
        $('real-assign-dialog').showModal();
    }
    function assignDestination() {
        const session = assignForm.elements.destination.value === 'session';
        $('real-personal-fields').hidden = session;
        $('real-session-field').hidden = !session;
        assignForm.elements.date.required = !session;
        assignForm.elements.athleteId.required = !session;
        assignForm.elements.sessionId.required = session;
    }
    assignForm.elements.destination.addEventListener('change', assignDestination);
    $('real-close-assign').addEventListener('click', () => $('real-assign-dialog').close());
    assignForm.addEventListener('submit', (event) => {
        event.preventDefault();
        api.run(async () => {
            const value = Object.fromEntries(new FormData(assignForm)),
                id = value.id;
            try {
                if (value.destination === 'session')
                    await mutate(`/api/club/sessions/${value.sessionId}/workout`, 'PUT', {
                        workoutId: id,
                    });
                else
                    await mutate(`/api/workouts/${id}/assignment`, 'POST', {
                        athleteId: value.athleteId,
                        date: value.schedule === 'day' ? value.date : '',
                        weekStart: value.schedule === 'week' ? value.date : null,
                    });
            } catch (error) {
                $('real-assign-error').textContent = error.message;
                throw error;
            }
            $('real-assign-dialog').close();
            await refresh();
            api.message('Workout toegewezen.');
        });
    });
    function workoutCard(workout, caption) {
        const card = node('details', null, 'club-card');
        card.append(node('summary', workout.name + (caption ? ' · ' + caption : '')));
        if (workout.description) card.append(node('p', workout.description));
        card.append(window.capWorkoutView.render(workout.exercises));
        return card;
    }
    function loadEditor(workout, assignmentId = null) {
        editId = assignmentId ? null : workout.id;
        editAssignmentId = assignmentId;
        form.elements.name.value = workout.name;
        form.elements.description.value = workout.description || '';
        form.elements.category.value = workout.category || '';
        form.elements.workoutType.value = workout.workoutType || 'other';
        form.elements.athleteId.value = workout.athleteId || '';
        form.elements.assignedDate.value = workout.assignedDate || '';
        draftExercises = structuredClone(workout.exercises).map((exercise) => ({
            ...exercise,
            setPlan: exercise.setPlan?.length
                ? exercise.setPlan
                : Array.from({ length: exercise.sets }, () => ({
                      reps: exercise.reps,
                      loadMode: exercise.loadMode || 'none',
                      loadValue: exercise.loadValue ?? null,
                  })),
        }));
        editor();
        form.elements.name.focus();
    }
    async function start(assignmentId) {
        active = await mutate('/api/training/active', 'POST', { assignmentId });
        renderTraining();
        $('real-active-title').focus();
        api.message(
            active.assignmentId === assignmentId
                ? 'Training gestart.'
                : 'Je hebt nog een lopende training. Rond die eerst af of verwerp die training.',
        );
    }
    function renderLibrary() {
        $('real-builder').hidden = !coach();
        $('real-history-athlete-label').hidden = !coach();
        const list = $('real-workout-list');
        list.replaceChildren();
        if (coach())
            library.forEach((workout) => {
                const card = workoutCard(workout, workout.category);
                card.append(
                    button('Toewijzen', async () => openAssign(workout)),
                    button('Bewerken', async () => {
                        loadEditor(workout);
                    }),
                    button('Workout verwijderen', async () => {
                        if (
                            confirm(
                                'Workout en toewijzingen verwijderen? Afgeronde trainingen blijven behouden.',
                            )
                        ) {
                            await mutate('/api/workouts/' + workout.id, 'DELETE');
                            await refresh();
                        }
                    }),
                );
                list.append(card);
            });
        assignments.forEach((assignment) => {
            const workout = assignment.planned,
                person = people.find((person) => person.id === workout.athleteId);
            const caption =
                (coach() ? (person?.name || 'Sporter') + ' · ' : '') +
                (workout.assignedDate
                    ? window.capDateTime.date(workout.assignedDate)
                    : 'Week vanaf ' + window.capDateTime.date(assignment.weekStart));
            const card = workoutCard(workout, caption);
            if (assignment.note) card.append(node('p', 'Coachnotitie: ' + assignment.note));
            if (!coach()) {
                card.append(button('Start training', () => start(assignment.assignmentId)));
                if (assignment.weekStart) {
                    const label = node('label', 'Kies een dag in deze week');
                    const input = document.createElement('input');
                    input.type = 'date';
                    input.value = workout.assignedDate || '';
                    input.min = assignment.weekStart;
                    const end = new Date(assignment.weekStart + 'T12:00:00Z');
                    end.setUTCDate(end.getUTCDate() + 6);
                    input.max = end.toISOString().slice(0, 10);
                    label.append(input);
                    card.append(
                        label,
                        button('Dag bewaren', async () => {
                            await mutate(
                                '/api/workout-assignments/' + assignment.assignmentId + '/schedule',
                                'PUT',
                                { date: input.value },
                            );
                            await refresh();
                        }),
                    );
                }
            } else {
                const label = node('label', 'Coachnotitie');
                const note = document.createElement('textarea');
                note.maxLength = 2000;
                note.value = assignment.note || '';
                label.append(note);
                card.append(
                    label,
                    button('Notitie bewaren', async () => {
                        await mutate(
                            '/api/workout-assignments/' + assignment.assignmentId + '/note',
                            'PUT',
                            { note: note.value },
                        );
                        await refresh();
                    }),
                    button('Deze workout bewerken', async () =>
                        loadEditor(workout, assignment.assignmentId),
                    ),
                    button('Toewijzing verwijderen', async () => {
                        if (confirm('Deze toewijzing verwijderen?')) {
                            await mutate(
                                '/api/workout-assignments/' + assignment.assignmentId,
                                'DELETE',
                            );
                            await refresh();
                        }
                    }),
                );
            }
            list.append(card);
        });
        if (!coach())
            sessionWorkouts.forEach((session) => {
                const card = workoutCard(
                    session.workout,
                    window.capDateTime.date(session.date) + ' · ' + session.title,
                );
                card.append(button('Start training', () => start('session:' + session.sessionId)));
                list.append(card);
            });
        if (!list.children.length)
            list.append(
                node(
                    'p',
                    coach()
                        ? 'Nog geen opgeslagen workouts. Maak je eerste workout.'
                        : 'Nog geen workouts toegewezen. Een geboekte les verschijnt hier zodra de coach een workout koppelt.',
                    'club-empty',
                ),
            );
        $('real-records').replaceChildren();
        records.forEach((record) =>
            $('real-records').append(
                node('p', `${record.exercise}: ${record.kg} kg × ${record.reps} reps`),
            ),
        );
        $('real-history').replaceChildren();
        history
            .slice()
            .reverse()
            .forEach((session) => {
                const card = workoutCard(
                    session.planned,
                    window.capDateTime.dateTime(session.finishedAt),
                );
                session.exercises.forEach((result) =>
                    card.append(
                        node(
                            'p',
                            session.planned.exercises[result.exerciseIndex].name +
                                ': ' +
                                result.sets
                                    .map(
                                        (set) =>
                                            `${set.reps} reps × ${set.kg} kg${set.rir != null ? ' · RIR ' + set.rir : ''}`,
                                    )
                                    .join(' / '),
                        ),
                    ),
                );
                $('real-history').append(card);
            });
    }
    function renderTraining() {
        $('real-active-panel').hidden = !active || coach();
        const area = $('real-training-body');
        area.replaceChildren();
        if (!active || coach()) return;
        area.append(node('h3', active.planned.name));
        const form = document.createElement('form');
        form.className = 'club-form';
        active.exercises.forEach((result) => {
            const exercise = active.planned.exercises[result.exerciseIndex],
                box = node('fieldset', null, 'real-exercise-row');
            box.append(node('legend', exercise.name), window.capWorkoutView.render([exercise]));
            result.sets.forEach((set, index) => {
                const row = node('div', null, 'real-training-set');
                row.append(node('strong', 'Set ' + (index + 1)));
                for (const [field, label, min, max, step] of [
                    ['reps', 'Reps', 1, 1000, '1'],
                    ['kg', 'Kg', 0, 2000, '0.01'],
                    ['rir', 'RIR (optioneel)', 0, 10, '1'],
                ]) {
                    const wrapper = numeric(label, set[field], min, max, step, (value) => {
                        set[field] = value;
                        queueProgress();
                    });
                    const input = wrapper.querySelector('input');
                    input.required = field !== 'rir';
                    if (field === 'kg')
                        input.addEventListener(
                            'wheel',
                            () => {
                                if (document.activeElement === input) input.blur();
                            },
                            { passive: true },
                        );
                    row.append(wrapper);
                }
                row.append(
                    button('Verwijder set', async () => {
                        result.sets.splice(index, 1);
                        renderTraining();
                        queueProgress();
                    }),
                );
                box.append(row);
            });
            box.append(
                button('+ Set', async () => {
                    if (result.sets.length < 100) {
                        result.sets.push({ reps: 0, kg: null, rir: null });
                        renderTraining();
                        queueProgress();
                    }
                }),
            );
            form.append(box);
        });
        form.append(
            button('Voortgang bewaren', async () => {
                clearTimeout(progressTimer);
                await progressChain;
                await saveProgress();
                api.message('Voortgang op de server bewaard.');
            }),
        );
        const finish = node('button', 'Training afronden', 'primary-button');
        finish.type = 'submit';
        form.append(finish);
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            api.run(async () => {
                clearTimeout(progressTimer);
                await progressChain;
                await saveProgress();
                await mutate('/api/training', 'POST', {
                    id: active.id,
                    version: active.version,
                    exercises: active.exercises,
                });
                active = null;
                await refresh();
                api.message('Training opgeslagen. Je historie en records zijn bijgewerkt.');
            });
        });
        area.append(
            form,
            button('Training verwerpen', async () => {
                if (confirm('Deze lopende training verwijderen?')) {
                    clearTimeout(progressTimer);
                    await progressChain.catch(() => {});
                    await mutate('/api/training/active', 'DELETE');
                    active = null;
                    renderTraining();
                    api.message('Training verworpen.');
                }
            }),
        );
    }
    async function saveProgress() {
        if (!active) return;
        const current = active;
        const updated = await mutate('/api/training/active', 'PUT', {
            id: current.id,
            version: current.version,
            exercises: current.exercises.map((exercise) => ({
                ...exercise,
                sets: exercise.sets.map((set) => ({ ...set, reps: set.reps ?? 0 })),
            })),
        });
        if (active === current) current.version = updated.version;
        if (active === current)
            $('real-progress-status').textContent = 'Voortgang op de server bewaard.';
    }
    function queueProgress() {
        clearTimeout(progressTimer);
        $('real-progress-status').textContent = 'Wijzigingen bewaren…';
        const id = active?.id;
        progressTimer = setTimeout(() => {
            progressChain = progressChain
                .catch(() => {})
                .then(async () => {
                    if (active?.id === id) await saveProgress();
                });
            progressChain.catch((error) => {
                if (active?.id === id)
                    $('real-progress-status').textContent =
                        'Voortgang nog niet bewaard: ' + error.message;
            });
        }, 700);
    }
    setInterval(() => {
        if (active) {
            const seconds = Math.max(
                0,
                Math.floor((Date.now() - Date.parse(active.startedAt)) / 1000),
            );
            $('real-training-timer').textContent = [
                Math.floor(seconds / 3600),
                Math.floor(seconds / 60) % 60,
                seconds % 60,
            ]
                .map((value) => String(value).padStart(2, '0'))
                .join(':');
        }
    }, 1000);
    async function refresh() {
        clearTimeout(progressTimer);
        if (active) {
            await progressChain;
            await saveProgress();
        }
        const ticket = ++generation,
            id = owner?.id;
        if (!id) return;
        const historyTarget = coach() ? $('real-history-athlete').value : '';
        const suffix = historyTarget ? '?athleteId=' + encodeURIComponent(historyTarget) : '';
        const data = await Promise.all([
            api.request('/api/workouts'),
            api.request('/api/workout-assignments'),
            coach() ? api.request('/api/club/people') : Promise.resolve([]),
            api.request('/api/club/sessions'),
            api.request('/api/session-workouts'),
            api.request('/api/training' + suffix),
            api.request('/api/records' + suffix),
            coach() ? Promise.resolve(null) : api.request('/api/training/active'),
        ]);
        if (ticket !== generation || id !== owner?.id) return;
        const localActive = active;
        [library, assignments, people, sessions, sessionWorkouts, history, records, active] = data;
        if (localActive && active?.id === localActive.id) active = localActive;
        const select = $('real-history-athlete'),
            value = select.value;
        select.replaceChildren(new Option('Kies een sporter', ''));
        people
            .filter((person) => person.role === 'ATHLETE')
            .forEach((person) => select.add(new Option(person.name, person.id)));
        select.value = value;
        const recipient = form.elements.athleteId,
            recipientValue = recipient.value;
        recipient.replaceChildren(new Option('Alleen opslaan in bibliotheek', ''));
        people
            .filter((person) => person.role === 'ATHLETE')
            .forEach((person) => recipient.add(new Option(person.name, person.id)));
        recipient.value = recipientValue;
        renderLibrary();
        renderTraining();
        route();
    }
    $('real-history-athlete').addEventListener('change', () => api.run(refresh));
    window.addEventListener('hashchange', () => {
        if (owner && location.hash === '#workouts')
            refresh().catch((error) => api.message(error.message));
    });
    document.addEventListener('cap:planning-updated', () => {
        if (owner && location.hash === '#workouts')
            refresh().catch((error) => api.message(error.message));
    });
    document.addEventListener('cap:catalog-updated', () => {
        if (coach())
            api.request('/api/exercises')
                .then((items) => {
                    if (!coach()) return;
                    catalog = items;
                    $('real-exercise-names').replaceChildren();
                    items
                        .filter((item) => !item.archived)
                        .forEach((item) =>
                            $('real-exercise-names').append(new Option(item.name, item.name)),
                        );
                })
                .catch((error) => api.message(error.message));
    });
    document.addEventListener('cap:account-ready', (event) => {
        clearTimeout(progressTimer);
        progressChain = Promise.resolve();
        owner = event.detail;
        generation++;
        active = null;
        library = [];
        assignments = [];
        history = [];
        records = [];
        people = [];
        sessions = [];
        sessionWorkouts = [];
        draftExercises = [];
        editId = null;
        editAssignmentId = null;
        form.reset();
        editor();
        form.elements.athleteId.replaceChildren(new Option('Alleen opslaan in bibliotheek', ''));
        assignForm.elements.athleteId.replaceChildren();
        assignForm.elements.sessionId.replaceChildren();
        $('real-history-athlete').replaceChildren(new Option('Kies een sporter', ''));
        $('real-workout-list').replaceChildren();
        $('real-records').replaceChildren();
        $('real-history').replaceChildren();
        $('real-training-body').replaceChildren();
        if ($('real-assign-dialog').open) $('real-assign-dialog').close();
        route();
        if (owner) {
            refresh().catch((error) => api.message(error.message));
            if (coach())
                api.request('/api/exercises')
                    .then((items) => {
                        if (!owner) return;
                        catalog = items;
                        $('real-exercise-names').replaceChildren();
                        items
                            .filter((item) => !item.archived)
                            .forEach((item) =>
                                $('real-exercise-names').append(new Option(item.name, item.name)),
                            );
                    })
                    .catch((error) => api.message(error.message));
        }
    });
})();
