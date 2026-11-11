// Account drafts sync to CAP, with a per-account local recovery copy.
(() => {
    const key = window.capReal?'cap-active-training-v2:'+window.capReal.account.id:'cap-active-training-v1';const state=window.capTrainingState;let recovery=null,finishedRecovery=null,syncText='',historyCache=[],editSequence=0;
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
    let exerciseOpened = matchMedia("(min-width: 1000px)").matches;
    try {
        const draft = window.capReal ? window.capReal.draft : JSON.parse(localStorage.getItem(key));
        if (draft && draft.id && draft.planned?.exercises?.length && Array.isArray(draft.exercises)
            && Number.isFinite(Date.parse(draft.startedAt))) active = state.normalize(draft);if(window.capReal){const local=JSON.parse(localStorage.getItem(key));if(!active&&local?.completionAttempted)finishedRecovery=local;const result=state.reconcile(active,local);active=result.draft;recovery=result.recovery;if(active?.localPending)syncText='Lokale invoer hersteld; nog niet op CAP bevestigd. Gebruik Opnieuw opslaan.';}
    } catch { /* An unreadable draft should not prevent the app from loading. */ }
    const section = node('section'); section.id = 'training'; section.hidden = true; section.dataset.view = '';
    section.setAttribute('aria-labelledby', 'training-title');
    section.innerHTML = `
        <header class="dashboard-header"><div><h2 id="training-title" tabindex="-1">Mijn training</h2><p class="dashboard-intro">Volg je workout en registreer wat je uitvoert.</p></div></header>
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
    function progressPayload(draft) {return state.payload(draft);}
    function syncStatus(text){syncText=text;if($('training-sync'))$('training-sync').textContent=text;if($('training-retry-inline'))$('training-retry-inline').hidden=!active?.localPending;}
    function mirror(){try{if(active)localStorage.setItem(key,JSON.stringify(active));else localStorage.removeItem(key);}catch{syncStatus('Lokale reservekopie niet beschikbaar. Bewaar op CAP voordat je vertrekt.');}}
    async function flushProgress(){clearTimeout(progressTimer);if(!active||!window.capReal)return;const draft=active,sequence=editSequence,exercises=progressPayload(draft),ui=structuredClone(draft.ui);syncStatus('Opslaan op CAP…');progressQueue=progressQueue.catch(()=>{}).then(async()=>{const saved=await window.capReal.request('/api/training/active','PUT',{id:draft.id,version:draft.version,exercises,ui});draft.version=saved.version;if(active?.id===draft.id){active.localPending=sequence!==editSequence;mirror();syncStatus(active.localPending?'Nieuwere invoer lokaal bewaard; opslag volgt.':'Opgeslagen op CAP.');}});return progressQueue;}
    function persist(){editSequence++;if(active)active.localPending=true;mirror();if(window.capReal){clearTimeout(progressTimer);syncStatus('Invoer lokaal bewaard; nog niet bevestigd op CAP.');if(active&&!active.finishedAt&&!recovery)progressTimer=setTimeout(()=>flushProgress().catch(error=>syncStatus(error.message+' Je invoer is alleen lokaal bewaard. Gebruik Opnieuw opslaan.')),600);}else syncStatus('Alleen in deze browser bewaard.');document.dispatchEvent(new CustomEvent('cap:training-state'));}
    document.addEventListener('visibilitychange',()=>{if(document.hidden&&active&&!active.finishedAt&&!recovery&&active.localPending){mirror();if(window.capReal)flushProgress().catch(()=>syncStatus('Nog niet op CAP bevestigd. Lokale kopie bewaard.'));}else tick();});
    window.addEventListener('pagehide',()=>{if(active||!finishedRecovery)mirror();});

        const duration = seconds => {
        const value = Math.max(0, Math.floor(seconds));
        return `${String(Math.floor(value / 3600)).padStart(2, '0')}:${String(Math.floor(value / 60) % 60).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
    };
    function tick() {
        if(active&&$('training-rest-count'))$('training-rest-count').textContent=active.ui.restPausedSeconds!=null?active.ui.restPausedSeconds+' s · Gepauzeerd':state.remaining(active.ui.restUntil)?state.remaining(active.ui.restUntil)+' s':'Rust klaar';if (active && $('training-timer')) $('training-timer').textContent = duration(((active.finishedAt ? Date.parse(active.finishedAt) : Date.now()) - Date.parse(active.startedAt)) / 1000);
    }
    setInterval(tick, 1000);
    function target(exercise) {
        if (exercise.setPlan?.length) return exercise.setPlan.map((set, i) => 'Set ' + (i + 1) + ': ' + window.capWorkoutView.targetText(set)).join(' | ');
        return `${exercise.sets} × ${exercise.reps}${exercise.targetKg != null ? ` · ${exercise.targetKg} kg` : ''}${exercise.restSeconds != null ? ` · Rust: ${exercise.restSeconds} s` : ''}${exercise.tempo ? ` · Tempo: ${exercise.tempo}` : ''}${exercise.supersetGroup ? ` · Superset ${exercise.supersetGroup}` : ''}${exercise.loadMode === 'percent' ? ` (${exercise.loadValue}% van ${exercise.referenceKg} kg bij ${exercise.reps} reps)` : ''}`;
    }
    function previousPerformance(name){const row=historyCache.slice().sort((a,b)=>Date.parse(b.finishedAt)-Date.parse(a.finishedAt)).flatMap(s=>s.exercises.map(e=>({name:s.planned.exercises[e.exerciseIndex]?.name,sets:e.sets,finishedAt:s.finishedAt}))).find(e=>e.name===name&&e.sets.length);return row?{sets:row.sets,text:'Vorige keer ('+capDateTime.date(row.finishedAt)+'): '+row.sets.map(s=>s.reps+' × '+s.kg+' kg').join(' · ')}:null;}
    function previousText(name){return previousPerformance(name)?.text||'';}
    function renderActive(){const container=$('active-training');container.replaceChildren();document.body.classList.toggle('training-focus',Boolean(active)&&location.hash==='#training'&&previewRole==='athlete');if(previewRole!=='athlete')return;if(!active){container.append(node('p',finishedRecovery?'Je vorige afronding wordt gecontroleerd op CAP.':'Geen training bezig. Open je workout om te starten.','club-empty'));return;}state.normalize(active);
        const index=active.ui.exerciseIndex,exercise=active.planned.exercises[index],performed=active.exercises[index],done=active.ui.completed.flat().filter(Boolean).length,total=active.exercises.reduce((n,e)=>n+e.sets.length,0);
        const dateNav=node('nav',null,'training-session-date');dateNav.setAttribute('aria-label','Workoutdag');const days=node('a','Trainingsdagen');days.href='#workouts';dateNav.append(days,node('span',active.planned.assignedDate?capDateTime.date(active.planned.assignedDate):'Weektraining'));container.append(dateNav);
        const head=node('div',null,'training-session-heading');head.append(node('h3',active.planned.name));const timer=node('span',null,'training-timer');timer.id='training-timer';head.append(timer);container.append(head,node('p',done+' van '+total+' sets voltooid · Oefening '+(index+1)+' van '+active.exercises.length,'training-progress'));
        const sync=node('p',syncText||(window.capReal?'Laatst geladen van CAP.':'Alleen in deze browser bewaard.'),'training-sync');sync.id='training-sync';sync.setAttribute('role','status');container.append(sync);
        const sessionOptions=node('details',null,'training-session-options');sessionOptions.append(node('summary','Workoutopties en coachopmerkingen'));
        if(window.capReal){const retry=button('Opnieuw opslaan',()=>flushProgress().catch(error=>syncStatus(error.message+' Lokale kopie behouden.')));retry.id='training-retry-inline';retry.hidden=!active.localPending;container.append(retry);}
        if(recovery){const note=node('section',null,'training-recovery');note.append(node('p','CAP heeft een andere versie. Je lokale invoer is apart bewaard. Kies welke versie je wilt gebruiken.'),button('Lokale invoer herstellen',()=>{const version=active.version;active=state.normalize({...recovery,version});recovery=null;persist();renderActive();}),button('CAP-versie gebruiken',()=>{recovery=null;mirror();syncStatus('CAP-versie geladen.');renderActive();}));container.append(note);}
        if(active.planned.description)sessionOptions.append(node('p',active.planned.description));container.append(sessionOptions);
        const form=capTrainingPlayer.render(container,{
            draft:active,opened:exerciseOpened,previous:previousPerformance(exercise.name),
            onChange:persist,onSave:()=>{if(window.capReal)flushProgress().catch(error=>syncStatus(error.message+' Alleen lokaal bewaard. Gebruik Opnieuw opslaan.'));},
            onOverview:()=>{exerciseOpened=false;renderActive();},onSelect:i=>{active.ui.exerciseIndex=i;exerciseOpened=true;persist();renderActive();$('active-exercise-title')?.focus();},redraw:renderActive,
            onRest:command=>{const ui=active.ui;if(command==='skip'){ui.restUntil=null;ui.restPausedSeconds=null;}else if(command==='add'){if(ui.restPausedSeconds!=null)ui.restPausedSeconds=Math.min(3600,ui.restPausedSeconds+15);else ui.restUntil=Date.now()+Math.min(3600,state.remaining(ui.restUntil)+15)*1000;}else if(ui.restPausedSeconds!=null){ui.restUntil=Date.now()+ui.restPausedSeconds*1000;ui.restPausedSeconds=null;}else{ui.restPausedSeconds=state.remaining(ui.restUntil);ui.restUntil=null;}persist();renderActive();}
        });

            const finish = node('button', active.finishedAt ? 'Opslaan opnieuw proberen' : 'Training afronden', 'primary-button');
        finish.type = 'submit'; finish.disabled = saving; container.append(finish);finish.onclick=()=>form.requestSubmit();
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (saving || !active || previewRole !== 'athlete' || !window.capAccess.signedIn) return;
            if (!state.finished(active).some(e => e.sets.length)) { $('training-message').textContent = 'Vul minstens één uitgevoerde set in.'; return; }
            if(!active.finishedAt&&active.ui.completed.flat().some(done=>!done)&&!confirm('Niet alle sets zijn voltooid. Alleen voltooide sets opslaan en de training afronden?'))return;active.finishedAt ||= new Date().toISOString(); persist();
            saving = true; renderActive(); tick();
            $('training-message').textContent = 'Training opslaan…';
            const timeoutController = new AbortController(); const timeout = setTimeout(() => timeoutController.abort(), 15000);
            try {
                if (window.capReal&&!active.completionAttempted) await flushProgress();active.completionAttempted=true;mirror();
                const response = await fetch('http://localhost:8080/api/training', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: timeoutController.signal,
                    body: JSON.stringify({ id: active.id, version: active.version, workoutId: active.planned.id, assignmentId: active.planned.assignmentId || null, startedAt: active.startedAt, finishedAt: active.finishedAt,
                        exercises: state.finished(active) })
                });
                const result = await response.json();
                if (!response.ok) {
                    if (response.status === 400) { active.finishedAt = null;active.completionAttempted=false;persist(); }
                    throw new Error(result.message || 'Opslaan mislukt.');
                }
                active = null; persist();
                document.dispatchEvent(new CustomEvent('cap:training-changed'));
                $('training-message').textContent = 'Training opgeslagen. Je overzicht en records zijn bijgewerkt.';
                showOverview(result); await loadHistory();
            } catch (error) {
                $('training-message').textContent = `${error.message} Je training blijft bewaard; probeer opnieuw. Dezelfde sessie wordt niet dubbel opgeslagen.`;
            } finally { clearTimeout(timeout); saving = false; renderActive(); }
        });
        tick();
        if (!saving && !active.finishedAt) {
            const cancel = button('Training verwerpen', () => {
                const confirmation = node('div', null, 'club-card');
                confirmation.append(node('p', 'Deze lopende training verwijderen zonder resultaten op te slaan?'));
                confirmation.append(button('Ja, verwerpen', async () => {
                    if (window.capReal) { try { clearTimeout(progressTimer); await progressQueue.catch(()=>{}); await window.capReal.request('/api/training/active', 'DELETE'); } catch (error) { $('training-message').textContent = error.message; return; } }
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
            const [history, records] = await Promise.all(responses.map(r => r.json()));historyCache=history;
            if(active&&!document.activeElement?.matches('#active-training input'))renderActive();
            if (!Array.isArray(history) || !Array.isArray(records)) throw new Error('Invalid data');if(finishedRecovery){const receipt=history.find(s=>s.id===finishedRecovery.id);if(receipt){showOverview(receipt);finishedRecovery=null;localStorage.removeItem(key);$('training-message').textContent='Je vorige training is op CAP opgeslagen. Het overzicht is hersteld.';}else $('training-message').textContent='De vorige afronding is nog niet op CAP gevonden. Je lokale kopie blijft bewaard.';}if(active&&$('training-previous')){const text=previousText(active.planned.exercises[active.ui.exerciseIndex].name);$('training-previous').textContent=text;$('training-previous').hidden=!text;}
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
        get hasActive() { return Boolean(active); },get current(){return active?{id:active.id,name:active.planned.name}:null;},
        isActive(workoutId) { return active?.planned.id === workoutId; },
        async start(workout, exerciseIndex) {
            if (previewRole !== 'athlete' || !window.capAccess.signedIn) return;
            if (!active && window.capReal) {
                try { active = await window.capReal.request('/api/training/active', 'POST', { assignmentId: workout.assignmentId }); $('finished-overview').hidden = true;active=state.normalize(active);if(active.version===0&&active.ui.completed.flat().every(v=>!v))active.exercises.forEach(e=>e.sets.forEach(s=>{if(s.kg==null)s.reps='';}));mirror(); }
                catch (error) { location.hash = 'training'; $('training-message').textContent = error.message; renderActive(); return; }
            } else if (!active) {
                active = { id: crypto.randomUUID(), planned: structuredClone(workout), startedAt: new Date().toISOString(), finishedAt: null,
                    exercises: workout.exercises.map((e, i) => ({ exerciseIndex: i, sets: Array.from({ length: e.sets }, () => ({ reps: '', kg: '' })) })) };
                active=state.normalize(active);persist(); $('finished-overview').hidden = true;
            } else if (active.planned.id !== workout.id || active.planned.assignmentId !== workout.assignmentId) {
                $('training-message').textContent = 'Er loopt al een training. Rond die eerst af voordat je een nieuwe start.';
            }
            if(active&&Number.isInteger(exerciseIndex)){active.ui.exerciseIndex=exerciseIndex;exerciseOpened=true;persist();}else exerciseOpened=matchMedia('(min-width:1000px)').matches;
            location.hash = 'training'; renderActive();
        }
    };
    if(window.capReal){const before=window.capBeforeSignOut,cleanup=window.capOnSignOut;window.capBeforeSignOut=()=>{if(before&&!before())return false;return !(active?.localPending||finishedRecovery)||confirm('Er is invoer die nog niet op CAP is bevestigd. Uitloggen wist de lokale kopie. Toch uitloggen?');};window.capOnSignOut=()=>{cleanup?.();clearTimeout(progressTimer);localStorage.removeItem(key);active=null;finishedRecovery=null;};}
    window.addEventListener('beforeunload',event=>{if(active?.localPending){event.preventDefault();event.returnValue='';}});
    $('reload-training').addEventListener('click', loadHistory);
    document.addEventListener('cap:viewchange', () => {
        document.body.classList.toggle('training-focus',Boolean(active)&&location.hash==='#training'&&previewRole==='athlete');if (location.hash === '#training' && window.capAccess.signedIn) { renderActive(); loadHistory(); }
    });
    showCurrentView(false);
})();
