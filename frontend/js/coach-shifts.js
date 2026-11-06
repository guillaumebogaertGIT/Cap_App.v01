// Working hours are staff-only and never bookable lessons.
(() => {
    const $=id=>document.getElementById(id),real=window.capReal;
    const allowed=()=>real?real.account.role==='COACH'&&['OWNER','MANAGER','INSTRUCTOR'].includes(real.staffRole):previewRole==='coach';
    const manage=()=>allowed()&&(!real||real.staffRole==='OWNER');
    const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Brussels'}).format(new Date());
    const now=()=>window.capDateTime.time(new Date());
    const future=shift=>shift.date>today()||shift.date===today()&&shift.start>now();
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
    function button(text,action){const el=node('button',text,'secondary-button');el.type='button';el.onclick=action;return el;}
    const panel=node('section',null,'coach-shifts-panel');panel.hidden=true;panel.setAttribute('aria-labelledby','coach-shifts-title');
    panel.innerHTML='<div class="coach-shifts-heading"><h3 id="coach-shifts-title">Werkuren</h3><button id="coach-shifts-add" class="secondary-button" type="button">+ Werkuren plannen</button></div><p>Werkuren staan los van lessen en zijn niet boekbaar voor sporters.</p><p id="coach-shifts-status" role="status"></p><button id="coach-shifts-retry" class="secondary-button" type="button" hidden>Opnieuw laden</button><div id="coach-shifts-list"></div>';
    $('calendar-day-sessions').after(panel);
    const quickAdd=button('+ Werkuren',()=>open());quickAdd.id='coach-shifts-quick-add';quickAdd.hidden=true;$('new-session').after(quickAdd);
    const dialog=node('dialog',null,'club-dialog');dialog.id='coach-shift-dialog';dialog.setAttribute('aria-labelledby','coach-shift-title');
    dialog.innerHTML='<div class="session-dialog-header"><h2 id="coach-shift-title">Werkuren plannen</h2><button id="coach-shift-close" type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div><form id="coach-shift-form" class="club-form"><label>Coach<select name="coachId" required></select></label><div class="club-form-grid"><label>Datum<input name="date" type="date" required></label><label>Start<input name="start" type="time" required></label><label>Einde<input name="end" type="time" required></label></div><label>Notitie (optioneel)<textarea name="note" maxlength="500" rows="2"></textarea></label><p id="coach-shift-error" role="status"></p><button class="primary-button" type="submit">Werkuren bewaren</button></form>';
    document.body.append(dialog);
    let key=null,from=null,through=null,items=[],coaches=[],status='idle',error='',selected=today(),busy=false,editing=null,generation=0;
    let demo=window.capStorage.read('coach-shifts-preview',[]);
    const demoCoaches=[{id:'preview-glenn',name:'Glenn'},{id:'preview-dieter',name:'Dieter'},{id:'preview-mathias',name:'Mathias'}];
    const changed=()=>document.dispatchEvent(new CustomEvent('cap:coach-shifts-changed'));
    async function load(start,end,force=false){
        if(!allowed())return;const next=start+end;if(!force&&key===next)return;
        key=next;from=start;through=end;status='loading';const current=++generation;
        // Notify after the calendar finishes its current render, including in preview mode.
        queueMicrotask(changed);await Promise.resolve();
        try{
            const data=real?await real.request(`/api/coach-shifts?from=${start}&through=${end}`):{shifts:demo.filter(s=>s.date>=start&&s.date<=end),coaches:demoCoaches};
            if(current!==generation||!allowed())return;
            items=data.shifts.map(s=>({...s,start:s.start.slice(0,5),end:s.end.slice(0,5)}));coaches=data.coaches;status='ready';error='';
        }catch(e){if(current!==generation)return;status='error';error=e.message;}
        changed();
    }
    function renderDay(day){
        selected=day;panel.hidden=!allowed();if(!allowed())return;
        $('coach-shifts-title').textContent=real&&real.staffRole!=='OWNER'?'Mijn werkuren':'Werkuren';
        quickAdd.hidden=!manage();quickAdd.disabled=busy||status!=='ready'||!coaches.length||day<today();
        $('coach-shifts-add').hidden=!manage()||day<today();$('coach-shifts-add').disabled=busy||status!=='ready'||!coaches.length;
        $('coach-shifts-status').textContent=status==='loading'?'Werkuren laden…':status==='error'?'Werkuren laden mislukt. '+error:'';
        $('coach-shifts-retry').hidden=status!=='error';
        const list=$('coach-shifts-list');list.replaceChildren();
        for(const shift of items.filter(s=>s.date===day).sort((a,b)=>a.start.localeCompare(b.start))){
            const row=node('article',null,'coach-shift-row');row.append(node('h4',`${shift.start} – ${shift.end} · ${shift.coach}`));if(shift.note)row.append(node('p',shift.note));
            if(manage()&&future(shift)){const actions=node('div',null,'coach-shift-actions');actions.append(button('Bewerken',()=>open(shift)),button('Verwijderen',()=>remove(shift)));row.append(actions);}else if(!future(shift))row.append(node('p','Gestart / afgelopen · Historiek blijft behouden.','sample-notice'));
            list.append(row);
        }
        if(status==='ready'&&!list.children.length)list.append(node('p',real&&real.staffRole!=='OWNER'?'Geen eigen werkuren op deze dag.':'Geen werkuren gepland op deze dag.'));
    }
    function open(shift=null){
        if(!manage()||busy)return;editing=shift;const form=$('coach-shift-form');form.reset();form.elements.coachId.replaceChildren(...coaches.map(c=>new Option(c.name,c.id)));
        form.elements.coachId.value=shift?.coachId||coaches[0]?.id||'';form.elements.date.min=today();form.elements.date.value=shift?.date||selected;
        let hour=9;if(!shift&&selected===today()){hour=Number(now().slice(0,2))+1;if(hour>23){const next=new Date(selected+'T12:00Z');next.setUTCDate(next.getUTCDate()+1);form.elements.date.value=next.toISOString().slice(0,10);hour=9;}}
        form.elements.start.value=shift?.start||String(hour).padStart(2,'0')+':00';form.elements.end.value=shift?.end||(hour>=23?'23:59':String(hour+1).padStart(2,'0')+':00');form.elements.note.value=shift?.note||'';
        $('coach-shift-title').textContent=shift?'Werkuren bewerken':'Werkuren plannen';$('coach-shift-error').textContent='';dialog.showModal();
    }
    function busyState(value){busy=value;quickAdd.disabled=value;dialog.querySelectorAll('button,input,select,textarea').forEach(el=>el.disabled=value);panel.querySelectorAll('button').forEach(el=>el.disabled=value);}
    async function remove(shift){
        if(!manage()||busy||!confirm(`Werkuren van ${shift.coach} op ${window.capDateTime.date(shift.date)} (${shift.start} – ${shift.end}) verwijderen?`))return;
        busyState(true);
        try{if(real)await real.request(`/api/coach-shifts/${shift.id}?version=${shift.version}`,'DELETE');else{demo=demo.filter(s=>s.id!==shift.id);window.capStorage.write('coach-shifts-preview',demo);}await load(from,through,true);}
        catch(e){error=e.message;status='error';changed();}finally{busyState(false);renderDay(selected);}
    }
    $('coach-shift-form').onsubmit=async event=>{
        event.preventDefault();if(!manage()||busy)return;const form=event.currentTarget;
        const input=Object.fromEntries(new FormData(form));input.version=editing?.version??-1;
        if(input.end<=input.start){$('coach-shift-error').textContent='Einde moet na de start liggen.';return;}
        if(!real&&demo.some(s=>s.id!==editing?.id&&s.coachId===input.coachId&&s.date===input.date&&s.start<input.end&&s.end>input.start)){$('coach-shift-error').textContent='Deze coach heeft al overlappende werkuren.';return;}
        busyState(true);
        try{
            const id=editing?.id||crypto.randomUUID();
            if(real)await real.request('/api/coach-shifts/'+id,'PUT',input);else{demo=demo.filter(s=>s.id!==id);demo.push({...input,id,coach:coaches.find(c=>c.id===input.coachId).name,version:input.version+1});window.capStorage.write('coach-shifts-preview',demo);}
            dialog.close();await load(from,through,true);document.dispatchEvent(new CustomEvent('cap:coach-shift-saved',{detail:{date:input.date}}));
        }catch(e){$('coach-shift-error').textContent=e.message;}finally{busyState(false);renderDay(selected);}
    };
    $('coach-shifts-add').onclick=()=>open();$('coach-shift-close').onclick=()=>dialog.close();dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    $('coach-shifts-retry').onclick=()=>load(from,through,true);
    document.addEventListener('cap:viewchange',()=>{quickAdd.hidden=!manage();if(location.hash!=='#planning'){generation++;key=null;if(!busy)dialog.close();}else key=null;});
    document.addEventListener('visibilitychange',()=>{if(!document.hidden&&location.hash==='#planning'&&from&&through)load(from,through,true);});
    setInterval(()=>{if(!document.hidden&&location.hash==='#planning'&&allowed()&&!busy&&from&&through)load(from,through,true);},60000);
    window.capCoachShifts={load,renderDay,allowed,onDate:day=>allowed()?items.filter(s=>s.date===day):[]};
})();
