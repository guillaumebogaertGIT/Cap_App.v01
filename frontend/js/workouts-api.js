(() => {
    const $ = (selector) => document.querySelector(selector);
    let loading = false;
    let pending = false;
    const node = (tag, text) => { const item = document.createElement(tag); item.textContent = text; return item; };
    function details(workout) {
        const content = document.createElement('div');
        content.append(node('h4', workout.name), node('p', workout.description));
        const exercises = document.createElement('ol');
        workout.exercises.forEach(e => exercises.append(node('li', `${e.name} — ${e.sets} sets × ${e.reps} herhalingen${e.targetKg != null ? ` · ${e.targetKg} kg` : ''}${e.loadMode === 'percent' ? ` (${e.loadValue}% van ${e.referenceKg} kg rep-PR)` : ''}`)));
        content.append(exercises);
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
            const response = await fetch('http://localhost:8080/api/workouts', { signal: controller.signal, cache: 'no-store' });
            if (!response.ok) throw new Error('Load failed');
            const workouts = await response.json();
            if (!Array.isArray(workouts) || !workouts.every(w => typeof w.name === 'string' && Array.isArray(w.exercises))) throw new Error('Invalid data');
            $('#api-workouts').replaceChildren();
            $('#coach-workout-library').replaceChildren();
            workouts.forEach(workout => {
                const card = document.createElement('article'); card.className = 'workout-option';
                card.append(details(workout));
                const select = node('button', 'Kies deze training'); select.type = 'button'; select.className = 'primary-button';
                select.addEventListener('click', () => {
                    $('#selected-workout-title').textContent = workout.name;
                    $('#selected-workout-description').textContent = workout.description;
                    let content = $('#selected-workout-exercises');
                    if (!content) { content = document.createElement('div'); content.id = 'selected-workout-exercises'; $('#selected-workout-description').after(content); }
                    content.replaceChildren(details(workout).querySelector('ol'));
                    $('#self-selected-workout').hidden = false;
                    window.location.hash = 'dashboard';
                });
                card.append(select); $('#api-workouts').append(card);
                const start = node('button', 'Start training'); start.type = 'button'; start.className = 'secondary-button';
                start.addEventListener('click', () => window.capTraining.start(workout)); card.append(start);
                const entry = document.createElement('details'); entry.className = 'saved-workout';
                entry.append(node('summary', workout.name), details(workout));
                if (workout.athleteId) entry.append(node('p', `Toegewezen aan Guillaume · ${workout.assignedDate}`));
                $('#coach-workout-library').prepend(entry);
            });
            const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
            const assigned = workouts.find(w => w.athleteId === 'preview-guillaume' && w.assignedDate === today);
            $('#assigned-workout').hidden = !assigned;
            if (assigned) {
                $('#assigned-workout-content').replaceChildren(details(assigned));
                const start = node('button', 'Start training'); start.type = 'button'; start.className = 'primary-button';
                start.addEventListener('click', () => window.capTraining.start(assigned)); $('#assigned-workout-content').append(start);
                $('#self-selected-workout').hidden = true;
            } else { $('#self-selected-workout').hidden = false; }
            $('#athlete-assignment-status').textContent = assigned ? `Vandaag: ${assigned.name}` : 'Geen workout toegewezen voor vandaag.';
            $('#athlete-list-assignment').textContent = assigned ? assigned.name : 'Geen workout toegewezen voor vandaag.';
            $('#coach-library-empty').hidden = workouts.length > 0;
            $('#workouts-status').textContent = workouts.length ? `${workouts.length} workouts uit Java geladen.` : 'Nog geen workouts. Maak er één in de coachweergave.';
            $('#coach-library-load-status').textContent = '';
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
    const retry = node('button', 'Bibliotheek vernieuwen'); retry.type = 'button'; retry.className = 'secondary-button';
    retry.addEventListener('click', loadWorkouts);
    $('#coach-workout-library').before(status, retry);
    $('#reload-workouts').addEventListener('click', loadWorkouts);
    document.addEventListener('cap:workouts-saved', loadWorkouts);
    document.addEventListener('cap:viewchange', () => {
        if (window.capAccess.signedIn && ['dashboard', 'workouts', 'athletes', ''].includes(location.hash.slice(1))) loadWorkouts();
    });
})();
