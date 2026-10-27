(() => {
    const api = window.capAccount,
        $ = (id) => document.getElementById(id);
    let owner = null,
        items = [],
        limit = 24,
        generation = 0;
    const node = (tag, text) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        return item;
    };
    $('real-exercises').innerHTML =
        `<h2>Oefeningen</h2><p>Beheer de gedeelde oefenbibliotheek. Archiveren verbergt een oefening bij nieuwe keuzes; bestaande workouts blijven behouden.</p>
        <label>Zoek oefening<input id="real-exercise-search" type="search" placeholder="Naam of lichaamsregio"></label><label class="real-inline-check"><input id="real-exercise-archived" type="checkbox">Ook gearchiveerde oefeningen</label><p id="real-exercise-count" role="status"></p><div id="real-exercise-results"></div><button id="real-exercise-more" type="button" class="secondary-button">Meer tonen</button>
        <h3 id="real-exercise-form-title">Oefening toevoegen</h3><form id="real-exercise-form" class="club-form"><input name="id" type="hidden"><label>Naam<input name="name" required maxlength="100"></label><div class="club-form-grid"><label>Lichaamsregio<input name="region" maxlength="100"></label><label>Categorie<input name="category" maxlength="100"></label><label>Subcategorie<input name="subcategory" maxlength="100"></label></div><label>Uitvoering<textarea name="instructions" rows="3" maxlength="3000"></textarea></label><label>Notities<textarea name="notes" rows="2" maxlength="3000"></textarea></label><button type="submit" class="primary-button">Oefening bewaren</button><button id="real-exercise-new" type="button" class="secondary-button">Nieuwe oefening</button></form>`;
    const form = $('real-exercise-form');
    function route() {
        const allowed = owner?.role === 'COACH';
        $('real-exercises-link').hidden = !allowed;
        $('real-exercises').hidden = !allowed || location.hash !== '#exercises';
    }
    function render() {
        const query = $('real-exercise-search').value.trim().toLocaleLowerCase('nl-BE'),
            archived = $('real-exercise-archived').checked;
        const filtered = items.filter(
            (item) =>
                (archived || !item.archived) &&
                [item.name, item.region, item.category, item.subcategory]
                    .join(' ')
                    .toLocaleLowerCase('nl-BE')
                    .includes(query),
        );
        $('real-exercise-count').textContent = `${filtered.length} oefeningen`;
        const list = $('real-exercise-results');
        list.replaceChildren();
        filtered.slice(0, limit).forEach((item) => {
            const row = node('div');
            row.className = 'real-exercise-library-row';
            row.append(
                node('strong', item.name + (item.archived ? ' · Gearchiveerd' : '')),
                node('span', [item.region, item.category].filter(Boolean).join(' · ')),
            );
            const edit = node('button', 'Bewerken');
            edit.type = 'button';
            edit.className = 'secondary-button';
            edit.addEventListener('click', () => {
                form.reset();
                Object.entries(item).forEach(([key, value]) => {
                    if (form.elements.namedItem(key))
                        form.elements.namedItem(key).value = value ?? '';
                });
                $('real-exercise-form-title').textContent = 'Oefening bewerken';
                form.elements.name.focus();
            });
            const archive = node('button', item.archived ? 'Herstellen' : 'Archiveren');
            archive.type = 'button';
            archive.className = 'secondary-button';
            archive.addEventListener('click', () =>
                api.run(async () => {
                    const csrf = await api.request('/csrf');
                    await api.request('/api/exercises/' + item.id + '/archive', {
                        method: 'PATCH',
                        headers: {
                            [csrf.headerName]: csrf.token,
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify({ archived: !item.archived }),
                    });
                    await refresh();
                    document.dispatchEvent(new CustomEvent('cap:catalog-updated'));
                    api.message('Oefenbibliotheek bijgewerkt.');
                }),
            );
            row.append(edit, archive);
            list.append(row);
        });
        $('real-exercise-more').hidden = filtered.length <= limit;
    }
    async function refresh() {
        const ticket = ++generation,
            accountId = owner?.id;
        const data = await api.request('/api/exercises');
        if (ticket !== generation || accountId !== owner?.id) return;
        items = data;
        render();
    }
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        api.run(async () => {
            const values = Object.fromEntries(new FormData(form)),
                id = values.id;
            delete values.id;
            const csrf = await api.request('/csrf');
            await api.request('/api/exercises' + (id ? '/' + id : ''), {
                method: id ? 'PUT' : 'POST',
                headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json' },
                body: JSON.stringify(values),
            });
            form.reset();
            $('real-exercise-form-title').textContent = 'Oefening toevoegen';
            await refresh();
            document.dispatchEvent(new CustomEvent('cap:catalog-updated'));
            api.message('Oefening bewaard.');
        });
    });
    $('real-exercise-new').addEventListener('click', () => {
        form.reset();
        $('real-exercise-form-title').textContent = 'Oefening toevoegen';
    });
    $('real-exercise-search').addEventListener('input', () => {
        limit = 24;
        render();
    });
    $('real-exercise-archived').addEventListener('change', render);
    $('real-exercise-more').addEventListener('click', () => {
        limit += 24;
        render();
    });
    window.addEventListener('hashchange', route);
    document.addEventListener('cap:account-ready', (event) => {
        owner = event.detail;
        generation++;
        items = [];
        form.reset();
        $('real-exercise-results').replaceChildren();
        route();
        if (owner?.role === 'COACH') refresh().catch((error) => api.message(error.message));
    });
})();
