// One next action on athlete Home, using the existing booking and training APIs.
(() => {
    const node = (tag, text, className) => {
        const value = document.createElement(tag);
        if (text) value.textContent = text;
        if (className) value.className = className;
        return value;
    };
    const card = node('section', null, 'home-next-training');
    card.id = 'home-next-training'; card.setAttribute('aria-labelledby', 'home-next-training-title');
    const heading = node('h3', 'Mijn volgende training'); heading.id = 'home-next-training-title';
    const body = node('div', null, 'home-next-training-body');
    card.append(heading, body);
    document.querySelector('#dashboard > header').after(card);
    const viewer = node('dialog', null, 'club-dialog');
    viewer.id = 'home-workout-dialog'; viewer.setAttribute('aria-labelledby', 'home-workout-title');
    viewer.innerHTML = '<div class="session-dialog-header"><h2 id="home-workout-title"></h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div><p></p><div class="home-workout-content"></div>';
    document.body.append(viewer);
    viewer.querySelector('button').addEventListener('click', () => viewer.close());
    let state = 'loading', completed = new Set(), generation = 0, selectedId = null, errorMessage = '';
    const athlete = () => window.capAccess.signedIn && previewRole === 'athlete';
    const visible = () => athlete() && ['', '#dashboard'].includes(location.hash);
    const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const next = () => window.capClub.athleteSessions().find(session => !completed.has('session:' + session.id));
    const button = (text, action, primary = false) => {
        const value = node('button', text, primary ? 'primary-button' : 'secondary-button');
        value.type = 'button'; value.addEventListener('click', action); return value;
    };
    function planningLink() { const link = node('a', 'Bekijk planning', 'secondary-button'); link.href = '#planning'; return link; }
    function closeStaleViewer() {
        if (viewer.open && (!athlete() || completed.has('session:' + selectedId)
                || !window.capClub.athleteSessions().some(session => session.id === selectedId))) viewer.close();
    }
    function render() {
        card.hidden = !athlete();
        if (!athlete()) { closeStaleViewer(); return; }
        body.replaceChildren(); card.setAttribute('aria-busy', String(state === 'loading'));
        if (state === 'loading') { const text = node('p', 'Je volgende training laden…'); text.setAttribute('role', 'status'); body.append(text); return; }
        if (state === 'error') {
            const text = node('p', errorMessage || 'Je volgende training kon niet worden geladen. Probeer opnieuw.'); text.setAttribute('role', 'status');
            body.append(text, button('Opnieuw laden', refresh)); return;
        }
        const session = next();
        if (!session) {
            body.append(node('p', 'Nog geen volgende training gepland.'), node('p', 'Bekijk het rooster en reserveer een plek.', 'sample-notice'));
            const actions = node('div', null, 'home-next-training-actions');
            if (window.capTraining.hasActive) actions.append(button('Verder trainen', () => { location.hash = 'training'; }, true));
            actions.append(planningLink()); body.append(actions); return;
        }
        const details = node('div', null, 'home-next-training-details');
        details.append(node('h4', session.title), node('p', `${window.capDateTime.dayLabel(session.date)} · ${session.start}–${session.end}`, 'home-next-training-time'));
        const meta = node('dl');
        for (const [label, value] of [['Coach', session.coach], ['Locatie', session.location]]) {
            if (value) { const row = node('div'); row.append(node('dt', label), node('dd', value)); meta.append(row); }
        }
        details.append(meta);
        const actions = node('div', null, 'home-next-training-actions');
        if (session.workoutSnapshot) {
            details.append(node('p', session.workoutSnapshot.name, 'home-next-training-workout'));
            actions.append(button('Bekijk workout', () => viewWorkout(session.id)));
            if (!window.capTraining.hasActive && session.date === today()) {
                actions.append(button('Training starten', () => startTraining(session.id), true));
            } else if (!window.capTraining.hasActive) {
                details.append(node('p', 'Je kunt deze training op de dag van de les starten.', 'sample-notice'));
            }
        } else {
            details.append(node('p', session.type === 'Open gym'
                ? 'Vrij trainen: kies je persoonlijke workout bij Workouts.'
                : 'Je coach heeft nog geen workout toegevoegd.', 'sample-notice'));
            if (session.type === 'Open gym') { const link = node('a', 'Bekijk mijn workouts', 'secondary-button'); link.href = '#workouts'; actions.append(link); }
        }
        if (window.capTraining.hasActive) {
            details.append(node('p', 'Er staat nog een training open in je trainingslog.', 'sample-notice'));
            actions.append(button('Verder trainen', () => { location.hash = 'training'; }, true));
        }
        actions.append(planningLink()); body.append(details, actions);
    }
    async function refresh() {
        if (!visible()) return false;
        const version = ++generation;
        state = 'loading'; errorMessage = ''; render();
        try {
            const [, response] = await Promise.all([
                window.capClub.refreshSessions(),
                fetch('http://localhost:8080/api/training', { cache: 'no-store', signal: AbortSignal.timeout(10000) })
            ]);
            if (!response.ok) throw new Error('Geschiedenis niet beschikbaar.');
            const history = await response.json();
            if (!Array.isArray(history)) throw new Error('Ongeldige geschiedenis.');
            if (version !== generation || !visible()) return false;
            completed = new Set(history.map(entry => entry.assignmentId).filter(Boolean));
            state = 'ready'; closeStaleViewer(); render(); return true;
        } catch {
            if (version !== generation || !visible()) return false;
            state = 'error'; closeStaleViewer(); render(); return false;
        }
    }
    async function currentSession(id) {
        if (!await refresh()) return null;
        const session = window.capClub.athleteSessions().find(item => item.id === id);
        if (!session || completed.has('session:' + id)) {
            errorMessage = 'Deze training is niet meer beschikbaar. Vernieuw je planning.';
            state = 'error'; viewer.close(); render(); return null;
        }
        return session;
    }
    async function viewWorkout(id) {
        const session = await currentSession(id);
        if (!session?.workoutSnapshot) return;
        selectedId = id;
        viewer.querySelector('h2').textContent = session.workoutSnapshot.name;
        viewer.querySelector('p').textContent = `${session.title} · ${window.capDateTime.date(session.date)} · ${session.start}`;
        const content = viewer.querySelector('.home-workout-content');
        content.replaceChildren();
        if (session.workoutSnapshot.description) content.append(node('p', session.workoutSnapshot.description));
        content.append(window.capWorkoutView.render(session.workoutSnapshot.exercises));
        viewer.showModal();
    }
    async function startTraining(id) {
        const session = await currentSession(id);
        if (!session?.workoutSnapshot || session.date !== today()) return;
        await window.capTraining.start({ ...session.workoutSnapshot, assignmentId: 'session:' + session.id });
    }
    document.addEventListener('cap:viewchange', () => {
        if (visible()) refresh();
        else { ++generation; if (viewer.open) viewer.close(); render(); }
    });
    document.addEventListener('cap:club-changed', () => { closeStaleViewer(); if (visible() && state !== 'loading') render(); });
    for (const name of ['cap:booking-changed', 'cap:training-changed']) document.addEventListener(name, () => { if (visible()) refresh(); });
    window.addEventListener('focus', () => { if (visible()) refresh(); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden && visible()) refresh(); });
    viewer.addEventListener('close', () => { if (visible()) card.querySelector('button')?.focus(); });
    render(); if (visible()) refresh();
})();
