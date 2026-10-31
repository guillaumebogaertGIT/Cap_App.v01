// Runs before the original deferred modules read the roster and form options.
(() => {
    const real = window.capReal;
    const athleteSelect = document.querySelector('[name="athlete"]');
    athleteSelect.replaceChildren(...real.athletes.map(person => new Option(person.name, person.id)));
    const groups = document.querySelector('#athletes .membership-groups');
    real.selectedAthleteId = real.athletes[0]?.id || '';
    const original = groups.querySelector('.athlete-card');
    original.hidden = true; groups.before(original);
    const group = document.createElement('details'); group.className = 'membership-group'; group.open = true;
    const heading = document.createElement('summary'); heading.textContent = `Sporters (${real.athletes.length})`;
    const list = document.createElement('ul'); list.className = 'membership-athletes';
    function renderAthletes() {
        heading.textContent = `Sporters (${real.athletes.length})`;
        list.replaceChildren();
        if (!real.athletes.some(person => person.id === real.selectedAthleteId)) real.selectedAthleteId = real.athletes[0]?.id || '';
        for (const select of document.querySelectorAll('select[name="athlete"], select[name="athleteId"]')) {
            const selected = select.value;
            select.replaceChildren(...real.athletes.map(person => new Option(person.name, person.id)));
            if (real.athletes.some(person => person.id === selected)) select.value = selected;
            select.dispatchEvent(new Event('change', { bubbles: true }));
        }
        for (const person of real.athletes) {
            const item = document.createElement('li'); item.className = 'athlete-card'; item.dataset.athleteId = person.id;
            const name = document.createElement('h3');
            const open=document.createElement('button');open.type='button';open.className='athlete-directory-open';open.textContent=person.name;
            open.setAttribute('aria-label','Dossier van '+person.name+' bekijken');name.append(open);
            open.addEventListener('click',()=>{real.selectedAthleteId=person.id;document.dispatchEvent(new CustomEvent('cap:open-athlete',{detail:{id:person.id,opener:open}}));});
            const identity = document.createElement('div'); identity.append(name);
            item.classList.add('athlete-directory-entry');item.append(identity);
            list.append(item);
        }
    }
    renderAthletes();
    document.addEventListener('cap:athletes-changed', renderAthletes);
    const refresh = () => {if(window.capCan('WORKOUT_ASSIGN'))real.refreshAthletes().catch(error => { invite.querySelector('[role="status"]').textContent = error.message; });};
    window.addEventListener('focus', refresh);
    document.addEventListener('cap:viewchange', () => {
        if (['#athletes', '#create-workout', '#saved-workouts'].includes(location.hash)) refresh();
    });
    group.append(heading, list); groups.replaceChildren(group);
    const invite = document.createElement('form'); invite.className = 'club-form settings-card';
    invite.innerHTML = '<h3>Sporter uitnodigen</h3><label>E-mailadres<input name="email" type="email" required autocomplete="email" maxlength="254"></label><button class="primary-button" type="submit">Uitnodigingslink maken</button><p role="status"></p><label hidden>Uitnodigingslink<input name="link" readonly></label>';
    groups.before(invite);
    invite.hidden=!window.capCan('CLIENT_SCOPE_MANAGE');
    invite.addEventListener('submit', async event => {
        event.preventDefault(); const button = invite.querySelector('button'); button.disabled = true;
        const status = invite.querySelector('[role="status"]');
        try {
            const result = await real.request('/api/accounts/invitations', 'POST', { email: invite.elements.email.value.trim() });
            const link = new URL('/accounts/index.html', location.origin); link.hash = new URLSearchParams({ invite: result.token });
            invite.elements.link.value = link.href; invite.elements.link.closest('label').hidden = false;
            status.textContent = 'Deel deze link met de sporter. Die kiest zelf een wachtwoord.';
            invite.elements.link.focus(); invite.elements.link.select();
        } catch (error) { status.textContent = error.message; } finally { button.disabled = false; }
    });
})();
