document.addEventListener('DOMContentLoaded', () => {
    const real=window.capReal, page=document.getElementById('colleagues');
    const list=page.querySelector('ul'), status=page.querySelector('[role="status"]'), refresh=page.querySelector('#colleagues-refresh');
    if(real?.account.role!=='COACH') {
        status.textContent='Het collega-overzicht is beschikbaar wanneer je met een coachaccount inlogt.';
        refresh.hidden=true; return;
    }
    const dialog=document.createElement('dialog'); dialog.className='club-dialog';dialog.setAttribute('aria-labelledby','colleague-name');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="colleague-name">Collega</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p id="colleague-title"></p><p role="status" aria-live="polite"></p><dl class="colleague-contact" hidden></dl>
        <button type="button" class="secondary-button" data-contact-retry hidden>Opnieuw proberen</button>
        <div class="colleagues-actions" data-staff-actions hidden><p class="sample-notice" data-staff-note></p><button type="button" class="secondary-button" data-toggle-coach></button></div>`;
    document.body.append(dialog);
    const feedback=dialog.querySelector('[role="status"]'),details=dialog.querySelector('dl'),retry=dialog.querySelector('[data-contact-retry]');
    const manage=!!real.permissions?.manageCoaches, actions=dialog.querySelector('[data-staff-actions]'),toggle=dialog.querySelector('[data-toggle-coach]');
    let generation=0,detailGeneration=0,selected,opener,selectedActive=true,changing=false;
    async function contact() {
        const version=++detailGeneration, id=selected.id;
        feedback.textContent='Contactgegevens laden…';details.hidden=true;details.replaceChildren();retry.hidden=true;actions.hidden=true;
        try {
            const value=await real.request('/api/coaches/'+encodeURIComponent(id));
            if(version!==detailGeneration || !dialog.open) return;
            if(value.id!==id) throw new Error('De contactgegevens passen niet bij deze collega. Probeer opnieuw.');
            dialog.querySelector('h2').textContent=value.name;dialog.querySelector('#colleague-title').textContent=value.title;
            for(const [label,text,url] of [['E-mailadres',value.email,'mailto:'+encodeURIComponent(value.email)],
                ['Telefoonnummer',value.phone||'Nog niet toegevoegd',value.phone?'tel:'+value.phone.replace(/[^+0-9]/g,''):'']]) {
                const term=document.createElement('dt'),description=document.createElement('dd');term.textContent=label;
                if(url){const link=document.createElement('a');link.href=url;link.textContent=text;description.append(link);}else description.textContent=text;
                details.append(term,description);
            }
            feedback.textContent='';details.hidden=false;
            selectedActive=value.active;
            if(manage){
                actions.hidden=false;toggle.hidden=value.id===real.account.id;
                toggle.textContent=value.active?'Coach deactiveren':'Opnieuw activeren';
                dialog.querySelector('[data-staff-note]').textContent=value.id===real.account.id?'Je kunt je eigen account niet deactiveren.':value.active?'Deactiveren stopt de toegang. Lessen en geschiedenis blijven bewaard.':'Dit account is gedeactiveerd. Opnieuw activeren behoudt het huidige wachtwoord.';
            }
        } catch(error) { if(version===detailGeneration && dialog.open){feedback.textContent=error.message;retry.hidden=false;} }
    }
    function open(member,button) {
        if(changing)return;
        selected=member;opener=button;dialog.querySelector('h2').textContent=member.name;
        dialog.querySelector('#colleague-title').textContent=member.title;dialog.showModal();contact();
    }
    async function load() {
        const version=++generation;list.replaceChildren();status.textContent='Collega’s laden…';refresh.disabled=true;
        try {
            const members=await real.request('/api/coaches');if(version!==generation || location.hash!=='#colleagues') return;
            real.coaches.splice(0,real.coaches.length,...members.filter(member=>member.active).map(member=>({id:member.id,name:member.name,role:'COACH'})));
            for(const member of members) {
                const item=document.createElement('li'),button=document.createElement('button'),name=document.createElement('strong'),title=document.createElement('span');
                button.type='button';button.className='colleague-button';name.textContent=member.name+(member.id===real.account.id?' (jij)':'');title.textContent=member.title;
                if(!member.active)title.textContent+=' · Inactief';
                button.append(name,title);button.addEventListener('click',()=>open(member,button));item.append(button);list.append(item);
            }
            status.textContent=members.length?'Kies een collega om contactgegevens te bekijken.':'Er zijn nog geen coachaccounts.';
            return true;
        } catch(error){if(version===generation)status.textContent=error.message+' Kies Opnieuw laden om opnieuw te proberen.';return false;}
        finally{if(version===generation)refresh.disabled=false;}
    }
    dialog.querySelector('.session-dialog-close').addEventListener('click',()=>{if(!changing)dialog.close();});
    dialog.addEventListener('cancel',event=>{if(changing)event.preventDefault();});
    dialog.addEventListener('close',()=>{++detailGeneration;details.replaceChildren();details.hidden=true;opener?.isConnected&&opener.focus();});
    retry.addEventListener('click',contact);refresh.addEventListener('click',load);
    toggle.addEventListener('click',async()=>{
        if(changing || !manage || selected.id===real.account.id) return;
        const id=selected.id,active=!selectedActive;
        if(!window.confirm(active?'Deze coach opnieuw toegang geven?':'Deze coach deactiveren? De toegang stopt; lessen en geschiedenis blijven bewaard.'))return;
        changing=true;toggle.disabled=true;feedback.textContent='Coachaccount bijwerken…';
        try {
            await real.request('/api/coaches/'+encodeURIComponent(id)+'/active','PUT',{active});
            dialog.close();const refreshed=await load();
            status.textContent=(active?'Coach opnieuw geactiveerd.':'Coach gedeactiveerd. De toegang is beëindigd; lessen en geschiedenis blijven bewaard.')+(refreshed?'':' Het overzicht kon niet worden vernieuwd. Kies Opnieuw laden.');
        }catch(error){feedback.textContent=error.message;}
        finally{changing=false;toggle.disabled=false;}
    });
    if(manage){
        const add=page.querySelector('#coach-add');add.hidden=false;
        const editor=document.createElement('dialog');editor.className='club-dialog';editor.setAttribute('aria-labelledby','coach-add-title');
        editor.innerHTML=`<div class="session-dialog-header"><h2 id="coach-add-title">Coach toevoegen</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
            <form class="club-form"><label>Naam<input name="name" required maxlength="100" autocomplete="name"></label>
            <label>E-mailadres<input name="email" type="email" required maxlength="254" autocomplete="email"></label>
            <label>Titel<select name="title"><option>Trainer</option><option>Assistent · Trainer</option><option>Manager · Trainer</option><option>Standaard · Trainer</option><option>Coach</option></select></label>
            <label>Tijdelijk wachtwoord<input name="password" type="password" autocomplete="new-password" required minlength="12" maxlength="72"></label>
            <p class="sample-notice">Minstens 12 tekens, maximaal 72 UTF-8 bytes. Deel het wachtwoord persoonlijk. De coach kan het wijzigen via Wachtwoord. De titel geeft geen extra beheerrechten.</p>
            <p role="status" aria-live="polite"></p><button type="submit" class="primary-button">Coachaccount maken</button></form>`;
        document.body.append(editor);const form=editor.querySelector('form'),message=editor.querySelector('[role="status"]'),submit=form.querySelector('button');let saving=false;
        add.addEventListener('click',()=>{form.reset();message.textContent='';editor.showModal();});
        editor.querySelector('.session-dialog-close').addEventListener('click',()=>{if(!saving)editor.close();});
        editor.addEventListener('cancel',event=>{if(saving)event.preventDefault();});
        editor.addEventListener('close',()=>{form.reset();add.focus();});
        document.addEventListener('cap:viewchange',()=>{if(location.hash!=='#colleagues' && editor.open && !saving)editor.close();});
        form.addEventListener('submit',async event=>{
            event.preventDefault();if(saving)return;
            if(new TextEncoder().encode(form.elements.password.value).length>72){message.textContent='Gebruik maximaal 72 UTF-8 bytes voor het wachtwoord.';return;}
            saving=true;submit.disabled=true;message.textContent='Coachaccount maken…';
            try {
                await real.request('/api/coaches','POST',Object.fromEntries(new FormData(form)));
                editor.close();const refreshed=await load();status.textContent='Coachaccount gemaakt. Deel het gekozen tijdelijke wachtwoord persoonlijk. De coach kan het daarna wijzigen.'+(refreshed?'':' Het overzicht kon niet worden vernieuwd. Kies Opnieuw laden.');
            }catch(error){message.textContent=error.message;}
            finally{saving=false;submit.disabled=false;}
        });
    }
    document.addEventListener('cap:viewchange',()=>{if(location.hash==='#colleagues')load();else{++generation;refresh.disabled=false;if(dialog.open)dialog.close();}});
    if(location.hash==='#colleagues')load();
});
