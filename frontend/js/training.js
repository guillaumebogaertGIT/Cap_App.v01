// Drafts stay in this browser; completed sessions and PRs are stored by Java.
(() => {
    const key = 'cap-active-training-v1';
    const $ = id => document.getElementById(id);
    const node = (tag, text, className) => {
        const item = document.createElement(tag); if (text != null) item.textContent = text;
        if (className) item.className = className; return item;
    };
    const button = (text, action) => {
        const item = node('button', text, 'secondary-button'); item.type = 'button'; item.addEventListener('click', action); return item;
    };
    let active = null;
    let saving = false;
    let historyLoading = false;
    try {
        const draft = window.capReal ? window.capReal.draft : JSON.parse(localStorage.getItem(key));
        if (draft && draft.id && draft.planned?.exercises?.length && Array.isArray(draft.exercises)
            && Number.isFinite(Date.parse(draft.startedAt))) active = draft;
    } catch { /* An unreadable draft should not prevent the app from loading. */ }
    const section = node('section'); section.id = 'training'; section.hidden = true; section.dataset.view = '';
    section.setAttribute('aria-labelledby', 'training-title');
    section.innerHTML = `
        <header class="dashboard-header"><div><span class="card-label">Elke set telt</span><h2 id="training-title" tabindex="-1">Trainingslog</h2><p class="dashboard-intro">Uitgevoerde trainingen en records per aantal herhalingen.</p></div></header>
        <p class="sample-notice">${window.capReal ? 'Jouw trainingslog.' : 'Voorbeeldsporter: Guillaume.'} Alleen afgeronde trainingen tellen mee voor PRs. Gebruik steeds dezelfde gewichtsnotatie per oefening; 0 kg betekent geen toegevoegd gewicht.</p>
        <p id="training-message" role="status"></p>
        <div id="active-training"></div>
        <section id="finished-overview" class="settings-card" hidden aria-labelledby="finished-title"><h3 id="finished-title" tabindex="-1">Training afgerond</h3><div id="finished-content"></div></section>
        <div class="club-toolbar"><button type="button" class="secondary-button" id="reload-training">Geschiedenis vernieuwen</button></div>
        <h3>Persoonlijke records per rep-aantal</h3><div id="training-records" class="club-grid"></div>
        <h3>Afgelopen trainingen</h3><div id="training-history"></div>`;
    document.querySelector('main').append(section);
    let progressTimer;
    let progressQueue = Promise.resolve();
    function progressPayload(draft) {
        return draft.exercises.map(e => ({ exerciseIndex: e.exerciseIndex, sets: e.sets.map(s => ({ reps: s.reps === '' ? 0 : Number(s.reps), kg: s.kg === '' || s.kg == null ? null : Number(s.kg), rir: s.rir === '' || s.rir == null ? null : Number(s.rir) })) }));
    }
    async function flushProgress() {
        clearTimeout(progressTimer);
        if (!active || !window.capReal) return;
        const draft = active; const exercises = progressPayload(draft);
        progressQueue = progressQueue.catch(() => {}).then(async () => {
            const saved = await window.capReal.request('/api/training/active', 'PUT', { id: draft.id, version: draft.version, exercises });
            draft.version = saved.version;
        });
        return progressQueue;
    }
    function persist() {
        if (window.capReal) {
            clearTimeout(progressTimer);
            if (active && !active.finishedAt) progressTimer = setTimeout(() => flushProgress().catch(error => { $('training-message').textContent = error.message + ' Je invoer staat nog op dit scherm.'; }), 600);
            return;
        }
        try { if (active) localStorage.setItem(key, JSON.stringify(active)); else localStorage.removeItem(key); }
        catch { $('training-message').textContent = 'Je browser kan de lopende training niet bewaren. Houd deze pagina open tot je klaar bent.'; }
    }
    const duration = seconds => {
        const value = Math.max(0, Math.floor(seconds));
        return `${String(Math.floor(value / 3600)).padStart(2, '0')}:${String(Math.floor(value / 60) % 60).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
    };
    function tick() {
        if (active && $('training-timer')) $('training-timer').textContent = duration(((active.finishedAt ? Date.parse(active.finishedAt) : Date.now()) - Date.parse(active.startedAt)) / 1000);
    }
    setInterval(tick, 1000);
    function target(exercise) {
        if (exercise.setPlan?.length) return exercise.setPlan.map((set, i) => 'Set ' + (i + 1) + ': ' + window.capWorkoutView.targetText(set)).join(' | ');
        return `${exercise.sets} × ${exercise.reps}${exercise.targetKg != null ? ` · ${exercise.targetKg} kg` : ''}${exercise.restSeconds != null ? ` · Rust: ${exercise.restSeconds} s` : ''}${exercise.tempo ? ` · Tempo: ${exercise.tempo}` : ''}${exercise.supersetGroup ? ` · Superset ${exercise.supersetGroup}` : ''}${exercise.loadMode === 'percent' ? ` (${exercise.loadValue}% van ${exercise.referenceKg} kg bij ${exercise.reps} reps)` : ''}`;
    }
    function renderActive() {
        const container = $('active-training'); container.replaceChildren();
        if (previewRole !== 'athlete') return;
        if (!active) {
            container.append(node('p', 'Geen training bezig. Start een training via Workouts of je toegewezen workout.', 'club-empty')); return;
        }
        container.append(node('h3', active.planned.name));
        const timer = node('p', '', 'training-timer'); timer.id = 'training-timer'; container.append(timer);
        container.append(node('p', 'De timer loopt ook als je deze pagina verlaat. Verwijder niet-uitgevoerde sets; laat geen geplande reps staan die je niet hebt gedaan.', 'sample-notice'));
        const form = document.createElement('form'); form.className = 'club-form training-form';
        form.append(node('p', 'RIR (optioneel): hoeveel herhalingen kon je nog? 0 = geen meer.', 'sample-notice'));
        const exerciseContainers = [];
        window.capWorkoutView.blocks(active.planned.exercises).forEach(group => {
            const section = node('section', null, 'training-workout-block');
            section.append(node('h3', group.title));
            const exercises = node('div', null, 'training-block-exercises');
            section.append(exercises); form.append(section);
            group.items.forEach(({ index }) => { exerciseContainers[index] = exercises; });
        });
        active.planned.exercises.forEach((exercise, index) => {
            const block = node('fieldset', null, 'exercise-row'); block.append(node('legend', exercise.name), node('p', `Gepland: ${target(exercise)}`));
            if (exercise.supersetGroup) block.classList.add('superset-exercise');
            const sets = active.exercises[index].sets;
            sets.forEach((set, setIndex) => {
                const row = node('div', null, 'training-set'); row.append(node('span', `Set ${setIndex + 1}`));
                const plannedSet = window.capWorkoutView.setTargets(exercise)[setIndex];
                if (plannedSet && exercise.setPlan?.length) block.append(node('p', 'Doel set ' + (setIndex + 1) + ': ' + window.capWorkoutView.targetText(plannedSet), 'sample-notice'));
                [['reps', 'Reps', '1', '1000', '1'], ['kg', 'Kg', '0', '2000', '0.01'], ['rir', 'RIR', '0', '10', '1']].forEach(([field, labelText, min, max, step]) => {
                    const label = node('label', labelText); const input = document.createElement('input');
                    input.type = 'number'; input.required = field !== 'rir'; input.min = min; input.max = max; input.step = step;
                    if (field === 'rir') {
                        label.className = 'rir-field'; input.placeholder = '—';
                        input.setAttribute('aria-label', `Resterende herhalingen, ${exercise.name}, set ${setIndex + 1} (optioneel)`);
                        label.title = 'Reps in reserve: hoeveel herhalingen kon je nog? Optioneel, 0–10.';
                    }
                    input.inputMode = field === 'kg' ? 'decimal' : 'numeric'; input.value = set[field] ?? '';
                    if (field === 'kg') {
                        // Leave scrolling to the page instead of the number input's stepper.
                        input.addEventListener('wheel', () => {
                            if (document.activeElement === input) input.blur();
                        }, { passive: true });
                    }
                    input.disabled = Boolean(active.finishedAt);
                    input.addEventListener('input', () => { set[field] = input.value; persist(); });
                    label.append(input); row.append(label);
                });
                const remove = button('Verwijder set', () => { sets.splice(setIndex, 1); persist(); renderActive(); });
                remove.disabled = Boolean(active.finishedAt); row.append(remove); block.append(row);
            });
            const add = button('+ Set toevoegen', () => { sets.push({ reps: '', kg: '' }); persist(); renderActive(); });
            add.disabled = Boolean(active.finishedAt) || sets.length >= 100; block.append(add); exerciseContainers[index].append(block);
        });
        const finish = node('button', active.finishedAt ? 'Opslaan opnieuw proberen' : 'Training afronden', 'primary-button');
        finish.type = 'submit'; finish.disabled = saving; form.append(finish);
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (saving || !active || previewRole !== 'athlete' || !window.capAccess.signedIn) return;
            if (!active.exercises.some(e => e.sets.length)) { $('training-message').textContent = 'Vul minstens één uitgevoerde set in.'; return; }
            active.finishedAt ||= new Date().toISOString(); persist();
            saving = true; renderActive(); tick();
            $('training-message').textContent = 'Training opslaan…';
            const timeoutController = new AbortController(); const timeout = setTimeout(() => timeoutController.abort(), 15000);
            try {
                if (window.capReal) await flushProgress();
                const response = await fetch('http://localhost:8080/api/training', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: timeoutController.signal,
                    body: JSON.stringify({ id: active.id, version: active.version, workoutId: active.planned.id, assignmentId: active.planned.assignmentId || null, startedAt: active.startedAt, finishedAt: active.finishedAt,
                        exercises: active.exercises.map(e => ({ exerciseIndex: e.exerciseIndex, sets: e.sets.map(s => ({ reps: Number(s.reps), kg: Number(s.kg), rir: s.rir == null || s.rir === '' ? null : Number(s.rir) })) })) })
                });
                const result = await response.json();
                if (!response.ok) {
                    if (response.status === 400) { active.finishedAt = null; persist(); }
                    throw new Error(result.message || 'Opslaan mislukt.');
                }
                active = null; persist();
                $('training-message').textContent = 'Training opgeslagen. Je overzicht en records zijn bijgewerkt.';
                showOverview(result); await loadHistory();
            } catch (error) {
                $('training-message').textContent = `${error.message} Je training blijft bewaard; probeer opnieuw. Dezelfde sessie wordt niet dubbel opgeslagen.`;
            } finally { clearTimeout(timeout); saving = false; renderActive(); }
        });
        container.append(form); tick();
        if (!saving && !active.finishedAt) {
            const cancel = button('Training verwerpen', () => {
                const confirmation = node('div', null, 'club-card');
                confirmation.append(node('p', 'Deze lopende training verwijderen zonder resultaten op te slaan?'));
                confirmation.append(button('Ja, verwerpen', async () => {
                    if (window.capReal) { try { clearTimeout(progressTimer); await progressQueue; await window.capReal.request('/api/training/active', 'DELETE'); } catch (error) { $('training-message').textContent = error.message; return; } }
                    active = null; persist(); renderActive(); $('training-message').textContent = 'Lopende training verwijderd. Geschiedenis blijft behouden.';
                }), button('Verder trainen', () => { confirmation.remove(); cancel.disabled = false; }));
                cancel.after(confirmation); cancel.disabled = true;
                confirmation.querySelector('button').focus();
            });
            container.append(cancel);
        }
    }
    function sessionContent(session) {
        const content = node('div');
        content.append(node('p', `${window.capDateTime.dateTime(session.finishedAt)} · Duur ${duration(session.durationSeconds)}`));
        session.exercises.forEach(result => {
            const exercise = session.planned.exercises[result.exerciseIndex];
            content.append(node('h4', exercise.name), node('p', `Gepland: ${target(exercise)}`));
            const list = node('ul');
            result.sets.forEach((set, i) => list.append(node('li', `Set ${i + 1}: ${set.reps} reps × ${set.kg} kg${set.rir != null ? ` · RIR ${set.rir}` : ''}`)));
            if (!result.sets.length) list.append(node('li', 'Overgeslagen'));
            content.append(list);
        });
        content.append(node('h4', 'Nieuwe records / eerste registraties'));
        if (!session.newRecords.length) content.append(node('p', 'Geen nieuwe records in deze sessie.'));
        session.newRecords.forEach(record => content.append(node('p', `${record.exercise}: ${record.kg} kg × ${record.reps} reps`, 'training-pr')));
        return content;
    }
    function showOverview(session) {
        $('finished-overview').hidden = false;
        $('finished-content').replaceChildren(node('h4', session.planned.name), sessionContent(session));
        $('finished-title').focus();
    }
    async function loadHistory() {
        if (historyLoading) return; historyLoading = true;
        $('reload-training').disabled = true;
        try {
            const responses = await Promise.all(['/api/training', '/api/records'].map(path => fetch(`http://localhost:8080${path}`, { cache: 'no-store', signal: AbortSignal.timeout(8000) })));
            if (responses.some(r => !r.ok)) throw new Error('Load failed');
            const [history, records] = await Promise.all(responses.map(r => r.json()));
            if (!Array.isArray(history) || !Array.isArray(records)) throw new Error('Invalid data');
            $('training-history').replaceChildren(); $('training-records').replaceChildren();
            history.slice().reverse().forEach(session => {
                const entry = node('details', null, 'saved-workout');
                entry.append(node('summary', `${session.planned.name} · ${window.capDateTime.date(session.finishedAt)} · ${duration(session.durationSeconds)}`), sessionContent(session));
                $('training-history').append(entry);
            });
            if (!history.length) $('training-history').append(node('p', 'Nog geen afgeronde trainingen.', 'club-empty'));
            records.forEach(record => {
                const card = node('article', null, 'club-card'); card.append(node('h4', record.exercise), node('p', `${record.kg} kg × ${record.reps} reps`, 'training-pr')); $('training-records').append(card);
            });
            if (!records.length) $('training-records').append(node('p', 'Records verschijnen na je eerste afgeronde training.'));
        } catch { $('training-message').textContent = 'Geschiedenis niet geladen. Controleer de backend en klik op Geschiedenis vernieuwen.'; }
        finally { historyLoading = false; $('reload-training').disabled = false; }
    }
    window.capTraining = {
        isActive(workoutId) { return active?.planned.id === workoutId; },
        async start(workout) {
            if (previewRole !== 'athlete' || !window.capAccess.signedIn) return;
            if (!active && window.capReal) {
                try { active = await window.capReal.request('/api/training/active', 'POST', { assignmentId: workout.assignmentId }); $('finished-overview').hidden = true; }
                catch (error) { location.hash = 'training'; $('training-message').textContent = error.message; renderActive(); return; }
            } else if (!active) {
                active = { id: crypto.randomUUID(), planned: structuredClone(workout), startedAt: new Date().toISOString(), finishedAt: null,
                    exercises: workout.exercises.map((e, i) => ({ exerciseIndex: i, sets: Array.from({ length: e.sets }, () => ({ reps: '', kg: '' })) })) };
                persist(); $('finished-overview').hidden = true;
            } else if (active.planned.id !== workout.id || active.planned.assignmentId !== workout.assignmentId) {
                $('training-message').textContent = 'Er loopt al een training. Rond die eerst af voordat je een nieuwe start.';
            }
            location.hash = 'training'; renderActive();
        }
    };
    $('reload-training').addEventListener('click', loadHistory);
    document.addEventListener('cap:viewchange', () => {
        if (location.hash === '#training' && window.capAccess.signedIn) { renderActive(); loadHistory(); }
    });
    showCurrentView(false);
})();
