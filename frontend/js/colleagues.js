document.addEventListener('DOMContentLoaded', () => {
    const real=window.capReal, page=document.getElementById('colleagues');
    const list=page.querySelector('ul'), status=page.querySelector('[role="status"]'), refresh=page.querySelector('button');
    if(real?.account.role!=='COACH') {
        status.textContent='Het collega-overzicht is beschikbaar wanneer je met een coachaccount inlogt.';
        refresh.hidden=true; return;
    }
    const dialog=document.createElement('dialog'); dialog.className='club-dialog';dialog.setAttribute('aria-labelledby','colleague-name');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="colleague-name">Collega</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <p id="colleague-title"></p><p role="status" aria-live="polite"></p><dl class="colleague-contact" hidden></dl>
        <button type="button" class="secondary-button" data-contact-retry hidden>Opnieuw proberen</button>`;
    document.body.append(dialog);
    const feedback=dialog.querySelector('[role="status"]'),details=dialog.querySelector('dl'),retry=dialog.querySelector('[data-contact-retry]');
    let generation=0,detailGeneration=0,selected,opener;
    async function contact() {
        const version=++detailGeneration, id=selected.id;
        feedback.textContent='Contactgegevens laden…';details.hidden=true;details.replaceChildren();retry.hidden=true;
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
        } catch(error) { if(version===detailGeneration && dialog.open){feedback.textContent=error.message;retry.hidden=false;} }
    }
    function open(member,button) {
        selected=member;opener=button;dialog.querySelector('h2').textContent=member.name;
        dialog.querySelector('#colleague-title').textContent=member.title;dialog.showModal();contact();
    }
    async function load() {
        const version=++generation;list.replaceChildren();status.textContent='Collega’s laden…';refresh.disabled=true;
        try {
            const members=await real.request('/api/coaches');if(version!==generation || location.hash!=='#colleagues') return;
            for(const member of members) {
                const item=document.createElement('li'),button=document.createElement('button'),name=document.createElement('strong'),title=document.createElement('span');
                button.type='button';button.className='colleague-button';name.textContent=member.name+(member.id===real.account.id?' (jij)':'');title.textContent=member.title;
                button.append(name,title);button.addEventListener('click',()=>open(member,button));item.append(button);list.append(item);
            }
            status.textContent=members.length?'Kies een collega om contactgegevens te bekijken.':'Er zijn nog geen coachaccounts.';
        } catch(error){if(version===generation)status.textContent=error.message+' Kies Opnieuw laden om opnieuw te proberen.';}
        finally{if(version===generation)refresh.disabled=false;}
    }
    dialog.querySelector('.session-dialog-close').addEventListener('click',()=>dialog.close());
    dialog.addEventListener('close',()=>{++detailGeneration;details.replaceChildren();details.hidden=true;opener?.isConnected&&opener.focus();});
    retry.addEventListener('click',contact);refresh.addEventListener('click',load);
    document.addEventListener('cap:viewchange',()=>{if(location.hash==='#colleagues')load();else{++generation;refresh.disabled=false;if(dialog.open)dialog.close();}});
    if(location.hash==='#colleagues')load();
});
