document.addEventListener('DOMContentLoaded', () => {
    const real=window.capReal;
    if (!real) return;
    const dialog=document.createElement('dialog');
    dialog.className='club-dialog'; dialog.setAttribute('aria-labelledby','password-title');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="password-title">Wachtwoord wijzigen</h2>
        <button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten" data-close-password><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <form class="club-form">
        <label>Huidig wachtwoord<input type="password" name="current" autocomplete="current-password" required maxlength="1000"></label>
        <label>Nieuw wachtwoord<input type="password" name="next" autocomplete="new-password" required minlength="12" maxlength="72" aria-describedby="password-help"></label>
        <label>Nieuw wachtwoord herhalen<input type="password" name="repeat" autocomplete="new-password" required minlength="12" maxlength="72"></label>
        <p id="password-help" class="sample-notice">Minstens 12 tekens, maximaal 72 UTF-8 bytes. Na het wijzigen word je op alle apparaten uitgelogd.</p>
        <p role="status" aria-live="polite"></p>
        <button type="submit" class="primary-button">Wachtwoord wijzigen</button>
        <button type="button" class="secondary-button" data-login-after-change hidden>Opnieuw inloggen</button>
        </form>`;
    document.body.append(dialog);
    const form=dialog.querySelector('form'), status=dialog.querySelector('[role="status"]');
    const login=dialog.querySelector('[data-login-after-change]'), submit=form.querySelector('[type="submit"]');
    let busy=false,changed=false;
    window.capOpenPassword=()=>{form.reset();status.textContent='';changed=false;submit.hidden=false;login.hidden=true;dialog.showModal();};
    const goLogin=()=>location.assign('/accounts/index.html');
    dialog.querySelector('[data-close-password]').addEventListener('click',()=>{if(!busy) dialog.close();});
    dialog.addEventListener('cancel',event=>{if(busy) event.preventDefault();});
    dialog.addEventListener('close',()=>{form.reset();if(changed) goLogin();else document.querySelector('#account-toggle')?.focus();});
    login.addEventListener('click',goLogin);
    form.addEventListener('submit',async event=>{
        event.preventDefault(); if(busy || changed) return;
        const next=form.elements.next.value;
        if(next!==form.elements.repeat.value){status.textContent='De nieuwe wachtwoorden komen niet overeen.';return;}
        if(new TextEncoder().encode(next).length>72){status.textContent='Het nieuwe wachtwoord is te lang. Gebruik maximaal 72 UTF-8 bytes.';return;}
        busy=true;submit.disabled=true;status.textContent='Wachtwoord wijzigen…';
        try {
            await real.request('/api/accounts/password','POST',{currentPassword:form.elements.current.value,newPassword:next});
            changed=true;form.reset();submit.hidden=true;login.hidden=false;
            status.textContent='Je wachtwoord is gewijzigd. Log opnieuw in met je nieuwe wachtwoord.';
            login.focus();
        } catch(error){status.textContent=error.message;}
        finally {busy=false;submit.disabled=false;}
    });
});
