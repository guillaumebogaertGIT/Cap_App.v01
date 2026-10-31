// Extend the existing shell. All grants and client scope are also enforced by Spring.
(() => {
    const real=window.capReal;
    if(!real || real.account.role!=='COACH')return;
    function applyControls(){
        if(!window.capCan('WORKOUT_TEMPLATE_MANAGE'))document.querySelectorAll('a[href="#create-workout"]').forEach(link=>{link.hidden=true;});
        if(!window.capCan('CLASS_MANAGE')){const add=document.getElementById('new-session');if(add)add.hidden=true;}
        if(real.staffRole==='INSTRUCTOR'){
            const notice=document.querySelector('#coach .sample-notice');if(notice)notice.textContent='Lesgever · Jouw lessen, gekoppelde sporters en beschikbare workouts.';
            const title=document.getElementById('planning-title');if(title)title.textContent='Mijn uurrooster / Lessen';
            document.querySelector('#coach-title')?.replaceChildren(document.createTextNode('Mijn coaching'));
        }
        if(real.staffRole==='DEVELOPMENT')document.querySelector('#coach-title')?.replaceChildren(document.createTextNode('Ontwikkelaccount · geen toegang tot cliëntgegevens'));
    }
    document.addEventListener('DOMContentLoaded',applyControls);document.addEventListener('cap:viewchange',applyControls);
    const node=(tag,text)=>{const e=document.createElement(tag);if(text!=null)e.textContent=text;return e;};
    function screen(id,title,permission){
        if(!window.capCan(permission))return null;
        const page=node('section');page.id=id;page.dataset.view='';page.hidden=true;
        const heading=node('h2',title);heading.id=id+'-title';heading.tabIndex=-1;page.setAttribute('aria-labelledby',heading.id);page.append(heading);
        document.querySelector('main').append(page);
        return page;
    }
    function select(label,options){const wrap=node('label',label),input=node('select');for(const [value,text] of options)input.add(new Option(text,value));wrap.append(input);return {wrap,input};}
    const notes=screen('staff-notes','Notities','NOTE_VIEW');
    if(notes){
        notes.append(node('p','Privé voor bevoegde medewerkers. Sporters zien deze notities niet.'));
        const card=node('article');card.className='club-card';
        const client=select('Sporter',real.athletes.map(p=>[p.id,p.name]));
        const assignment=select('Workouttoewijzing (optioneel)',[['','Algemene cliëntnotitie']]);
        const form=node('form');form.className='club-form';
        const label=node('label','Notitie'),text=node('textarea');text.required=true;text.maxLength=4000;text.rows=4;label.append(text);
        const submit=node('button','Notitie bewaren');submit.type='submit';submit.className='primary-button';
        const status=node('p');status.setAttribute('role','status');status.setAttribute('aria-live','polite');
        const list=node('div');list.className='staff-note-list';const reload=node('button','Opnieuw laden');reload.type='button';reload.className='secondary-button';
        form.append(assignment.wrap,label,submit);card.append(client.wrap,form,status,reload,list);notes.append(card);
        let generation=0,busy=false,requestId=crypto.randomUUID(),requestedAssignment=null;
        async function load(){const version=++generation,id=client.input.value;list.replaceChildren();assignment.input.replaceChildren(new Option('Algemene cliëntnotitie',''));
            form.hidden=!id;reload.disabled=!id;
            if(!id){status.textContent='Er zijn nog geen sporters aan jou gekoppeld.';return;}
            status.textContent='Notities laden…';
            try{const [values,assignments]=await Promise.all([real.request('/api/staff-notes?clientId='+encodeURIComponent(id)),real.request('/api/workout-assignments')]);
                if(version!==generation)return;
                assignments.filter(a=>a.planned.athleteId===id).forEach(a=>assignment.input.add(new Option(a.planned.name+' · '+(a.planned.assignedDate||a.weekStart||'Zonder datum'),a.assignmentId)));
                if(requestedAssignment){assignment.input.value=requestedAssignment;requestedAssignment=null;}
                for(const value of values){const entry=node('article');entry.className='saved-workout';entry.append(node('strong',value.author+' · '+window.capDateTime.dateTime(value.createdAt)),node('p',value.content));if(value.assignmentId)entry.append(node('small','Workouttoewijzing: '+value.assignmentId));list.append(entry);}
                status.textContent=values.length?values.length+' privénotities.':'Nog geen privénotities voor deze sporter.';
            }catch(error){if(version===generation)status.textContent=error.message;}}
        client.input.addEventListener('change',()=>{text.value='';requestId=crypto.randomUUID();load();});
        document.addEventListener('cap:athletes-changed',()=>{const selected=client.input.value;client.input.replaceChildren(...real.athletes.map(p=>new Option(p.name,p.id)));if(real.athletes.some(p=>p.id===selected))client.input.value=selected;else{text.value='';requestId=crypto.randomUUID();}if(location.hash==='#staff-notes')load();});
        text.addEventListener('input',()=>{requestId=crypto.randomUUID();});assignment.input.addEventListener('change',()=>{requestId=crypto.randomUUID();});
        reload.addEventListener('click',load);
        document.addEventListener('cap:note-client',event=>{if(!real.athletes.some(p=>p.id===event.detail.clientId))return;client.input.value=event.detail.clientId;text.value='';requestId=crypto.randomUUID();requestedAssignment=event.detail.assignmentId||null;load();});
        form.addEventListener('submit',async event=>{event.preventDefault();if(busy)return;busy=true;submit.disabled=true;client.input.disabled=true;assignment.input.disabled=true;text.disabled=true;
            const content=text.value,id=client.input.value;
            try{await real.request('/api/staff-notes','POST',{clientId:id,assignmentId:assignment.input.value||null,content,requestId});text.value='';requestId=crypto.randomUUID();await load();status.textContent='Privénotitie bewaard.';}
            catch(error){status.textContent=error.message;}finally{busy=false;submit.disabled=false;client.input.disabled=false;assignment.input.disabled=false;text.disabled=false;}});
        document.addEventListener('cap:viewchange',()=>{if(location.hash==='#staff-notes')real.refreshAthletes().then(load).catch(error=>{status.textContent=error.message;});else ++generation;});
        if(location.hash==='#staff-notes')load();
    }
})();
