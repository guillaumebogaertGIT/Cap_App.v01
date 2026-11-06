// Shared owner tasks and per-owner notification reads; preview data stays separate.
(() => {
    const $ = id => document.getElementById(id), real = window.capReal;
    const allowed = () => real ? real.account.role === 'COACH' && real.staffRole === 'OWNER' : previewRole === 'coach';
    const active = () => allowed() && location.hash === '#owner-agenda';
    const today = () => new Intl.DateTimeFormat('sv-SE', {timeZone:'Europe/Brussels'}).format(new Date());
    const date = value => new Date(value + 'T12:00:00Z');
    const iso = value => value.toISOString().slice(0,10);
    const monthOf = value => value.slice(0,7);
    let selected=today(), month=monthOf(selected), tasks=[], owners=[], generation=0, busy=false, editing=null;
    const demoOwners=[{id:'preview-glenn',name:'Glenn'},{id:'preview-dieter',name:'Dieter'}];
    // Home can open the same task editor without navigating away.
    document.body.append($('agenda-dialog'));
    let demoTasks=window.capStorage.read('owner-agenda-preview',[]), demoNotifications=[];
    const node=(tag,text,className)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(className)el.className=className;return el;};
    function button(text,action){const el=node('button',text,'secondary-button');el.type='button';el.onclick=action;return el;}
    function render(){
        $('agenda-month').textContent=new Intl.DateTimeFormat('nl-BE',{month:'long',year:'numeric',timeZone:'UTC'}).format(date(month+'-01'));
        $('agenda-day').textContent=window.capDateTime.dayLabel(selected);
        const grid=$('agenda-grid');grid.replaceChildren();
        const first=date(month+'-01'),offset=(first.getUTCDay()+6)%7;
        for(let i=0;i<42;i++){
            const day=new Date(first);day.setUTCDate(i-offset+1);const value=iso(day),outside=monthOf(value)!==month;
            const entries=tasks.filter(t=>t.date===value),open=entries.filter(t=>!t.done).length;
            const el=button('',()=>{selected=value;render();$('agenda-day').focus();});
            el.replaceChildren(node('span',String(day.getUTCDate())));
            if(entries.length)el.append(node('small',open?`${open} open`:'✓'));
            el.setAttribute('aria-label',`${window.capDateTime.date(value)}: ${open} open, ${entries.length} taken`);
            el.setAttribute('aria-pressed',String(value===selected));if(value===today())el.setAttribute('aria-current','date');
            if(outside){el.classList.add('agenda-outside');el.disabled=true;}
            el.onkeydown=event=>{const step={ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7}[event.key];if(!step)return;event.preventDefault();const next=date(value);next.setUTCDate(next.getUTCDate()+step);selected=iso(next);if(monthOf(selected)!==month){month=monthOf(selected);load();}else{render();grid.querySelector(`[data-date="${selected}"]`)?.focus();}};
            el.dataset.date=value;grid.append(el);
        }
        const list=$('agenda-tasks');list.replaceChildren();
        const daily=tasks.filter(t=>t.date===selected&&($('agenda-show-done').checked||!t.done));
        if(!daily.length)list.append(node('p','Geen taken voor deze dag. Voeg een taak toe of kies een andere datum.'));
        for(const task of daily){
            const item=node('article',null,'agenda-task'+(task.done?' is-done':''));item.append(node('h4',task.title));
            item.append(node('p',task.assignees.map(id=>owners.find(o=>o.id===id)?.name||'Voormalige admin').join(' & ')+(task.done?' · Klaar':''),'agenda-task-meta'));
            if(task.notes)item.append(node('p',task.notes));
            const actions=node('div',null,'agenda-task-actions');
            const toggle=button('',()=>save({...task,done:!task.done}));
            toggle.classList.add('task-check');toggle.setAttribute('aria-pressed',String(task.done));
            toggle.setAttribute('aria-label',`${task.done?'Taak opnieuw openen':'Taak afronden'}: ${task.title}`);
            toggle.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>';
            actions.append(toggle,button('Bewerken',()=>open(task)),button('Verwijderen',async()=>{try{await removeTask(task);}catch(error){$('agenda-status').textContent=error.message;}}));item.append(actions);list.append(item);
        }
        $('agenda-add').disabled=busy||!owners.length;
    }
    async function notifications(){
        if(!allowed()||document.hidden)return;
        try{
            const data=real?await real.request('/api/owner-agenda/notifications'):{items:demoNotifications,unread:demoNotifications.filter(n=>!n.read).length};
            const badge=$('agenda-unread');badge.hidden=!data.unread;badge.textContent=data.unread?`(${data.unread})`:'';
            $('agenda-notification-count').textContent=data.unread?`· ${data.unread} ongelezen`:'';
            const list=$('agenda-notifications');list.replaceChildren();
            if(!data.items.length)list.append(node('li','Nog geen workoutmeldingen. Nieuwe voltooide workouts verschijnen hier.'));
            for(const event of data.items){
                const item=node('li',null,event.read?'':'is-unread');item.append(node('strong',`${event.athlete} heeft ${event.workout} voltooid`),node('p',window.capDateTime.dateTime(event.completed)));
                if(!event.read)item.append(button('Markeer als gelezen',async()=>{try{if(real)await real.request(`/api/owner-agenda/notifications/${event.id}/read`,'PUT');else event.read=true;await notifications();}catch(error){$('agenda-status').textContent=error.message;}}));list.append(item);
            }
        }catch(error){if(active())$('agenda-status').textContent='Meldingen laden mislukt. '+error.message;}
    }
    async function load(){
        if(!active())return;const current=++generation;$('agenda-status').textContent='Agenda laden…';$('agenda-add').disabled=true;
        try{
            const data=real?await real.request('/api/owner-agenda?month='+month):{tasks:demoTasks.filter(t=>monthOf(t.date)===month),owners:demoOwners};
            if(current!==generation||!active())return;tasks=data.tasks;owners=data.owners;render();$('agenda-status').textContent=real?'':'Ontwerpvoorbeeld · Taken blijven alleen in deze preview.';await notifications();
        }catch(error){if(current===generation){$('agenda-status').textContent=error.message;tasks=[];owners=[];render();}}
    }
    function open(task=null){
        editing=task;const form=$('agenda-form');form.reset();form.elements.title.value=task?.title||'';form.elements.date.value=task?.date||selected;form.elements.notes.value=task?.notes||'';
        $('agenda-dialog-title').textContent=task?'Taak bewerken':'Taak toevoegen';$('agenda-form-status').textContent='';
        $('agenda-assignees').replaceChildren(...owners.map(owner=>{const label=node('label',null,'agenda-assignee'),input=node('input');input.type='checkbox';input.name='assignee';input.value=owner.id;input.checked=task?task.assignees.includes(owner.id):owner.id===(real?.account.id||demoOwners[0].id);label.append(input,node('span',owner.name));return label;}));
        $('agenda-dialog').showModal();form.elements.title.focus();
    }
    async function save(input){
        if(busy)return;busy=true;document.querySelectorAll('#owner-agenda button, #agenda-dialog button').forEach(el=>el.disabled=true);
        try{
            const saved=real?await real.request('/api/owner-agenda/tasks/'+input.id,'PUT',input):{...input,version:input.version+1};
            if(!real){demoTasks=demoTasks.filter(t=>t.id!==saved.id);demoTasks.push({...saved,updatedAt:new Date().toISOString()});window.capStorage.write('owner-agenda-preview',demoTasks);}
            selected=saved.date;month=monthOf(selected);$('agenda-dialog').close();await load();$('agenda-status').textContent='Taak bewaard.';
            document.dispatchEvent(new CustomEvent('cap:owner-agenda-changed'));
        }catch(error){($('agenda-dialog').open?$('agenda-form-status'):$('agenda-status')).textContent=error.message;}
        finally{busy=false;document.querySelectorAll('#owner-agenda button, #agenda-dialog button').forEach(el=>el.disabled=false);render();}
    }
    $('agenda-form').onsubmit=event=>{event.preventDefault();const form=event.currentTarget,assignees=[...form.querySelectorAll('[name="assignee"]:checked')].map(el=>el.value);if(!assignees.length){$('agenda-form-status').textContent='Kies minstens één verantwoordelijke.';return;}save({id:editing?.id||crypto.randomUUID(),title:form.elements.title.value,notes:form.elements.notes.value,date:form.elements.date.value,done:editing?.done||false,version:editing?.version??-1,assignees});};
    $('agenda-add').onclick=()=>open();$('agenda-close').onclick=()=>$('agenda-dialog').close();
    $('agenda-dialog').addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    function shiftMonth(step){const value=date(month+'-01');value.setUTCMonth(value.getUTCMonth()+step);if(value.getUTCFullYear()<1900||value.getUTCFullYear()>2200)return;month=monthOf(iso(value));selected=month+'-01';load();}
    $('agenda-prev').onclick=()=>shiftMonth(-1);$('agenda-next').onclick=()=>shiftMonth(1);$('agenda-today').onclick=()=>{selected=today();month=monthOf(selected);load();};
    $('agenda-refresh').onclick=load;$('agenda-show-done').onchange=render;
    async function removeTask(task){
        if(!allowed()||busy)return false;
        if(!window.confirm(`Taak “${task.title}” definitief verwijderen? De taak verdwijnt voor beide admins uit Home en Taken & agenda.`))return false;
        busy=true;
        try{
            if(real)await real.request(`/api/owner-agenda/tasks/${task.id}?version=${task.version}`,'DELETE');
            else{demoTasks=demoTasks.filter(item=>item.id!==task.id);window.capStorage.write('owner-agenda-preview',demoTasks);}
            await load();$('agenda-status').textContent='Taak verwijderd.';
            document.dispatchEvent(new CustomEvent('cap:owner-agenda-changed'));
            return true;
        }finally{busy=false;}
    }
    window.capOwnerAgenda={
        remove:removeTask,
        preview(){return {tasks:[...demoTasks],owners:demoOwners,notifications:demoNotifications};},
        create(dateValue,ownerList){if(!allowed()||busy)return;owners=ownerList;selected=dateValue;open();},
        async complete(task){
            if(!allowed())throw new Error('Geen toegang.');
            if(real)await real.request('/api/owner-agenda/tasks/'+task.id,'PUT',{...task,done:!task.done});
            else{demoTasks=demoTasks.map(t=>t.id===task.id?{...t,done:!t.done,version:t.version+1,updatedAt:new Date().toISOString()}:t);window.capStorage.write('owner-agenda-preview',demoTasks);}
            document.dispatchEvent(new CustomEvent('cap:owner-agenda-changed'));
        },
        async read(id){if(!allowed())throw new Error('Geen toegang.');if(real)await real.request(`/api/owner-agenda/notifications/${id}/read`,'PUT');else{const item=demoNotifications.find(n=>n.id===id);if(item)item.read=true;}await notifications();document.dispatchEvent(new CustomEvent('cap:owner-agenda-changed'));}
    };
    document.addEventListener('cap:viewchange',()=>{if(active())load();else{generation++;if(!busy)$('agenda-dialog').close();}});
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)notifications();});
    setInterval(()=>{if(allowed()&&!document.hidden)notifications();},60000);
    if(allowed())notifications();if(active())load();
})();
