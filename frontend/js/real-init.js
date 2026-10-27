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
    for (const person of real.athletes) {
        const item = document.createElement('li'); item.className = 'athlete-card'; item.dataset.athleteId = person.id;
        const name = document.createElement('h3'); name.textContent = person.name;
        const identity = document.createElement('div'); identity.append(name);
        const actions = document.createElement('div'); actions.className = 'athlete-actions';
        item.append(identity, actions);
        for (const [label, target] of [['Workouts bekijken', 'review-athlete-workouts'], ['Workout toewijzen', 'assign-athlete-workout']]) {
            const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary-button'; button.textContent = label;
            button.addEventListener('click', () => { real.selectedAthleteId = person.id; document.getElementById(target).click(); }); actions.append(button);
        }
        list.append(item);
    }
    group.append(heading, list); groups.replaceChildren(group);
    const invite = document.createElement('form'); invite.className = 'club-form settings-card';
    invite.innerHTML = '<h3>Sporter uitnodigen</h3><label>E-mailadres<input name="email" type="email" required autocomplete="email" maxlength="254"></label><button class="primary-button" type="submit">Uitnodigingslink maken</button><p role="status"></p><label hidden>Uitnodigingslink<input name="link" readonly></label>';
    groups.before(invite);
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
