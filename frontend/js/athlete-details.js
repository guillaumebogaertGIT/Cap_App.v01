// Athlete-first shortcuts into existing screens, with the existing client-access grants.
(() => {
    const real=window.capReal;
    if(!real || !window.capCan('WORKOUT_ASSIGN'))return;
    const node=(tag,text)=>{const value=document.createElement(tag);if(text!=null)value.textContent=text;return value;};
    const dialog=node('dialog');dialog.id='athlete-details-dialog';dialog.className='club-dialog';dialog.setAttribute('aria-labelledby','athlete-details-title');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="athlete-details-title">Sporterdossier</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p>Bekijk de trainingen, vooruitgang en begeleiding van deze sporter.</p><div class="athlete-details-actions"></div>`;
    document.body.append(dialog);
    let selectedId='',opener=null,generation=0,busy=false,restoreFocus=true;
    const actions=dialog.querySelector('.athlete-details-actions');
    function go(action){restoreFocus=false;dialog.close();real.selectedAthleteId=selectedId;action();}
    function action(label,callback,permission){if(!window.capCan(permission))return;const button=node('button',label);button.type='button';button.className='secondary-button';button.addEventListener('click',()=>{if(!busy)go(callback);});actions.append(button);}
    function selectPage(route){document.dispatchEvent(new CustomEvent('cap:select-athlete',{detail:{id:selectedId}}));location.hash=route;}
    action('Workouts bekijken',()=>document.getElementById('review-athlete-workouts').click(),'WORKOUT_VIEW');
    action('Workout toewijzen',()=>document.getElementById('assign-athlete-workout').click(),'WORKOUT_ASSIGN');
    action('Metingen, PR’s & trainingsgeschiedenis',()=>selectPage('coach-health'),'CLIENT_HEALTH');
    action('Voeding & dagdoelen',()=>selectPage('coach-nutrition'),'NUTRITION_MANAGE');
    action('Privénotities',()=>{document.dispatchEvent(new CustomEvent('cap:note-client',{detail:{clientId:selectedId}}));location.hash='staff-notes';},'NOTE_VIEW');
    action('Lidmaatschap & credits',()=>{const picker=document.getElementById('coach-membership-athlete');if(picker){picker.value=selectedId;picker.dispatchEvent(new Event('change'));picker.focus();picker.scrollIntoView({block:'center'});}},'FINANCE_VIEW');
    const section=node('section');section.className='athlete-coach-assignment';section.hidden=!window.capCan('CLIENT_SCOPE_MANAGE');
    const label=node('label','Coach'),select=node('select');select.id='athlete-coach-select';label.htmlFor=select.id;label.append(select);
    const form=node('form');form.className='club-form';const submit=node('button','Aan coach koppelen');submit.type='submit';submit.className='primary-button';form.append(label,submit);
    const status=node('p');status.setAttribute('role','status');status.setAttribute('aria-live','polite');
    const linked=node('ul');linked.className='athlete-linked-coaches';const retry=node('button','Coaches opnieuw laden');retry.type='button';retry.className='secondary-button';
    section.append(node('h3','Aan coach koppelen'),node('p','Admins hebben al toegang tot alle sporters. Koppel hier één of meerdere lesgevers.'),form,status,linked,retry);dialog.append(section);
    let members=[],grants=[];
    function controls(){select.disabled=busy||!members.some(m=>m.active&&m.role==='INSTRUCTOR');submit.disabled=busy||!select.value||grants.includes(select.value);retry.disabled=busy;}
    async function load(){const version=++generation,id=selectedId;submit.disabled=true;select.disabled=true;linked.replaceChildren();status.textContent='Coaches laden…';
        try{const [people,ids]=await Promise.all([real.request('/api/staff/members'),real.request('/api/staff/clients/'+id+'/instructors')]);if(version!==generation||!dialog.open)return;
            members=people;grants=ids;const previous=select.value;select.replaceChildren(new Option('Kies een coach',''),...members.filter(m=>m.active&&m.role==='INSTRUCTOR').map(m=>new Option(m.name,m.id)));if(members.some(m=>m.id===previous&&m.active&&m.role==='INSTRUCTOR'))select.value=previous;
            for(const coachId of grants){const coach=members.find(m=>m.id===coachId),item=node('li');item.append(node('span',(coach?.name||'Onbekende medewerker')+(coach?.active?'':' · niet actief')));
                const remove=node('button','Ontkoppelen');remove.type='button';remove.className='secondary-button';remove.setAttribute('aria-label',(coach?.name||'Coach')+' ontkoppelen');remove.disabled=busy;remove.addEventListener('click',()=>mutate(coachId,'DELETE'));item.append(remove);linked.append(item);}
            status.textContent=grants.length?grants.length+' gekoppelde coach(es).':'Nog geen coach gekoppeld.';controls();
        }catch(error){if(version===generation&&dialog.open){status.textContent=error.message;select.disabled=true;submit.disabled=true;retry.disabled=false;}}
    }
    async function mutate(coachId,method){if(busy||!selectedId)return;busy=true;controls();dialog.querySelectorAll('button').forEach(b=>{b.disabled=true;});status.textContent='Koppeling bewaren…';
        try{await real.request('/api/staff/'+coachId+'/clients/'+selectedId,method);await load();status.textContent=method==='PUT'?'Sporter aan coach gekoppeld. De coach ziet deze sporter bij Mijn klanten.':'Coach ontkoppeld. De toegang tot deze sporter is ingetrokken.';}
        catch(error){status.textContent=error.message;}
        finally{busy=false;dialog.querySelectorAll('button').forEach(b=>{b.disabled=false;});controls();}
    }
    form.addEventListener('submit',event=>{event.preventDefault();if(select.value)mutate(select.value,'PUT');});select.addEventListener('change',controls);retry.addEventListener('click',load);
    dialog.querySelector('.session-dialog-close').addEventListener('click',()=>{if(!busy)dialog.close();});dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    dialog.addEventListener('close',()=>{++generation;if(restoreFocus&&opener?.isConnected)opener.focus();});
    document.addEventListener('cap:open-athlete',event=>{const athlete=real.athletes.find(a=>a.id===event.detail.id);if(!athlete||busy)return;selectedId=athlete.id;opener=event.detail.opener;restoreFocus=true;dialog.querySelector('h2').textContent=athlete.name;dialog.showModal();if(!section.hidden)load();});
    document.addEventListener('cap:viewchange',()=>{if(dialog.open&&!busy&&location.hash!=='#athletes')dialog.close();});
})();
