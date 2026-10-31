// Coach preview: roster from Sporters; measurements saved in this browser per athlete.
document.addEventListener('DOMContentLoaded', () => {
    if(window.capReal && !window.capCan('CLIENT_HEALTH'))return;
    const real=window.capReal;
    const $ = id => document.getElementById(id);
    const node = (tag, text, className) => {
        const item = document.createElement(tag);
        if (text != null) item.textContent = text;
        if (className) item.className = className;
        return item;
    };
    const format = value => new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(value);
    const dateLabel = value => value?window.capDateTime.dateTime(value):'Uit het profiel · datum onbekend';
    const isOpen = () => window.capAccess.signedIn && previewRole === 'coach' && location.hash === '#coach-health';
    const metrics = window.capBodyMetrics;
    const primaryKeys = ['weightKg', 'bodyFatPercent', 'muscleKg', 'waterPercent', 'boneKg', 'fatFreeKg'];
    const roster = [];
    if(real)roster.push(...real.athletes.map(person=>({...person,membership:'Account'})));
    else document.querySelectorAll('#athletes .membership-group').forEach(group => {
        const membership = group.querySelector('summary').firstChild.textContent.trim();
        group.querySelectorAll('.membership-athlete, .athlete-card').forEach(item => {
            const name = item.classList.contains('athlete-card') ? item.querySelector('h3').textContent.trim() : item.querySelectorAll('span')[1].textContent.trim();
            roster.push({ id: item.dataset.athleteId || (name === 'Guillaume' ? 'preview-guillaume' : `example-${name.toLocaleLowerCase('nl-BE').replaceAll(' ', '-')}`), name, membership });
        });
    });
    roster.sort((a, b) => a.name.localeCompare(b.name, 'nl-BE'));
    const measurementsByAthlete = real?null:window.capStorage.map('coach-measurements');
    let realEntries=[],realProfile={},measurementGeneration=0,saving=false;
    let selectedId = roster.find(athlete => athlete.id === 'preview-guillaume')?.id || roster[0]?.id;
    let request = null;
    const selected = () => roster.find(athlete => athlete.id === selectedId);
    const dialog = $('coach-measurement-dialog');
    const form = $('coach-measurement-form');

    // Read Guillaume's existing athlete preview rather than creating a second copy.
    function measurements() {
        if(real)return realEntries;
        if (selectedId !== 'preview-guillaume') return measurementsByAthlete.get(selectedId) || [];
        const entries = window.capProfile.measurements();
        window.capProfile.weights().forEach(weight => {
            const alreadyIncluded = entries.some(entry => entry.weightKg === weight.kg && Math.abs(Date.parse(entry.recordedAt) - Date.parse(weight.recordedAt)) < 1000);
            if (!alreadyIncluded) entries.push({ recordedAt: weight.recordedAt, weightKg: weight.kg });
        });
        return entries.sort((a, b) => Date.parse(b.recordedAt) - Date.parse(a.recordedAt));
    }
    function renderPicker() {
        const query = $('coach-health-search').value.trim().toLocaleLowerCase('nl-BE');
        const matches = roster.filter(athlete => athlete.name.toLocaleLowerCase('nl-BE').includes(query));
        const list = $('coach-health-athletes'); list.replaceChildren();
        matches.forEach(athlete => {
            const button = node('button', null, 'coach-athlete-option'); button.type = 'button';
            button.setAttribute('aria-pressed', String(athlete.id === selectedId));
            const avatar = node('span', athlete.name.charAt(0), 'membership-avatar'); avatar.setAttribute('aria-hidden', 'true');
            const label = node('span'); label.append(node('strong', athlete.name), node('small', athlete.membership));
            button.append(avatar, label);
            button.addEventListener('click', () => {
                selectedId = athlete.id;
                form.reset(); $('coach-measurement-status').textContent = '';
                renderPicker(); if(real)loadMeasurements();else renderMeasurements(); loadTraining();
                $('coach-health-name').focus();
            });
            list.append(button);
        });
        $('coach-health-search-count').textContent = `${matches.length} van ${roster.length} sporters`;
        if (!matches.length) list.append(node('p', 'Geen sporter gevonden. Probeer een andere naam.', 'sample-notice'));
    }
    function appendMetric(container, label, value, note, key) {
        const group = node('div'); if (key) group.dataset.metric = key;
        group.append(node('dt', label), node('dd', value));
        if (note) group.append(node('small', note));
        container.append(group);
    }
    function renderMeasurements() {
        const athlete = selected();
        if (!athlete) {
            $('coach-health-name').textContent = 'Geen sporter geselecteerd';
            $('coach-health-membership').textContent = '';
            $('coach-health-goal').textContent = '';
            ['coach-health-metrics', 'coach-health-extra', 'coach-health-history'].forEach(id => $(id).replaceChildren());
            return;
        }
        $('coach-health-name').textContent = athlete.name;
        $('coach-health-membership').textContent = real?'Gekoppeld sportersaccount':`${athlete.membership} · Voorbeeldsporter`;
        const profile = real?realProfile:selectedId === 'preview-guillaume' ? window.capProfile.get() : {};
        $('coach-health-goal').textContent = real?'Profielmetingen en bewaarde meetgeschiedenis.':profile.goal ? `Doel: ${profile.goal}` : 'Nog geen doel ingevuld.';
        const entries = measurements();
        $('coach-health-metrics').replaceChildren(); $('coach-health-extra').replaceChildren();
        metrics.forEach(metric => {
            const available = entries.filter(entry => entry[metric.key] != null);
            const latest = available[0];
            const value = latest?.[metric.key] ?? (profile[metric.key]!=null ? Number(profile[metric.key]) : null);
            let note = latest ? dateLabel(latest.recordedAt) : value != null ? 'Uit het profiel' : 'Nog niet gemeten';
            if (available.length > 1) {
                const difference = latest[metric.key] - available[1][metric.key];
                note += ` · ${difference > 0 ? '+' : ''}${format(difference)} ${metric.unit} t.o.v. vorige`;
            }
            const container = $(primaryKeys.includes(metric.key) ? 'coach-health-metrics' : 'coach-health-extra');
            appendMetric(container, metric.label, value != null ? `${format(value)} ${metric.unit}` : '—', note, metric.key);
        });
        const history = $('coach-health-history'); history.replaceChildren();
        if (!entries.length) history.append(node('p', 'Nog geen metingen. Voeg de eerste meting toe om veranderingen te volgen.', 'sample-notice'));
        entries.forEach(entry => {
            const row = node('details', null, 'saved-workout'); row.append(node('summary', dateLabel(entry.recordedAt)));
            const values = node('dl', null, 'body-metric-values');
            metrics.filter(metric => entry[metric.key] != null).forEach(metric => appendMetric(values, metric.label, `${format(entry[metric.key])} ${metric.unit}`));
            row.append(values); history.append(row);
        });
    }
    function clearTraining() {
        ['coach-health-training-stats', 'coach-health-records', 'coach-health-training-history'].forEach(id => $(id).replaceChildren());
    }
    const measurementStatus=document.createElement('p');measurementStatus.setAttribute('role','status');
    if(real)$('coach-health-metrics').before(measurementStatus);
    function applyMeasurements(snapshot){realProfile=snapshot.profile;realEntries=snapshot.measurements.map(entry=>({...entry.values,recordedAt:entry.recordedAt}));renderMeasurements();}
    async function loadMeasurements(){
        const version=++measurementGeneration,id=selectedId;realProfile={};realEntries=[];renderMeasurements();
        if(!id){measurementStatus.textContent='Er zijn nog geen sporters.';return;}
        measurementStatus.textContent='Metingen laden…';
        try{const value=await real.request('/api/measurements/athletes/'+encodeURIComponent(id));
            if(version!==measurementGeneration || selectedId!==id || !isOpen())return;
            applyMeasurements(value);measurementStatus.textContent='Metingen uit het sportersaccount.';
        }catch(error){if(version===measurementGeneration)measurementStatus.textContent=error.message+' Kies Vernieuwen om opnieuw te proberen.';}
    }
    async function loadTraining() {
        request?.abort(); request = null;
        clearTraining();
        const athlete = selected(); if (!athlete || !isOpen()) return;
        const connected = Boolean(window.capReal) || athlete.id === 'preview-guillaume';
        $('coach-health-reload').hidden = !connected;
        if (!connected) {
            $('coach-health-training-status').textContent = 'Nog geen trainingsgegevens gekoppeld voor deze voorbeeldsporter.';
            $('coach-health-records').append(node('p', 'Nog geen records.'));
            $('coach-health-training-history').append(node('p', 'Nog geen afgeronde trainingen.'));
            return;
        }
        const controller = new AbortController(); request = controller;
        $('coach-health-training-status').textContent = 'Trainingsgegevens laden…';
        const timeout = setTimeout(() => controller.abort(), 6000);
        try {
            const responses = await Promise.all(['/api/training', '/api/records'].map(path => fetch(`http://localhost:8080${path}${window.capReal ? "?athleteId=" + encodeURIComponent(athlete.id) : ""}`, { signal: controller.signal, cache: 'no-store' })));
            if (responses.some(response => !response.ok)) throw new Error('Load failed');
            const [history, records] = await Promise.all(responses.map(response => response.json()));
            if (!Array.isArray(history) || !Array.isArray(records)) throw new Error('Invalid data');
            // A late response must never appear under a different athlete's name.
            if (request !== controller || selectedId !== athlete.id || !isOpen()) return;
            const sessions = history.filter(session => session.athleteId === athlete.id).sort((a, b) => Date.parse(b.finishedAt) - Date.parse(a.finishedAt));
            const stats = $('coach-health-training-stats');
            appendMetric(stats, 'Afgeronde trainingen', String(sessions.length));
            appendMetric(stats, 'Persoonlijke records', String(records.length));
            appendMetric(stats, 'Laatste training', sessions.length ? dateLabel(sessions[0].finishedAt) : 'Nog geen');
            records.forEach(record => $('coach-health-records').append(node('p', `${record.exercise} · ${record.reps} herhalingen · ${format(record.kg)} kg`)));
            if (!records.length) $('coach-health-records').append(node('p', 'Nog geen records.'));
            sessions.forEach(session => {
                const row = node('details', null, 'saved-workout');
                row.append(node('summary', `${session.planned.name} · ${dateLabel(session.finishedAt)}`));
                row.append(node('p', `${Math.round(session.durationSeconds / 60)} minuten · ${session.exercises.length} oefeningen uitgevoerd`));
                session.exercises.forEach(result => {
                    const exercise = session.planned.exercises[result.exerciseIndex];
                    const sets = result.sets.map(set => `${set.reps} × ${set.kg == null ? '—' : format(set.kg)} kg`).join(' · ');
                    row.append(node('p', `${exercise?.name || 'Oefening'}: ${sets}`));
                });
                $('coach-health-training-history').append(row);
            });
            if (!sessions.length) $('coach-health-training-history').append(node('p', 'Nog geen afgeronde trainingen.'));
            $('coach-health-training-status').textContent = 'Opgeslagen trainingen en records van de geselecteerde sporter.';
        } catch {
            if (request === controller && selectedId === athlete.id && isOpen()) {
                clearTraining();
                $('coach-health-training-status').textContent = 'Trainingsgegevens niet geladen. Controleer de backend en kies Vernieuwen.';
            }
        } finally { clearTimeout(timeout); if (request === controller) request = null; }
    }
    metrics.forEach(metric => {
        const label = node('label', `${metric.label} (${metric.unit})`);
        const input = document.createElement('input'); input.type = 'number'; input.name = metric.key;
        input.min = metric.key === 'weightKg' ? '20' : metric.key === 'heightCm' ? '50' : '0'; input.max = String(metric.max);
        input.step = ['bmr', 'metabolicAge'].includes(metric.key) ? '1' : '0.1'; input.inputMode = 'decimal'; input.placeholder = 'Optioneel';
        label.append(input); $(primaryKeys.includes(metric.key) ? 'coach-measurement-primary' : 'coach-measurement-extra').append(label);
    });
    $('coach-health-add').addEventListener('click', () => {
        if (!isOpen() || !selected()) return;
        form.reset(); $('coach-measurement-status').textContent = '';
        $('coach-measurement-athlete').textContent = `Voor ${selected().name}`;
        dialog.showModal();
    });
    $('coach-measurement-close').addEventListener('click', () => {if(!saving)dialog.close();});
    dialog.addEventListener('cancel',event=>{if(saving)event.preventDefault();});
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!isOpen() || !selected() || !form.reportValidity()) return;
        if(saving)return;saving=true;const button=form.querySelector('[type="submit"]');button.disabled=true;const targetId=selectedId;
        try {
            const values = Object.fromEntries(new FormData(form));
            if(real){
                const input=Object.fromEntries(Object.entries(values).filter(([,value])=>String(value).trim()!=='').map(([key,value])=>[key,Number(value)]));
                const result=await real.request('/api/measurements/athletes/'+encodeURIComponent(targetId),'POST',input);
                if(selectedId===targetId && isOpen())applyMeasurements(result);
            }
            else if (selectedId === 'preview-guillaume') await window.capProfile.logMeasurement(values);
            else {
                const entry = { recordedAt: new Date().toISOString() };
                metrics.forEach(metric => {
                    if (!String(values[metric.key] ?? '').trim()) return;
                    const value = Number(values[metric.key]);
                    if (!Number.isFinite(value) || value < (metric.key === 'weightKg' ? 20 : metric.key === 'heightCm' ? 50 : 0) || value > metric.max) throw new Error(`Controleer ${metric.label.toLowerCase()}.`);
                    entry[metric.key] = value;
                });
                if (Object.keys(entry).length === 1) throw new Error('Vul minstens één meetwaarde in.');
                measurementsByAthlete.set(selectedId, [entry, ...(measurementsByAthlete.get(selectedId) || [])]);
            }
            dialog.close(); renderMeasurements(); $('coach-health-add').focus();
        } catch (error) { $('coach-measurement-status').textContent = error.message; }finally{saving=false;button.disabled=false;}
    });
    $('coach-health-search').addEventListener('input', renderPicker);
    $('coach-health-change').addEventListener('click', () => { $('coach-health-search').value = ''; renderPicker(); $('coach-health-search').focus(); });
    $('coach-health-reload').addEventListener('click', ()=>{loadTraining();if(real)loadMeasurements();});
    document.addEventListener('cap:profile-changed', () => { if (isOpen() && selectedId === 'preview-guillaume') renderMeasurements(); });
    document.addEventListener('cap:select-athlete',event=>{if(!roster.some(a=>a.id===event.detail.id)||saving)return;selectedId=event.detail.id;form.reset();$('coach-health-search').value='';renderPicker();if(isOpen()){loadMeasurements();loadTraining();}});
    document.addEventListener('cap:viewchange', () => {
        if (!isOpen()) { ++measurementGeneration;request?.abort(); request = null; if (dialog.open) dialog.close(); }
        if (!window.capAccess.signedIn) {
            selectedId = roster.find(athlete => athlete.id === 'preview-guillaume')?.id || roster[0]?.id;
            $('coach-health-search').value = ''; form.reset(); $('coach-measurement-status').textContent = '';
            renderPicker(); renderMeasurements(); clearTraining(); $('coach-health-training-status').textContent = '';
        }
        if (isOpen()) { renderPicker(); if(real)loadMeasurements();else renderMeasurements(); loadTraining(); }
    });
    renderPicker(); renderMeasurements();
    if(real){
        $('coach-health').querySelector(':scope > .sample-notice').textContent='Metingen worden gedeeld via het sportersaccount. Profielwijzigingen verschijnen hier automatisch.';
        document.addEventListener('cap:athletes-changed',()=>{roster.splice(0,roster.length,...real.athletes.map(person=>({...person,membership:'Account'})));if(!roster.some(person=>person.id===selectedId))selectedId=roster[0]?.id;renderPicker();if(isOpen())loadMeasurements();});
        window.addEventListener('focus',()=>{if(isOpen()&&!saving)loadMeasurements();});
    }
    if (isOpen()) {loadTraining();if(real)loadMeasurements();}
});
