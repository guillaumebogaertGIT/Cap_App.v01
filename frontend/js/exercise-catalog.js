// One session cache shared by the manager and picker; searching never calls the API.
(() => {
    const url = 'http://localhost:8080/api/exercises';
    let entries = [];
    let ready = false;
    let pending = null;
    function changed() { document.dispatchEvent(new CustomEvent('cap:exercise-catalog-changed')); }
    async function request(path = '', options = {}) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 8000);
        try {
            const response = await fetch(url + path, { ...options, cache: 'no-store', signal: controller.signal });
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || 'Opslaan mislukt. Probeer opnieuw.');
            return body;
        } catch (error) {
            if (error instanceof TypeError || error.name === 'AbortError') {
                throw new Error('Geen verbinding met de oefenbibliotheek. Controleer de backend en probeer opnieuw.');
            }
            throw error;
        } finally { clearTimeout(timeout); }
    }
    async function load(force = false) {
        if (pending) return pending;
        if (ready && !force) return entries;
        pending = (async () => {
            const result = await request();
            if (!Array.isArray(result) || result.some(item => !item.id || typeof item.name !== 'string')) {
                throw new Error('De oefenbibliotheek kon niet worden gelezen. Probeer opnieuw.');
            }
            entries = result; ready = true; changed();
            return entries;
        })();
        try { return await pending; } finally { pending = null; }
    }
    async function save(path, method, body) {
        if (previewRole !== 'coach' || !window.capAccess.signedIn) throw new Error('Open de coachweergave om oefeningen te beheren.');
        await load();
        const exercise = await request(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        const index = entries.findIndex(item => item.id === exercise.id);
        if (index < 0) entries.push(exercise); else entries[index] = exercise;
        changed();
        return exercise;
    }
    window.capExerciseCatalog = {
        load, get ready() { return ready; }, get entries() { return entries; },
        create: input => save('', 'POST', input),
        update: (id, input) => save('/' + encodeURIComponent(id), 'PUT', input),
        archive: (id, archived) => save('/' + encodeURIComponent(id) + '/archive', 'PATCH', { archived })
    };
})();
