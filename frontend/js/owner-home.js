// Owner Home is an operational overview; other roles keep their existing Home.
(() => {
    const $=id=>document.getElementById(id),real=window.capReal,agenda=window.capOwnerAgenda,club=window.capClub;
    const host=$('owner-home'),tiles=document.querySelector('#coach > .home-tiles');
    const intro=document.querySelector('#coach .dashboard-intro'),notice=document.querySelector('#coach .sample-notice'),originalIntro=intro.textContent;
    const allowed=()=>real?real.account.role==='COACH'&&real.staffRole==='OWNER':previewRole==='coach';
    const active=()=>allowed()&&['','#dashboard'].includes(location.hash)&&window.capAccess.signedIn;
    const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Brussels'}).format(new Date());
    let owners=[],generation=0,loading=false;
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
    function action(text,fn){const el=node('button',text,'secondary-button');el.type='button';el.onclick=async()=>{el.disabled=true;try{await fn();}catch(error){$('owner-home-feedback').textContent=error.message;}finally{el.disabled=false;}};return el;}
    function apply(){host.hidden=!allowed();tiles.hidden=allowed();notice.hidden=allowed();intro.textContent=allowed()?'Jouw club vandaag: sessies, taken en vooruitgang.':originalIntro;}
    function lessons(){
        if(!active())return;
        $('owner-home-date').textContent=window.capDateTime.dayLabel(today());
        club.renderUpcomingSessions($('owner-home-lessons'),4,today());
        $('owner-home-lessons-title').textContent='Sessies vandaag';
    }
    function taskList(data){
        owners=data.owners;$('owner-home-task').disabled=!owners.length;
        const todayCount=data.dueCount-data.overdueCount;
        $('owner-home-task-summary').textContent=todayCount?`${todayCount} ${todayCount===1?'taak':'taken'} te doen vandaag.`:data.tasks.some(task=>task.date===data.today)?'Alle taken voor vandaag zijn afgerond.':'';
        const list=$('owner-home-tasks');list.replaceChildren();
        list.classList.toggle('has-many-tasks',data.tasks.length>3);
        const renderTask=task=>{
            const row=node('article',null,'owner-home-task'+(task.done?' is-done':'')),copy=node('div');copy.append(node('h4',task.title));
            const names=task.assignees.map(id=>owners.find(o=>o.id===id)?.name||'Voormalige admin').join(' & ');
            const due=task.date<data.today?`${task.done?'':'Te laat · '}${window.capDateTime.date(task.date)} · `:'';
            copy.append(node('p',`${due}${names}${task.done?' · Afgerond':''}`,task.date<data.today&&!task.done?'is-overdue':''));
            const toggle=action('',async()=>{await agenda.complete(task);$('owner-home-feedback').textContent=task.done?'Taak opnieuw geopend.':'Taak afgerond.';});
            toggle.classList.add('task-check');toggle.setAttribute('aria-pressed',String(task.done));
            toggle.setAttribute('aria-label',`${task.done?'Taak opnieuw openen':'Taak afronden'}: ${task.title}`);
            toggle.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>';
            const remove=action('',async()=>{if(await agenda.remove(task))$('owner-home-feedback').textContent='Taak verwijderd.';});
            remove.classList.add('task-check');remove.setAttribute('aria-label',`Taak verwijderen: ${task.title}`);remove.title='Taak verwijderen';
            remove.innerHTML='<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/></svg>';
            const actions=node('div',null,'owner-home-task-actions');actions.append(toggle,remove);
            row.append(copy,actions);list.append(row);
        };
        const todaysTasks=data.tasks.filter(task=>task.date===data.today),overdue=data.tasks.filter(task=>task.date<data.today);
        todaysTasks.forEach(renderTask);
        if(!todaysTasks.length){
            const empty=node('div',null,'owner-home-task-empty');empty.append(node('p','Geen taken voor vandaag.'),node('p','Gebruik + om een taak voor vandaag toe te voegen.'));list.append(empty);
        }
        if(overdue.length){list.append(node('h4','Van eerdere dagen','owner-home-overdue-heading'));overdue.forEach(renderTask);}
        if((data.totalCount??data.dueCount)>data.tasks.length)list.append(node('p',`Er zijn meer taken. Bekijk ze in Taken & agenda.`));
    }
    function completions(data){
        const list=$('owner-home-completions');list.replaceChildren();
        $('owner-home-completions-title').textContent='Recent getraind'+(data.unread?` · ${data.unread} ongelezen`:'');
        for(const item of data.items.slice(0,6)){
            const row=node('li',null,item.read?'':'is-unread'),copy=node('div');copy.append(node('strong',item.athlete),node('p',item.workout),node('p',window.capDateTime.dateTime(item.completed)));row.append(copy);
            if(!item.read)row.append(action('Gelezen',()=>agenda.read(item.id)));list.append(row);
        }
        if(!data.items.length)list.append(node('li','Nog geen nieuwe voltooide workouts. Zodra een sporter een training afrondt, zie je het hier.'));
    }
    async function refresh(){
        apply();if(!active())return;const current=++generation;loading=true;$('owner-home-refresh').disabled=true;$('owner-home-task').disabled=true;
        lessons();$('owner-home-tasks-status').textContent='Taken laden…';$('owner-home-completions-status').textContent='Workouts laden…';
        const preview=()=>{const data=agenda.preview(),due=data.tasks.filter(t=>t.date===today()||t.date<today()&&(!t.done||t.updatedAt&&new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Brussels'}).format(new Date(t.updatedAt))===today())).sort((a,b)=>Number(b.date===today())-Number(a.date===today())||a.date.localeCompare(b.date)||a.id.localeCompare(b.id));return {today:today(),tasks:due.slice(0,8),totalCount:due.length,dueCount:due.filter(t=>!t.done).length,overdueCount:due.filter(t=>!t.done&&t.date<today()).length,owners:data.owners};};
        const results=await Promise.allSettled([
            real?real.request('/api/owner-agenda/overview'):Promise.resolve(preview()),
            real?real.request('/api/owner-agenda/notifications'):Promise.resolve({items:agenda.preview().notifications,unread:agenda.preview().notifications.filter(n=>!n.read).length})
        ]);
        if(current!==generation||!active())return;
        loading=false;$('owner-home-refresh').disabled=false;
        const [tasks,events]=results;
        if(tasks.status==='fulfilled'){taskList(tasks.value);$('owner-home-tasks-status').textContent='';}
        else{owners=[];$('owner-home-tasks').replaceChildren();$('owner-home-task-summary').textContent='';$('owner-home-tasks-status').textContent='Taken laden mislukt. '+tasks.reason.message+' Kies Vernieuwen om opnieuw te proberen.';}
        if(events.status==='fulfilled'){completions(events.value);$('owner-home-completions-status').textContent='';}
        else{$('owner-home-completions').replaceChildren();$('owner-home-completions-status').textContent='Workoutmeldingen laden mislukt. '+events.reason.message+' Kies Vernieuwen om opnieuw te proberen.';}
    }
    $('owner-home-task').onclick=()=>agenda.create(today(),owners);
    $('owner-home-lesson').onclick=()=>club.planSession(today());
    $('owner-home-refresh').onclick=async()=>{refresh();try{await club.refreshSessions();lessons();$('owner-home-lessons-status').textContent='';}catch(error){$('owner-home-lessons-status').textContent='Lessen vernieuwen mislukt. '+error.message;}};
    document.addEventListener('cap:viewchange',()=>{apply();if(active())refresh();else generation++;});
    document.addEventListener('cap:club-changed',lessons);
    document.addEventListener('cap:session-saved',lessons);
    document.addEventListener('cap:owner-agenda-changed',()=>{if(active())refresh();});
    document.addEventListener('visibilitychange',()=>{if(!document.hidden&&active())refresh();});
    setInterval(()=>{if(active()&&!document.hidden&&!loading)refresh();},60000);
    apply();if(active())refresh();
})();
