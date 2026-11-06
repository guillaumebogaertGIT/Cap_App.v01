(() => {
    const token=location.hash.startsWith('#reset=')?decodeURIComponent(location.hash.slice(7)):null;
    if(token)history.replaceState(null,'',location.pathname+location.search);
    const dialog=document.createElement('dialog');dialog.className='club-dialog';dialog.setAttribute('aria-labelledby','recovery-title');
    dialog.innerHTML='<div class="session-dialog-header"><h2 id="recovery-title">Wachtwoord herstellen</h2><button type="button" class="secondary-button" aria-label="Sluiten">×</button></div><form class="club-form"><label data-email>E-mailadres<input name="email" type="email" required autocomplete="email" maxlength="254"></label><label data-new hidden>Nieuw wachtwoord<input name="password" type="password" minlength="12" autocomplete="new-password"></label><label data-confirm hidden>Herhaal wachtwoord<input name="confirmation" type="password" autocomplete="new-password"></label><p role="status"></p><button type="submit" class="primary-button">Herstellink aanvragen</button></form>';
    document.body.append(dialog);const form=dialog.querySelector('form'),status=dialog.querySelector('[role=status]'),submit=form.querySelector('button');let enabled=false,busy=false;
    dialog.querySelector('[aria-label=Sluiten]').onclick=()=>dialog.close();dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    async function open(){
        dialog.showModal();status.textContent='Beschikbaarheid controleren…';
        try{const response=await fetch('/api/accounts/recovery',{cache:'no-store'});if(!response.ok)throw Error('Herstelservice niet bereikbaar.');enabled=(await response.json()).enabled;status.textContent=enabled?'Je ontvangt een link als er een actief account bij dit adres hoort.':'E-mailherstel is nog niet ingesteld. Neem contact op met CAP.';submit.disabled=!enabled;}
        catch(error){status.textContent=error.message;submit.disabled=true;}
    }
    if(token){dialog.querySelector('[data-email]').hidden=true;form.elements.email.required=false;for(const attr of ['data-new','data-confirm'])dialog.querySelector('['+attr+']').hidden=false;form.elements.password.required=true;form.elements.confirmation.required=true;submit.textContent='Nieuw wachtwoord bewaren';}
    document.getElementById('recovery-open').onclick=open;
    form.onsubmit=async event=>{event.preventDefault();if(busy||!enabled)return;if(token&&form.elements.password.value!==form.elements.confirmation.value){status.textContent='De wachtwoorden komen niet overeen.';return;}busy=true;submit.disabled=true;
        try{const csrf=await (await fetch('/api/accounts/csrf',{cache:'no-store'})).json();const response=await fetch('/api/accounts/recovery'+(token?'/confirm':''),{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(token?{token,password:form.elements.password.value}:{email:form.elements.email.value})});if(!response.ok){const error=await response.json().catch(()=>({}));throw Error(error.message||'Herstellen mislukt. De link kan ongeldig of verlopen zijn.');}form.reset();status.textContent=token?'Je wachtwoord is gewijzigd. Sluit dit venster en log opnieuw in.':'Als er een actief account bestaat, ontvang je een herstellink. Controleer ook je spammap.';if(token)enabled=false;}
        catch(error){status.textContent=error.message;}finally{busy=false;submit.disabled=!enabled;}
    };
    if(token)open();
})();
