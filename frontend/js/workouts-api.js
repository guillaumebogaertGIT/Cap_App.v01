(() => {
    if(window.capReal?.account.role==='COACH' && !window.capCan('WORKOUT_VIEW'))return;
    const $ = (selector) => document.querySelector(selector);
    let loading = false;
    const athleteId = () => window.capReal ? (previewRole === 'coach' ? window.capReal.selectedAthleteId : window.capReal.account.id) : 'preview-guillaume';
    let pending = false;
    let visibleLimit = 6;
    let coachType = 'all';
    let coachCategory = 'all';
    const libraryCategories = [['all', 'Alle workouts'], ['CAP HYROX', 'CAP HYROX'], ['CAP Lift & HIT', 'CAP Lift & HIT'],
        ['CAP Hybrid', 'CAP Hybrid'], ['CAP Move', 'CAP Move'], ['Athlete', 'Specifieke sporter'], ['Private', 'Privétraining'], ['Open gym', 'Open gym'], ['', 'Overig']];
    const library = $('#coach-workout-library');
    const categoryChooser = document.createElement('div');
    const chooserTitle = document.createElement('h3'); chooserTitle.id = 'library-category-title'; chooserTitle.tabIndex = -1;
    chooserTitle.textContent = 'Kies een categorie';
    const categoryTiles = document.createElement('div'); categoryTiles.className = 'home-tiles';
    categoryTiles.setAttribute('role', 'group'); categoryTiles.setAttribute('aria-labelledby', chooserTitle.id);
    categoryChooser.append(chooserTitle, categoryTiles);
    const libraryPanel = document.createElement('div'); libraryPanel.hidden = true;
    library.before(categoryChooser, libraryPanel);
    const toolbar = document.createElement('div'); toolbar.className = 'workout-category-toolbar';
    const selectionTitle = document.createElement('h3'); selectionTitle.tabIndex = -1;
    const back = document.createElement('button'); back.type = 'button'; back.className = 'secondary-button'; back.textContent = 'Alle categorieën';
    toolbar.append(selectionTitle, back);
    const searchLabel = document.createElement('label'); searchLabel.className = 'library-search'; searchLabel.textContent = 'Zoek een workout';
    const coachSearch = document.createElement('input'); coachSearch.type = 'search'; coachSearch.placeholder = 'Naam, beschrijving of oefening';
    searchLabel.append(coachSearch);
    libraryPanel.append(toolbar, searchLabel, library);
    const categoryCounts = new Map();
    const bodyTypeCounts = new Map();
    const bodyHeading = document.createElement('h3'); bodyHeading.textContent = 'Kies een workout type';
    const bodyTiles = document.createElement('div'); bodyTiles.className = 'home-tiles workout-type-tiles';
    chooserTitle.before(bodyHeading, bodyTiles);
    chooserTitle.textContent = 'Of kies een groep';
    [['lower-body', 'Lower body'], ['upper-body', 'Upper body'], ['full-body', 'Full body'], ['wod', 'WOD']].forEach(([type, label]) => {
        const tile = document.createElement('button'); tile.type = 'button'; tile.className = 'home-tile'; tile.dataset.workoutType = type;
        const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', 'M3 6h7l2 2h9v13H3zM3 6V3h7l2 3'); icon.append(path);
        const title = document.createElement('strong'); title.textContent = label;
        const count = document.createElement('span'); count.textContent = 'Nog niet geladen'; bodyTypeCounts.set(type, count);
        tile.append(icon, title, count);
        tile.addEventListener('click', () => {
            coachCategory = 'all'; coachType = type; coachSearch.value = '';
            categoryChooser.hidden = true; libraryPanel.hidden = false;
            filterCoachTypes(); selectionTitle.focus();
        });
        bodyTiles.append(tile);
    });
    function showLibraryCategories(moveFocus = true) {
        categoryChooser.hidden = false; libraryPanel.hidden = true;
        if (moveFocus) chooserTitle.focus();
    }
    back.addEventListener('click', () => showLibraryCategories());
    libraryCategories.forEach(([value, label]) => {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'home-tile';
        const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', value === 'all' ? 'M3 3h7v7H3zm11 0h7v7h-7zM3 14h7v7H3zm11 0h7v7h-7z' : 'M3 6h7l2 2h9v13H3zM3 6V3h7l2 3');
        icon.append(path);
        const title = document.createElement('strong'); title.textContent = label;
        const count = document.createElement('span'); count.textContent = 'Nog niet geladen'; categoryCounts.set(value, count);
        button.append(icon, title, count);
        button.addEventListener('click', () => {
            coachCategory = value; coachType = 'all'; coachSearch.value = '';
            selectionTitle.textContent = label;
            categoryChooser.hidden = true; libraryPanel.hidden = false;
            filterCoachTypes(); selectionTitle.focus();
        });
        categoryTiles.append(button);
    });
    coachSearch.addEventListener('input', filterCoachTypes);
    document.querySelectorAll('a[href="#saved-workouts"]').forEach(link => link.addEventListener('click', () => {
        quickAssign = false;
        quickAssignNotice.textContent = '';
        showLibraryCategories(false);
    }));
    function updateCategoryCounts(workouts) {
        bodyTypeCounts.forEach((element, type) => {
            const count = workouts.filter(workout => workout.workoutType === type).length;
            element.textContent = `${count} ${count === 1 ? 'workout' : 'workouts'}`;
        });
        libraryCategories.forEach(([value]) => {
            const count = value === 'all' ? workouts.length : workouts.filter(workout => (workout.category || '') === value).length;
            categoryCounts.get(value).textContent = `${count} ${count === 1 ? 'workout' : 'workouts'}`;
        });
    }
    function filterCoachTypes() {
        const typeName = { 'lower-body': 'Lower body', 'upper-body': 'Upper body', 'full-body': 'Full body', conditioning: 'Conditioning', wod: 'WOD', other: 'Overig' }[coachType];
        const categoryName = libraryCategories.find(([value]) => value === coachCategory)?.[1] || 'Alle workouts';
        selectionTitle.textContent = typeName ? `${typeName}${coachCategory === 'all' ? '' : ' · ' + categoryName}` : categoryName;
        const query = coachSearch.value.trim().toLocaleLowerCase('nl-BE');
        let total = 0;
        document.querySelectorAll('#coach-workout-library .workout-category').forEach(group => {
            const entries = Array.from(group.querySelectorAll('[data-workout-type]'));
            let count = 0;
            entries.forEach(entry => {
                entry.hidden = (coachType !== 'all' && entry.dataset.workoutType !== coachType) || !entry.dataset.search.includes(query);
                if (!entry.hidden) count++;
            });
            const selectedCategory = coachCategory === 'all' || group.dataset.categoryKey === coachCategory;
            group.hidden = count === 0 || !selectedCategory;
            if (selectedCategory) total += count;
            if (selectedCategory) group.open = true;
            group.querySelector('summary').textContent = group.dataset.category + ' (' + count + ')';
        });
        document.querySelector('#coach-type-empty').hidden = total > 0;
        document.querySelectorAll('#coach-type-filters button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.type === coachType)));
    }
    const typeFilters = document.createElement('div'); typeFilters.id = 'coach-type-filters'; typeFilters.className = 'workout-type-filters';
    typeFilters.setAttribute('role', 'group'); typeFilters.setAttribute('aria-label', 'Filter op workout type');
    [['all','Alles'],['full-body','Full body'],['lower-body','Lower body'],['upper-body','Upper body'],['conditioning','Conditioning'],['wod','WOD'],['other','Overig']].forEach(([type,label]) => {
        const button = document.createElement('button'); button.type='button'; button.className='secondary-button'; button.textContent=label; button.dataset.type=type;
        button.setAttribute('aria-pressed', String(type === coachType));
        button.addEventListener('click', () => { coachType=type; filterCoachTypes(); }); typeFilters.append(button);
    });
    const typeEmpty = document.createElement('p'); typeEmpty.id='coach-type-empty'; typeEmpty.hidden=true; typeEmpty.textContent='Nog geen workouts voor deze selectie.';
    library.before(typeFilters, typeEmpty);

    function filterLibrary() {
        const query = $('#workout-search').value.trim().toLocaleLowerCase('nl-BE');
        const cards = Array.from($('#api-workouts').children);
        const matches = cards.filter(card => card.dataset.search.includes(query));
        cards.forEach(card => { card.hidden = true; });
        matches.slice(0, visibleLimit).forEach(card => { card.hidden = false; });
        $('#show-more-workouts').hidden = matches.length <= visibleLimit;
        $('#workout-results-count').textContent = matches.length
            ? `${Math.min(visibleLimit, matches.length)} van ${matches.length} workouts`
            : 'Geen workouts gevonden.';
    }
    const node = (tag, text) => { const item = document.createElement(tag); item.textContent = text; return item; };
    const discovery=document.createElement('section');discovery.className='athlete-workout-discovery';discovery.id='athlete-workout-discovery';$('#workouts > header').after(discovery);
    let selectedWorkoutDate=null;
    let lastWorkoutRoute=location.hash;
    function discover(all,completed,today){
        discovery.hidden=previewRole!=='athlete';if(discovery.hidden)return;
        const intro=$('#workouts > header .dashboard-intro');if(intro)intro.textContent='Jouw workout voor vandaag. Open de agenda om een andere dag te bekijken.';
        selectedWorkoutDate ||= today;discovery.replaceChildren();window.capWorkoutDate=selectedWorkoutDate;
        const selected=selectedWorkoutDate,workouts=all.filter(w=>w.assignedDate===selected);
        const dateLabel=value=>new Intl.DateTimeFormat('nl-BE',{weekday:'short',day:'numeric',month:'short',year:'numeric',timeZone:'UTC'}).format(new Date(value+'T12:00:00Z'));
        function select(value){selectedWorkoutDate=value;discover(all,completed,today);}
        function svg(path){const image=document.createElementNS('http://www.w3.org/2000/svg','svg');image.setAttribute('viewBox','0 0 24 24');image.setAttribute('aria-hidden','true');const line=document.createElementNS(image.namespaceURI,'path');line.setAttribute('d',path);image.append(line);return image;}
        function control(label,path,fn){const button=node('button','');button.type='button';button.className='secondary-button workout-date-control';button.setAttribute('aria-label',label);button.append(svg(path));button.onclick=fn;return button;}
        const nav=document.createElement('nav');nav.className='workout-day-nav workout-calendar-nav';nav.setAttribute('aria-label','Workoutdatum');
        const calendar=control('Open workoutagenda','M4 5h16v16H4zM8 3v4m8-4v4M4 11h16',openCalendar);calendar.classList.add('workout-calendar-open');calendar.append(node('strong',dateLabel(selected)));
        nav.append(control('Vorige dag','m15 5-7 7 7 7',()=>select(capAgenda.add(selected,-1))),calendar,control('Volgende dag','m9 5 7 7-7 7',()=>select(capAgenda.add(selected,1))));discovery.append(nav);
        if(selected!==today){const current=node('button','Vandaag');current.type='button';current.className='secondary-button workout-today';current.onclick=()=>select(today);discovery.append(current);}
        if(!workouts.length){const empty=document.createElement('section');empty.className='workout-day-empty';empty.append(node('h3',selected===today?'Geen workouts vandaag':'Geen workouts op deze dag'),node('p','Kies een workout die je coach voor jou heeft klaargezet.'));const add=node('button','Workout toevoegen');add.type='button';add.className='primary-button';add.onclick=openAssignedPicker;empty.append(add);discovery.append(empty);}
        workouts.forEach(workout=>{
            const wrap=document.createElement('section');wrap.className='workout-day-overview';wrap.append(node('h3',workout.name));
            const status=node('p',completed.has(workout.assignmentId)?'Afgerond':capTraining.isActive(workout.id)?'Training bezig':'Klaar om te starten');status.className='workout-day-status';wrap.append(status);
            if(workout.description){const notes=document.createElement('details');notes.append(node('summary','Opmerkingen van je coach'),node('p',workout.description));wrap.append(notes);}
            const start=node('button',capTraining.hasActive?'Verder trainen':completed.has(workout.assignmentId)?'Bekijk afgeronde training':'Training starten');start.type='button';start.className='primary-button';
            function open(i){if(completed.has(workout.assignmentId)){location.hash='health';return;}if(capTraining.hasActive&&!capTraining.isActive(workout.id)){location.hash='training';return;}capTraining.start(workout,i);}
            start.onclick=()=>{if(capTraining.hasActive){location.hash='training';return;}open();};
            wrap.append(capTrainingPlayer.timeline(workout.exercises,{onSelect:open}),start);discovery.append(wrap);
        });
        if(capTraining.hasActive&&!workouts.some(w=>capTraining.isActive(w.id))){const resume=node('a','Verder met '+(capTraining.current?.name||'je training'));resume.href='#training';resume.className='secondary-button';discovery.append(resume);}
        const history=node('a','PR’s en afgeronde trainingen');history.href='#health';history.className='workout-day-history';discovery.append(history);
        document.dispatchEvent(new CustomEvent('cap:workout-day',{detail:{date:selected}}));
        function openAssignedPicker(){
            const dialog=document.createElement('dialog');dialog.className='club-dialog workout-calendar-dialog';dialog.setAttribute('aria-label','Workout toevoegen');dialog.append(node('h3','Workout toevoegen'),node('p','Voor '+dateLabel(selected)+' · gesorteerd op uiterste datum'));
            const feedback=node('p','');feedback.setAttribute('role','status');dialog.append(feedback);
            const available=all.filter(w=>w.weekStart&&!w.assignedDate&&!completed.has(w.assignmentId)).sort((a,b)=>capAgenda.add(a.weekStart,6).localeCompare(capAgenda.add(b.weekStart,6))||a.name.localeCompare(b.name));
            if(!available.length)dialog.append(node('p','Je hebt geen ongeplande workouts van je coach.'));
            available.forEach(w=>{const end=capAgenda.add(w.weekStart,6),row=document.createElement('section');row.className='workout-picker-item';row.append(node('h4',w.name),node('p','Te voltooien uiterlijk '+dateLabel(end)),node('p','Trainingsblok: '+capAgenda.range(w.weekStart)));const choose=node('button','Op deze dag plannen');choose.type='button';choose.className='secondary-button';choose.disabled=selected<w.weekStart||selected>end||capTraining.isActive(w.id);if(choose.disabled)row.append(node('p',capTraining.isActive(w.id)?'Deze training is al gestart.':'Kies een dag binnen dit trainingsblok.'));choose.onclick=async()=>{const buttons=[...dialog.querySelectorAll('button')],states=buttons.map(b=>b.disabled);buttons.forEach(b=>b.disabled=true);try{await changeAssignment(w.assignmentId,'PUT',{date:selected},'/schedule');dialog.close();await loadWorkouts();}catch(error){feedback.textContent=error.message;}finally{buttons.forEach((b,i)=>b.disabled=states[i]);}};row.append(choose);dialog.append(row);});
            const close=node('button','Sluiten');close.type='button';close.className='secondary-button';close.onclick=()=>dialog.close();dialog.append(close);dialog.addEventListener('close',()=>{dialog.remove();discovery.querySelector('.workout-day-empty button')?.focus();});document.body.append(dialog);dialog.showModal();
        }
        function openCalendar(){
            const dialog=document.createElement('dialog');dialog.className='club-dialog workout-calendar-dialog';dialog.setAttribute('aria-label','Workoutagenda');
            dialog.append(node('h3','Workoutagenda'));const form=document.createElement('form');form.className='club-form';const label=node('label','Kies een datum'),input=document.createElement('input');input.type='date';input.required=true;input.value=selected;label.append(input);const go=node('button','Bekijk dag');go.type='submit';go.className='primary-button';form.append(label,go);dialog.append(form);form.onsubmit=event=>{event.preventDefault();if(!input.value||!input.checkValidity())return;dialog.close();select(input.value);};
            const list=document.createElement('div');list.className='workout-calendar-dates';dialog.append(node('h4','Toegewezen trainingsdagen'),list);
            const dates=[...new Set(all.map(w=>w.assignedDate).filter(Boolean))].sort();
            if(!dates.length)list.append(node('p','Nog geen workouts op een datum gepland.'));
            dates.forEach(date=>{const items=all.filter(w=>w.assignedDate===date),button=node('button','');button.type='button';button.className='workout-calendar-date';button.setAttribute('aria-current',date===selected?'date':'false');button.append(node('strong',dateLabel(date)),node('span',items.map(w=>w.name+(completed.has(w.assignmentId)?' · Afgerond':'')).join(' / ')));button.onclick=()=>{dialog.close();select(date);};list.append(button);});
            const unplanned=all.filter(w=>!w.assignedDate&&w.weekStart&&!completed.has(w.assignmentId));
            if(unplanned.length){dialog.append(node('h4','Nog te plannen'));unplanned.forEach(w=>{const row=document.createElement('div');row.className='workout-calendar-unplanned';const plan=node('button','Dag plannen');plan.type='button';plan.className='secondary-button';plan.onclick=()=>{dialog.close();planDay(w);};row.append(node('p',w.name+' · '+scheduleLabel(w)),plan);dialog.append(row);});}
            const close=node('button','Sluiten');close.type='button';close.className='secondary-button';close.onclick=()=>dialog.close();dialog.append(close);dialog.addEventListener('close',()=>{dialog.remove();discovery.querySelector('.workout-calendar-open')?.focus();});document.body.append(dialog);dialog.showModal();
        }
    }
    document.addEventListener('cap:workout-bookings',event=>{if(event.detail.date!==selectedWorkoutDate)return;const empty=discovery.querySelector('.workout-day-empty');if(empty)empty.hidden=event.detail.count>0;});
    let viewedWeek = window.capAgenda.monday(window.capAgenda.today());
    const weekToolbar = node('div', ''); weekToolbar.className = 'agenda-week-toolbar';
    const previousWeek = node('button', '←'); previousWeek.type = 'button'; previousWeek.className = 'secondary-button'; previousWeek.setAttribute('aria-label', 'Vorige week');
    const nextWeek = node('button', '→'); nextWeek.type = 'button'; nextWeek.className = 'secondary-button'; nextWeek.setAttribute('aria-label', 'Volgende week');
    const weekTitle = node('strong', '');
    weekToolbar.append(previousWeek, weekTitle, nextWeek); $('#api-workouts').before(weekToolbar);
    const browseWeek = offset => { viewedWeek = window.capAgenda.add(viewedWeek, offset); $('#assigned-workout-period').value = window.capAgenda.add(viewedWeek, 6) < window.capAgenda.today() ? 'past' : 'upcoming'; loadWorkouts(); };
    previousWeek.addEventListener('click', () => browseWeek(-7)); nextWeek.addEventListener('click', () => browseWeek(7));
    const scheduleLabel = workout => workout.weekStart
        ? `Week ${window.capAgenda.range(workout.weekStart)}${workout.assignedDate ? ` · Gepland: ${window.capAgenda.label(workout.assignedDate)}` : ' · Zelf een dag kiezen'}`
        : window.capDateTime.date(workout.assignedDate);
    function planDay(workout) {
        if (!window.capAccess.signedIn || window.capTraining.isActive(workout.id)) return;
        const dialog = document.createElement('dialog'); dialog.className = 'club-dialog'; dialog.setAttribute('aria-label', 'Workout op een dag plannen');
        const form = document.createElement('form'); form.className = 'club-form';
        const label = node('label', 'Trainingsdag'); const input = document.createElement('input'); input.type = 'date'; input.required = true;
        input.value = workout.assignedDate || (workout.weekStart > window.capAgenda.today() ? workout.weekStart : window.capAgenda.today()); label.append(input);
        const title = node('h2', workout.name); form.append(title, label);
        const feedback = node('p', ''); feedback.setAttribute('role', 'status');
        const save = node('button', 'Dag bewaren'); save.type = 'submit'; save.className = 'primary-button';
        const flexible = node('button', 'Dag vrijlaten'); flexible.type = 'button'; flexible.className = 'secondary-button';
        const close = node('button', 'Sluiten'); close.type = 'button'; close.className = 'secondary-button'; close.addEventListener('click', () => dialog.close());
        form.append(feedback, save, flexible, close); dialog.append(form); document.body.append(dialog);
        window.capAgenda.mount(input, { dayOnly: true, fixedWeek: workout.weekStart });
        let saving = false;
        async function persist(date) {
            if (saving || !window.capAccess.signedIn) return;
            saving = true;
            const controls = Array.from(form.querySelectorAll('button, input'), item => [item, item.disabled]);
            controls.forEach(([item]) => { item.disabled = true; });
            try {
                await changeAssignment(workout.assignmentId, 'PUT', { date }, '/schedule');
                dialog.close(); await loadWorkouts();
            } catch (error) { feedback.textContent = error.message; }
            finally { saving = false; controls.forEach(([item, disabled]) => { item.disabled = disabled; }); }
        }
        form.addEventListener('submit', event => { event.preventDefault(); if (form.reportValidity()) persist(input.value); });
        flexible.addEventListener('click', () => persist(''));
        dialog.addEventListener('cancel', event => { if (saving) event.preventDefault(); });
        dialog.addEventListener('close', () => dialog.remove()); dialog.showModal();
    }
    let quickAssign = false;
    let reopenAthleteWorkouts = false;
    const quickAssignNotice = node('p', '');
    quickAssignNotice.setAttribute('role', 'status');
    categoryChooser.before(quickAssignNotice);
    $('#assign-athlete-workout').addEventListener('click', () => {
        quickAssign = true;
        coachCategory = 'all'; coachType = 'all'; coachSearch.value = '';
        categoryChooser.hidden = true; libraryPanel.hidden = false;
        filterCoachTypes();
        quickAssignNotice.textContent = 'Kies een workout en plan deze in de agenda voor een week of een dag.';
    });
    function assignToAthlete(workout) {
        if (previewRole !== 'coach') return;
        openAssignment(workout, true);
    }
    const athleteWorkoutsDialog = document.createElement('dialog');
    athleteWorkoutsDialog.className = 'club-dialog';
    athleteWorkoutsDialog.setAttribute('aria-labelledby', 'athlete-workouts-title');
    athleteWorkoutsDialog.innerHTML = `<h2 id="athlete-workouts-title">Workouts van Guillaume</h2>
        <div id="coach-athlete-workouts"><p>Workouts worden geladen. Probeer opnieuw zodra Spring Boot verbonden is.</p></div>
        <button type="button" class="secondary-button">Sluiten</button>`;
    document.body.append(athleteWorkoutsDialog);
    athleteWorkoutsDialog.querySelector('button').addEventListener('click', () => athleteWorkoutsDialog.close());
    $('#review-athlete-workouts').addEventListener('click', () => {
        if (previewRole === 'coach') { loadWorkouts(); athleteWorkoutsDialog.querySelector('h2').textContent = 'Toegewezen workouts'; athleteWorkoutsDialog.showModal(); }
    });
    let assigningWorkout = null;
    const assignmentDialog = document.createElement('dialog'); assignmentDialog.className = 'club-dialog';
    assignmentDialog.setAttribute('aria-labelledby', 'assign-workout-title');
    assignmentDialog.innerHTML = `<form id="assign-saved-workout-form" class="club-form">
        <h2 id="assign-workout-title">Workout toewijzen</h2><p id="assign-workout-name"></p>
        <label>Toewijzen aan<select id="assignment-target"><option value="athlete">Sporter</option><option value="sessions">Specifieke groepssessies</option></select></label>
        <label>Sporter<select name="athleteId" required><option value="preview-guillaume">Guillaume (voorbeeldsporter)</option></select></label>
        <label>Datum / week kiezen<input name="date" id="assignment-date" type="date" required></label><p class="sample-notice">Kies een flexibele week of een vaste dag. Groepsworkouts worden aan specifieke sessies gekoppeld.</p>
        <div id="assignment-session-options" class="workout-session-options" hidden></div>
        <p id="assign-workout-status" role="status"></p>
        <button type="submit" class="primary-button">Toewijzen</button><button type="button" class="secondary-button" id="close-workout-assignment">Annuleren</button>
    </form>`;
    document.body.append(assignmentDialog);
    const assignmentForm = $('#assign-saved-workout-form');
    if (window.capReal) assignmentForm.elements.athleteId.replaceChildren(...window.capReal.athletes.map(p => new Option(p.name, p.id)));
    let returnToAthlete = false;
    const assignmentAgenda = window.capAgenda.mount($('#assignment-date'), { onChange: () => updateAssignmentTargets() });
    async function openAssignment(workout, fromAthlete = false) {
        if (window.capReal) {
            try { await window.capReal.refreshAthletes(); }
            catch (error) { $('#coach-workout-status').textContent = error.message; return; }
        }
        assigningWorkout = workout; returnToAthlete = fromAthlete;
        if (window.capReal && fromAthlete) assignmentForm.elements.athleteId.value = athleteId();
        assignmentAgenda.reset();
        $('#assignment-target').value = 'athlete'; updateAssignmentTargets();
        $('#assign-workout-name').textContent = workout.name;
        $('#assign-workout-status').textContent = '';
        assignmentDialog.showModal();
    }
    let assignmentSaving = false;
    function updateAssignmentTargets() {
        const forSessions = $('#assignment-target').value === 'sessions';
        assignmentAgenda.setGroup(forSessions);
        assignmentForm.elements.namedItem('athleteId').closest('label').hidden = forSessions;
        const list = $('#assignment-session-options'); list.hidden = !forSessions; list.replaceChildren();
        if (!forSessions || !assigningWorkout) return;
        const selection = assignmentAgenda.value();
        const matches = window.capClub.sessions().filter(session => (selection.mode === 'week'
            ? session.date >= selection.weekStart && session.date <= window.capAgenda.add(selection.weekStart, 6)
            : session.date === selection.date)
            && (!assigningWorkout.category || session.category === assigningWorkout.category));
        matches.forEach(session => {
            const label = node('label', ''); const input = document.createElement('input');
            input.type = 'checkbox'; input.name = 'sessionIds'; input.value = session.id;
            label.append(input, node('span', `${window.capDateTime.date(session.date)} · ${session.start} · ${session.category || session.title}`)); list.append(label);
        });
        if (!matches.length) list.append(node('p', 'Geen passende sessies op deze datum. Kies een andere datum of voeg eerst een sessie toe in Planning.'));
    }
    $('#assignment-target').addEventListener('change', updateAssignmentTargets);
    if(window.capReal && !window.capCan('CLASS_MANAGE')) {
        $('#assignment-target').querySelector('option[value="sessions"]')?.remove();
        $('#assignment-target').value='athlete';
    }
    $('#assignment-date').addEventListener('change', updateAssignmentTargets);
    $('#close-workout-assignment').addEventListener('click', () => assignmentDialog.close());
    assignmentDialog.addEventListener('cancel', event => { if (assignmentSaving) event.preventDefault(); });
    assignmentForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (assignmentSaving || !assigningWorkout || previewRole !== 'coach' || !window.capAccess.can('workouts')) return;
        if (window.capTraining.isActive(assigningWorkout.id)) { $('#assign-workout-status').textContent = 'Rond de lopende training eerst af.'; return; }
        assignmentSaving = true;
        const controls = Array.from(assignmentForm.querySelectorAll('button, input, select'), item => [item, item.disabled]);
        controls.forEach(([item]) => { item.disabled = true; });
        $('#assign-workout-status').textContent = 'Workout toewijzen…';
        try {
            if ($('#assignment-target').value === 'sessions') {
                const ids = Array.from(document.querySelectorAll('#assignment-session-options input:checked'), input => input.value);
                if (!ids.length) throw new Error('Selecteer minstens één specifieke sessie.');
                await window.capClub.assignWorkout(ids, assigningWorkout);
                assignmentDialog.close();
                $('#coach-workout-status').textContent = `Workout gekoppeld aan ${ids.length} gekozen sessie(s). Andere sessies blijven ongewijzigd.`;
                return;
            }
            const response = await fetch(`http://localhost:8080/api/workouts/${encodeURIComponent(assigningWorkout.id)}/assignment`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(8000),
                body: JSON.stringify({ athleteId: assignmentForm.elements.namedItem('athleteId').value,
                    date: assignmentAgenda.value().date,
                    weekStart: assignmentAgenda.value().mode === 'week' ? assignmentAgenda.value().weekStart : null })
            });
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.message || 'Toewijzen mislukt.');
            }
            assignmentDialog.close();
            const confirmation = assignmentAgenda.value().mode === 'week'
                ? `Workout “${assigningWorkout.name}” toegewezen voor de week ${window.capAgenda.range(assignmentAgenda.value().date)}.`
                : `Workout “${assigningWorkout.name}” ingepland op ${window.capDateTime.date(assignmentAgenda.value().date)}.`;
            $('#coach-workout-status').textContent = confirmation;
            if (returnToAthlete) { quickAssign = false; $('#athlete-action-status').textContent = confirmation; location.hash = 'athletes'; }
            await loadWorkouts();
            retry.focus();
        } catch (error) { $('#assign-workout-status').textContent = `${error.message} Controleer de toewijzing voordat je opnieuw probeert.`; }
        finally {
            assignmentSaving = false;
            controls.forEach(([item, disabled]) => { item.disabled = disabled; });
        }
    });
    async function deleteWorkout(workout, button) {
        if (!window.capAccess.can('workouts') || previewRole !== 'coach') return;
        const status = $('#coach-library-load-status');
        if (window.capTraining.isActive(workout.id)) {
            status.textContent = 'Deze workout heeft een lopende training. Rond die eerst af of verwijder de lopende training.';
            return;
        }
        if (!window.confirm(`Workout "${workout.name}" definitief verwijderen? Ook de toewijzing wordt verwijderd. Afgeronde trainingen en PRs blijven bewaard.`)) return;
        button.disabled = true;
        button.textContent = 'Verwijderen…';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        try {
            const response = await fetch(`http://localhost:8080/api/workouts/${encodeURIComponent(workout.id)}`, {
                method: 'DELETE', signal: controller.signal
            });
            if (!response.ok && response.status !== 404) throw new Error('Delete failed');
            const refreshed = await loadWorkouts();
            if (refreshed) status.textContent = `Workout "${workout.name}" verwijderd.`;
            retry.focus();
        } catch {
            status.textContent = 'Verwijderen kon niet worden bevestigd. Vernieuw de bibliotheek om te controleren en probeer opnieuw.';
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            button.textContent = 'Workout verwijderen';
        }
    }
    function details(workout, onRemove = null) {
        if(window.capReal && !window.capCan('WORKOUT_TEMPLATE_MANAGE'))onRemove=null;
        const content = document.createElement('div');
        content.append(node('h4', workout.name));
        if (workout.description) content.append(node('p', workout.description));
        content.append(window.capWorkoutView.render(workout.exercises, onRemove));
        window.capCoachGlenn.mount(content,workout);
        return content;
    }
    async function loadWorkouts() {
        if (loading) { pending = true; return; }
        loading = true;
        $('#reload-workouts').disabled = true;
        $('#api-workouts').setAttribute('aria-busy', 'true');
        $('#workouts-status').textContent = 'Workouts laden…';discovery.hidden=previewRole!=='athlete';if(!discovery.hidden&&!discovery.children.length)discovery.append(node('p','Jouw workouts laden…'));
        const finishLoading=capLoading.begin($('#api-workouts'),{shape:'cards',count:2});
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        try {
            const responses = await Promise.all(['/api/workouts', '/api/workout-assignments', '/api/training'].map(path => fetch(`http://localhost:8080${path}${window.capReal && previewRole === "coach" && path === "/api/training" && athleteId() ? "?athleteId=" + encodeURIComponent(athleteId()) : ""}`, { signal: controller.signal, cache: 'no-store' })));
            if (responses.some(response => !response.ok)) throw new Error('Load failed');
            const [workouts, assignments, history] = await Promise.all(responses.map(response => response.json()));
            const completed = new Set(history.map(session => session.assignmentId).filter(Boolean));
            if (!Array.isArray(assignments)) throw new Error('Invalid assignments');
            if (!Array.isArray(workouts) || !workouts.every(w => typeof w.name === 'string' && Array.isArray(w.exercises))) throw new Error('Invalid data');
            const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
            const allPersonal = assignments.map(a => ({ ...a.planned, assignmentId: a.assignmentId, note: a.note || '', weekStart: a.weekStart || '', assignedBy:a.assignedBy, assignedByName:a.assignedByName, assignedAt:a.assignedAt })).filter(w => w.athleteId === athleteId());
            const personalWorkouts = allPersonal.filter(w => w.weekStart ? w.weekStart === viewedWeek : w.assignedDate >= viewedWeek && w.assignedDate <= window.capAgenda.add(viewedWeek, 6))
                .sort((a, b) => (a.assignedDate || a.weekStart).localeCompare(b.assignedDate || b.weekStart));
            discover(allPersonal,completed,today);weekTitle.textContent = `Workouts deze week · ${window.capAgenda.range(viewedWeek)}`;
            $('#api-workouts').replaceChildren();
            $('#coach-workout-library').replaceChildren();
            personalWorkouts.forEach(workout => {
                const card = document.createElement('details'); card.className = 'workout-option compact-workout';
                card.dataset.search = `${workout.name} ${workout.description} ${workout.exercises.map(e => e.name).join(' ')}`.toLocaleLowerCase('nl-BE');
                const summary = node('summary', `${workout.name} · ${scheduleLabel(workout)}${completed.has(workout.assignmentId) ? ' · Afgerond' : ''}`);
                const available = !workout.weekStart || !completed.has(workout.assignmentId) && workout.weekStart <= today && window.capAgenda.add(workout.weekStart, 6) >= today;
                card.append(summary);
                card.append(details(workout));
                const select = node('button', 'Bekijk workout'); select.type = 'button'; select.className = 'primary-button';
                select.addEventListener('click', () => {
                    const viewer = document.createElement('dialog'); viewer.className = 'club-dialog workout-viewer';
                    viewer.setAttribute('aria-label', workout.name);
                    const start = node('button', 'Start training'); start.type = 'button'; start.className = 'primary-button';
                    start.hidden = !available;
                    start.addEventListener('click', () => { viewer.close(); window.capTraining.start(workout); });
                    const close = node('button', 'Sluiten'); close.type = 'button'; close.className = 'secondary-button';
                    close.addEventListener('click', () => viewer.close());
                    viewer.append(details(workout), start, close); document.body.append(viewer);
                    viewer.addEventListener('close', () => { viewer.remove(); select.focus(); });
                    viewer.showModal();
                });
                card.append(select);
                $('#api-workouts').append(card);
                const start = node('button', 'Start training'); start.type = 'button'; start.className = 'secondary-button';
                start.hidden = !available;
                start.addEventListener('click', () => window.capTraining.start(workout)); card.append(start);
                if (workout.weekStart && !completed.has(workout.assignmentId) && window.capAgenda.add(workout.weekStart, 6) >= today) {
                    const plan = node('button', workout.assignedDate ? 'Dag wijzigen' : 'Dag kiezen'); plan.type = 'button'; plan.className = 'secondary-button';
                    plan.addEventListener('click', () => planDay(workout)); card.append(plan);
                }
            });
            const categoryLists = new Map();
            ['', 'CAP HYROX', 'CAP Hybrid', 'CAP Move', 'CAP Lift & HIT', 'Private', 'Open gym', 'Athlete'].forEach(category => {
                const group = document.createElement('details'); group.className = 'saved-workout workout-category'; group.dataset.category = category || 'Algemeen / persoonlijk'; group.dataset.categoryKey = category;
                group.append(node('summary', category || 'Algemeen / persoonlijk'));
                const list = document.createElement('div'); group.append(list); categoryLists.set(category, { group, list, count: 0 });
            });
            workouts.forEach(workout => {
                const entry = document.createElement('details'); entry.className = 'saved-workout'; entry.dataset.workoutType = workout.workoutType || 'other';
                entry.dataset.search = `${workout.name} ${workout.description || ''} ${workout.exercises.map(exercise => exercise.name).join(' ')}`.toLocaleLowerCase('nl-BE');
                const edit = node('button', 'Bewerken'); edit.type = 'button'; edit.className = 'secondary-button';
                edit.setAttribute('aria-label', `Workout ${workout.name} bewerken`);
                edit.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    document.dispatchEvent(new CustomEvent('cap:edit-workout', { detail: workout }));
                });
                entry.append(node('summary', workout.name), details(workout));
                entry.querySelector('summary').addEventListener('click', event => {
                    if (!quickAssign) return;
                    event.preventDefault();
                    assignToAthlete(workout);
                });
                if (workout.athleteId && workout.assignedDate) entry.append(node('p', `Toegewezen aan sporter · ${window.capDateTime.date(workout.assignedDate)}`));
                const remove = node('button', 'Workout verwijderen');
                remove.type = 'button'; remove.className = 'secondary-button delete-workout';
                remove.setAttribute('aria-label', `Workout ${workout.name} verwijderen`);
                remove.addEventListener('click', () => deleteWorkout(workout, remove));
                if(!window.capReal || window.capCan('WORKOUT_TEMPLATE_MANAGE')) {entry.append(remove);entry.append(edit);}
                const assign = node('button', 'Toewijzen'); assign.type = 'button'; assign.className = 'secondary-button';
                assign.setAttribute('aria-label', `Workout ${workout.name} toewijzen`);
                assign.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    openAssignment(workout);

                });
                entry.append(assign);
                if (quickAssign) {
                    edit.hidden = true; remove.hidden = true; assign.hidden = true;
                    entry.querySelector('summary').setAttribute('aria-label', `${workout.name} vandaag toewijzen aan sporter`);
                }
                const category = categoryLists.get(workout.category || '') || categoryLists.get('');
                category.list.prepend(entry); category.count++;
            });
            categoryLists.forEach(({group, count}, category) => {
                group.querySelector('summary').textContent = `${category || 'Algemeen / persoonlijk'} (${count})`;
                if (count) $('#coach-workout-library').append(group);
            });
            updateCategoryCounts(workouts);
            filterCoachTypes();
            filterLibrary();
            const assigned = allPersonal.find(w => w.assignedDate === today || w.weekStart && w.weekStart <= today && window.capAgenda.add(w.weekStart, 6) >= today && !completed.has(w.assignmentId));
            const athleteWorkouts = $('#coach-athlete-workouts');
            athleteWorkouts.replaceChildren();
            if (!allPersonal.length) athleteWorkouts.append(node('p', 'Nog geen workouts toegewezen.'));
            [...allPersonal].sort((a, b) => (b.assignedDate || b.weekStart).localeCompare(a.assignedDate || a.weekStart)).forEach(workout => {
                const entry = document.createElement('details'); entry.className = 'saved-workout';
                const preview = details(workout, async (index, button) => {
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    if (workout.exercises.length === 1) {
                        feedback.textContent = 'Dit is de laatste oefening. Gebruik Volledige workout verwijderen om de volledige workout te verwijderen.';
                        return;
                    }
                    const exercises = workout.exercises.filter((_, i) => i !== index).map(exercise => ({ ...exercise }));
                    exercises.forEach(exercise => {
                        if (exercise.supersetGroup && exercises.filter(item => item.supersetGroup === exercise.supersetGroup).length < 2) exercise.supersetGroup = '';
                    });
                    assignmentSaving = true; button.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'PUT', { ...workout, exercises });
                        workout.exercises = exercises;
                        await loadWorkouts();
                        Array.from($('#coach-athlete-workouts').children).find(item => item.dataset.assignmentId === workout.assignmentId)?.setAttribute('open', '');
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; button.disabled = false; }
                });
                entry.dataset.assignmentId = workout.assignmentId;
                entry.append(node('summary', `${workout.name} · ${scheduleLabel(workout)}`));
                const actions = node('div', ''); actions.className = 'athlete-actions';
                const edit = node('button', 'Bewerken'); edit.type = 'button'; edit.className = 'secondary-button';
                const remove = node('button', 'Volledige workout verwijderen'); remove.type = 'button'; remove.className = 'secondary-button';
                const feedback = node('p', ''); feedback.setAttribute('role', 'status');
                edit.addEventListener('click', () => {
                    if (previewRole !== 'coach') return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    document.dispatchEvent(new CustomEvent('cap:edit-assignment', { detail: workout }));
                    if (location.hash === '#create-workout') athleteWorkoutsDialog.close();
                });
                remove.addEventListener('click', async () => {
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    if (window.capTraining.isActive(workout.id)) { feedback.textContent = 'Rond de lopende training eerst af.'; return; }
                    if (!window.confirm(`Workout “${workout.name}” · ${scheduleLabel(workout)} verwijderen bij deze sporter? Het opgeslagen voorbeeld blijft behouden.`)) return;
                    assignmentSaving = true; remove.disabled = true; edit.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'DELETE');
                        await loadWorkouts();
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; remove.disabled = false; edit.disabled = false; }
                });
                const wholeWorkout = node('section', ''); wholeWorkout.className = 'whole-workout-actions';
                entry.append(node('p',workout.assignedBy?'Toegewezen door '+(workout.assignedByName||workout.assignedBy)+' · '+window.capDateTime.dateTime(workout.assignedAt):'Bestaande toewijzing · oorspronkelijke medewerker en datum onbekend'));
                const scope = node('div', '');
                scope.append(node('h3', 'Volledige workout'), node('p', 'Verwijder deze volledige workout voor deze datum, inclusief alle oefeningen.'));
                wholeWorkout.append(scope, remove);
                if(window.capReal && !window.capCan('WORKOUT_TEMPLATE_MANAGE')) {wholeWorkout.hidden=true;edit.hidden=true;}
                actions.append(edit);
                if (workout.weekStart && !completed.has(workout.assignmentId) && window.capAgenda.add(workout.weekStart, 6) >= today) {
                    const plan = node('button', workout.assignedDate ? 'Dag wijzigen' : 'Dag plannen'); plan.type = 'button'; plan.className = 'secondary-button';
                    plan.addEventListener('click', () => planDay(workout)); actions.append(plan);
                }
                entry.append(wholeWorkout, preview);
                const notePanel = document.createElement('details');
                if(window.capReal && !window.capCan('WORKOUT_TEMPLATE_MANAGE'))notePanel.hidden=true;
                notePanel.append(node('summary', workout.note ? 'Notitie bekijken / wijzigen' : 'Notitie toevoegen'));
                const form = document.createElement('form'); form.className = 'club-form';
                const label = node('label', 'Notitie voor deze workout');
                const textarea = document.createElement('textarea'); textarea.rows = 3; textarea.maxLength = 2000; textarea.value = workout.note;
                label.append(textarea);
                const save = node('button', 'Notitie bewaren'); save.type = 'submit'; save.className = 'secondary-button';
                form.append(label, save); notePanel.append(form);
                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    if (previewRole !== 'coach' || assignmentSaving) return;
                    assignmentSaving = true; save.disabled = true;
                    try {
                        await changeAssignment(workout.assignmentId, 'PUT', { note: textarea.value }, '/note');
                        notePanel.querySelector('summary').textContent = textarea.value.trim() ? 'Notitie bekijken / wijzigen' : 'Notitie toevoegen';
                        feedback.textContent = 'Notitie bewaard.';
                    } catch (error) { feedback.textContent = error.message; }
                    finally { assignmentSaving = false; save.disabled = false; }
                });
                if(window.capReal){
                    notePanel.hidden=!workout.note;
                    notePanel.replaceChildren(node('summary','Historische notitie · auteur en datum onbekend'),node('p',workout.note));
                    const addNote=node('a','Privénotitie toevoegen');addNote.href='#staff-notes';addNote.className='secondary-button';
                    addNote.addEventListener('click',()=>{athleteWorkoutsDialog.close();document.dispatchEvent(new CustomEvent('cap:note-client',{detail:{clientId:workout.athleteId,assignmentId:workout.assignmentId}}));});
                    actions.append(addNote);
                }
                entry.append(actions, notePanel, feedback);
                athleteWorkouts.append(entry);
            });
            $('#home-workout-meta').textContent = assigned ? 'Workout voor vandaag klaar' : 'Jouw trainingen';
            $('#athlete-assignment-status').textContent = window.capReal?.account.role==='COACH' ? 'Bekijk sporters en hun toegewezen trainingen' : assigned ? `${assigned.weekStart ? 'Deze week' : 'Vandaag'}: ${assigned.name}` : 'Geen workout klaar voor vandaag of deze week.';
            $('#athlete-list-assignment').textContent = assigned ? `${assigned.name} · ${scheduleLabel(assigned)}` : 'Geen workout klaar voor vandaag of deze week.';
            $('#coach-library-empty').hidden = workouts.length > 0;
            $('#workouts-status').textContent = personalWorkouts.length ? 'Jouw workouts voor deze week. Kies eventueel een dag of open een workout om te trainen.' : 'Geen persoonlijke workouts in deze week. Gebruik de pijlen voor een andere week. Geboekte groepssessies verschijnen hierboven.';
            $('#coach-library-load-status').textContent = '';
            if (reopenAthleteWorkouts && previewRole === 'coach' && location.hash === '#athletes') {
                reopenAthleteWorkouts = false;
                if (!athleteWorkoutsDialog.open) athleteWorkoutsDialog.showModal();
            }
            return true;
        } catch {
            const message = 'Workouts konden niet worden vernieuwd. Controleer Spring Boot en probeer opnieuw. Eerder geladen gegevens kunnen verouderd zijn.';
            $('#workouts-status').textContent = message;
            if(previewRole==='athlete'){discovery.querySelector('.workout-day-load-error')?.remove();const warning=node('p',message);warning.className='workout-day-load-error';warning.setAttribute('role','alert');discovery.append(warning);}
            $('#coach-library-load-status').textContent = message;
        } finally {
            finishLoading(); clearTimeout(timeout); loading = false;
            $('#reload-workouts').disabled = false;
            $('#api-workouts').setAttribute('aria-busy', 'false');
            if (pending) { pending = false; loadWorkouts(); }
        }
    }
    const status = node('p', ''); status.id = 'coach-library-load-status'; status.setAttribute('role', 'status');
    async function changeAssignment(id, method, body, suffix = '') {
        const response = await fetch(`http://localhost:8080/api/workout-assignments/${encodeURIComponent(id)}${suffix}`, {
            method, headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(8000)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Bewaren mislukt. Controleer Spring Boot en probeer opnieuw.');
        }
    }
    document.addEventListener('cap:assignment-saved', () => {
        reopenAthleteWorkouts = true;
        loadWorkouts();
    });
    const weekPanel=document.getElementById('workout-library-panel');if(weekPanel){weekPanel.hidden=previewRole==='athlete';weekPanel.open=false;weekPanel.querySelector('summary').textContent='Zoeken op week';}
    const retry = node('button', 'Bibliotheek vernieuwen'); retry.type = 'button'; retry.className = 'secondary-button';
    retry.addEventListener('click', loadWorkouts);
    categoryChooser.before(status, retry);
    $('#reload-workouts').addEventListener('click', loadWorkouts);
    $('#assigned-workout-period').addEventListener('change', () => {
        viewedWeek = window.capAgenda.monday(window.capAgenda.today());
        if ($('#assigned-workout-period').value === 'past') viewedWeek = window.capAgenda.add(viewedWeek, -7);
        loadWorkouts();
    });
    $('#workout-search').addEventListener('input', () => { visibleLimit = 6; filterLibrary(); });
    $('#show-more-workouts').addEventListener('click', () => { visibleLimit += 6; filterLibrary(); });
    document.addEventListener('cap:workouts-saved', () => { showLibraryCategories(false); loadWorkouts(); });
    document.addEventListener('cap:viewchange', () => {
        if(location.hash==='#workouts'&&lastWorkoutRoute!=='#workouts')selectedWorkoutDate=capAgenda.today();lastWorkoutRoute=location.hash;
        if (location.hash !== '#saved-workouts') {
            quickAssign = false;
            quickAssignNotice.textContent = '';
        }
        if (window.capAccess.signedIn && ['dashboard', 'saved-workouts', 'workouts', 'athletes', ''].includes(location.hash.slice(1))) loadWorkouts();
    });
})();
