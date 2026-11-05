// Keep choosing, creating and assigning a workout anchored to the same saved lesson.
(() => {
    const allowed = () => previewRole === 'coach' && window.capAccess.signedIn
        && (!window.capReal || window.capCan('WORKOUT_TEMPLATE_MANAGE'));
    const node = (tag, text, className) => {
        const value = document.createElement(tag);
        if (text) value.textContent = text;
        if (className) value.className = className;
        return value;
    };
    const dialog = node('dialog', null, 'club-dialog lesson-workout-dialog');
    dialog.id = 'lesson-workout-dialog';
    dialog.setAttribute('aria-labelledby', 'lesson-workout-title');
    dialog.innerHTML = `<div class="session-dialog-header"><h2 id="lesson-workout-title">Workout toevoegen</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p class="lesson-workout-context"></p><p class="lesson-workout-current sample-notice"></p>
        <button type="button" class="secondary-button" data-new>Nieuwe workout maken</button>
        <label class="library-search">Opgeslagen workouts zoeken<input type="search" maxlength="100" placeholder="Zoek op naam of categorie"></label>
        <fieldset class="lesson-workout-list"><legend>Kies een opgeslagen workout</legend></fieldset>
        <details class="lesson-workout-preview" hidden><summary>Gekozen workout bekijken</summary><div></div></details>
        <p role="status" aria-live="polite"></p><button type="button" class="secondary-button" data-retry hidden>Opnieuw laden</button>
        <div class="editor-actions"><button type="button" class="primary-button" data-add disabled>Toevoegen aan deze les</button><button type="button" class="secondary-button" data-back>Terug naar les</button></div>`;
    document.body.append(dialog);
    const $ = selector => dialog.querySelector(selector);
    const search = $('input[type=search]'), list = $('fieldset'), status = $('[role=status]');
    const add = $('[data-add]'), retry = $('[data-retry]'), preview = $('details');
    let ids = [], returnLesson = null, workouts = [], selected = '', busy = false, loading = false, generation = 0;
    let context = null, pendingLoad = Promise.resolve();
    const banner = node('section', null, 'lesson-workout-banner'); banner.hidden = true;
    banner.setAttribute('aria-label', 'Geselecteerde les');
    const bannerTitle = node('strong', 'Workout voor deze les'), bannerText = node('p');
    const back = node('button', 'Terug naar les', 'secondary-button'); back.type = 'button';
    banner.append(bannerTitle, bannerText, back);
    document.querySelector('#create-workout > header').after(banner);

    function description(targetIds) {
        return window.capClub.workoutTargets(targetIds).map(session =>
            `${session.title} · ${window.capDateTime.date(session.date)} · ${session.start}`).join(' / ');
    }
    function setBusy(value) {
        busy = value;
        dialog.querySelectorAll('button, input').forEach(control => { control.disabled = value; });
        add.disabled = value || loading || !selected;
        $('[data-new]').disabled = value || loading || ids.length === 0;
    }
    function clearContext() { context = null; banner.hidden = true; }
    function returnToLesson() {
        if (busy) return;
        ++generation;
        dialog.close(); clearContext();
        location.hash = 'planning';
        // Let the normal navigation handler finish before selecting/expanding the lesson.
        setTimeout(() => {
            if (location.hash === '#planning' && returnLesson) document.dispatchEvent(new CustomEvent('cap:session-saved', { detail: returnLesson }));
        }, 0);
    }
    function updateContext() {
        $('.lesson-workout-context').textContent = description(ids);
        const targets = window.capClub.workoutTargets(ids);
        $('.lesson-workout-current').textContent = targets.some(session => session.hasWorkout)
            ? 'Deze les heeft al een programma. Je bevestigt het vervangen voordat we iets wijzigen.'
            : 'Kies een opgeslagen workout of maak een nieuwe voor deze les.';
        if (ids.length > 1) $('.lesson-workout-current').textContent += ` De workout wordt aan ${ids.length} lessen toegevoegd.`;
        add.textContent = ids.length > 1 ? `Toevoegen aan ${ids.length} lessen` : 'Toevoegen aan deze les';
    }
    function render() {
        list.querySelectorAll('label, p').forEach(item => item.remove());
        const query = search.value.trim().toLocaleLowerCase('nl-BE');
        const matches = workouts.filter(workout => `${workout.name} ${workout.category || ''}`.toLocaleLowerCase('nl-BE').includes(query));
        for (const workout of matches) {
            const label = node('label');
            const radio = node('input'); radio.type = 'radio'; radio.name = 'lesson-workout'; radio.value = workout.id;
            radio.checked = selected === workout.id;
            const text = node('span'); text.append(node('strong', workout.name), node('small', `${workout.category || 'Overig'} · ${workout.exercises.length} oefeningen`));
            radio.addEventListener('change', () => { selected = workout.id; render(); list.querySelector('input:checked')?.focus(); });
            label.append(radio, text); list.append(label);
        }
        if (!matches.length) list.append(node('p', workouts.length ? 'Geen workouts gevonden.' : 'Nog geen opgeslagen workouts. Maak een nieuwe workout voor deze les.', 'sample-notice'));
        const workout = workouts.find(item => item.id === selected);
        preview.hidden = !workout;
        preview.querySelector('div').replaceChildren(...(workout ? [window.capWorkoutView.render(workout.exercises)] : []));
        setBusy(busy);
    }
    async function load(preselected) {
        const current = ++generation;
        loading = true; status.textContent = 'Opgeslagen workouts laden…'; retry.hidden = true; setBusy(false);
        try {
            await window.capClub.refreshSessions();
            const targets = window.capClub.workoutTargets(ids);
            if (targets.length !== ids.length) throw new Error('Een gekozen les is niet meer beschikbaar. Keer terug naar Planning.');
            const response = await fetch('http://localhost:8080/api/workouts', { cache: 'no-store', signal: AbortSignal.timeout(10000) });
            if (!response.ok) throw new Error('Opgeslagen workouts konden niet worden geladen. Probeer opnieuw.');
            const values = await response.json();
            if (!Array.isArray(values)) throw new Error('Opgeslagen workouts konden niet worden gelezen.');
            if (current !== generation || !dialog.open) return;
            workouts = values; selected = preselected && values.some(workout => workout.id === preselected) ? preselected : '';
            status.textContent = ''; updateContext();
        } catch (error) {
            if (current !== generation || !dialog.open) return;
            workouts = []; selected = ''; status.textContent = error.message; retry.hidden = false;
        } finally {
            if (current === generation) { loading = false; render(); }
        }
    }
    function open(targetIds, preselected = '') {
        if (!allowed() || busy) return false;
        clearContext(); ids = [...new Set(targetIds)];
        const targets = window.capClub.workoutTargets(ids);
        if (!ids.length || targets.length !== ids.length) return false;
        returnLesson = { id: targets[0].id, date: targets[0].date };
        workouts = []; selected = ''; search.value = ''; preview.open = false;
        location.hash = 'planning';
        updateContext(); render();
        if (!dialog.open) dialog.showModal();
        pendingLoad = load(preselected);
        return true;
    }
    async function attach() {
        if (busy || loading || !allowed()) return;
        const workout = workouts.find(item => item.id === selected);
        if (!workout) return;
        setBusy(true); status.textContent = 'Workout toevoegen…';
        let completed = 0;
        try {
            await window.capClub.refreshSessions();
            const targets = window.capClub.workoutTargets(ids);
            if (targets.length !== ids.length) throw new Error('Een gekozen les is niet meer beschikbaar. Keer terug naar Planning.');
            if (workout.exercises.some(exercise => exercise.loadMode === 'percent' || exercise.setPlan?.some(set => set.loadMode === 'percent'))) {
                throw new Error('Deze workout gebruikt PR-percentages. Gebruik voor een gedeelde les vaste gewichten of geen doelgewicht.');
            }
            if (targets.some(session => session.hasWorkout) && !window.confirm(`Het bestaande programma vervangen door “${workout.name}” voor ${description(ids)}?`)) {
                status.textContent = 'Niet vervangen. Het bestaande programma blijft behouden.'; return;
            }
            // Retain only unfinished lessons after a partial failure, so retry cannot repeat completed writes.
            while (ids.length) {
                await window.capClub.assignWorkout([ids[0]], workout);
                ids.shift(); completed++;
            }
            setBusy(false); returnToLesson();
            document.dispatchEvent(new CustomEvent('cap:lesson-workout-added', { detail: { ...returnLesson, name: workout.name } }));
        } catch (error) {
            status.textContent = `${completed ? `${completed} les(sen) bijgewerkt. ` : ''}${error.message} De workout blijft opgeslagen; probeer het toevoegen opnieuw.`;
            updateContext();
        } finally { setBusy(false); }
    }
    $('[data-new]').addEventListener('click', () => {
        if (busy || loading || !allowed()) return;
        const hasDraft = document.querySelector('#coach-workout-form').elements.namedItem('title').value.trim()
            || document.querySelector('#exercise-fields .exercise-row');
        if (hasDraft && !window.confirm('Je huidige workoutinvoer vervangen door een nieuwe workout voor deze les?')) return;
        ++generation; dialog.close(); context = { ids: [...ids] };
        banner.hidden = false; bannerText.textContent = description(ids);
        location.hash = 'create-workout';
        document.dispatchEvent(new CustomEvent('cap:new-lesson-workout'));
    });
    async function created(workout, targetIds) {
        // POST succeeded. Any assignment error now retries from this saved workout, never another POST.
        if (!open(targetIds, workout.id)) {
            clearContext(); location.hash = 'planning';
            document.getElementById('club-feedback').textContent = 'Workout opgeslagen. De gekozen les is niet meer beschikbaar; kies een andere les in Planning om de workout toe te voegen.';
            return;
        }
        const current = generation;
        // The chooser is also the recovery screen if refreshing the library fails.
        await pendingLoad;
        if (current === generation && selected === workout.id) await attach();
    }
    add.addEventListener('click', attach); search.addEventListener('input', render);
    retry.addEventListener('click', () => { pendingLoad = load(selected); });
    for (const button of [$('[data-back]'), $('.session-dialog-close')]) button.addEventListener('click', returnToLesson);
    dialog.addEventListener('cancel', event => { event.preventDefault(); returnToLesson(); });
    back.addEventListener('click', () => {
        if (document.querySelector('#coach-workout-form [type=submit]').disabled) return;
        if (!window.confirm('Terug naar de les? Je onopgeslagen workoutinvoer blijft in de editor staan.')) return;
        returnToLesson();
    });
    document.addEventListener('cap:viewchange', () => {
        if (context && !['#create-workout', '#exercises'].includes(location.hash)) clearContext();
        if (dialog.open && (location.hash !== '#planning' || !allowed()) && !busy) { ++generation; dialog.close(); }
    });
    window.capLessonWorkout = { open, created, description, get context() { return context; } };
})();
