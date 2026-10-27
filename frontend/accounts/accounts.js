// Real sessions use same-origin HttpOnly cookies; never the preview role or storage.
(() => {
    const $ = id => document.getElementById(id);
    const base = '/api/accounts';
    const parameters = new URLSearchParams(location.hash.slice(1));
    let invitationToken = parameters.get('invite');
    let member = null;
    let busy = false;
    if (invitationToken) history.replaceState(null, '', location.pathname);

    function message(text) { $('account-status').textContent = text; }
    function clearInvitation() {
        $('invitation-link').value = '';
        $('invitation-recipient').textContent = '';
        $('invitation-result').hidden = true;
    }
    function showMember(account) {
        member = account;
        $('login-panel').hidden = Boolean(account) || Boolean(invitationToken);
        $('accept-panel').hidden = Boolean(account) || !invitationToken;
        $('member-panel').hidden = !account;
        $('coach-invitations').hidden = account?.role !== 'COACH';
        $('member-identity').textContent = account ? `${account.name} · ${account.email} · ${account.role === 'COACH' ? 'Coach' : 'Sporter'}` : '';
        clearInvitation();
    }
    async function request(path, options = {}) {
        let response;
        try {
            response = await fetch(base + path, { ...options, credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(15000) });
        } catch {
            throw new Error('Geen verbinding met CAP. Controleer je verbinding en probeer opnieuw.');
        }
        if (response.status === 401) {
            if (member) { showMember(null); throw new Error('Je sessie is verlopen. Log opnieuw in.'); }
            if (path === '/me') return null;
            throw new Error('Inloggen mislukt. Controleer je e-mailadres en wachtwoord.');
        }
        if (!response.ok) {
            let result = {};
            try { result = await response.json(); } catch { /* Fall back to a Dutch message. */ }
            throw new Error(result.message || (response.status === 403
                ? 'Deze actie is niet toegestaan of je sessie is verlopen. Vernieuw de pagina en probeer opnieuw.'
                : 'De aanvraag is niet gelukt. Probeer opnieuw.'));
        }
        return response.status === 204 ? null : response.json();
    }
    async function post(path, body, form = false) {
        // A new token is needed after login/logout because Spring rotates the session.
        const csrf = await request('/csrf');
        return request(path, {
            method: 'POST',
            headers: { [csrf.headerName]: csrf.token, 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json' },
            body: form ? new URLSearchParams(body) : JSON.stringify(body)
        });
    }
    async function run(action) {
        if (busy) return;
        busy = true;
        document.querySelectorAll('button').forEach(button => { button.disabled = true; });
        message('Even geduld…');
        try { await action(); }
        catch (error) { message(error.message); $('account-status').focus(); }
        finally {
            busy = false;
            document.querySelectorAll('button').forEach(button => { button.disabled = false; });
        }
    }
    $('real-login').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        run(async () => {
            try {
                await post('/login', { email: form.elements.email.value.trim(), password: form.elements.password.value }, true);
                showMember(await request('/me'));
                message('Je bent ingelogd.');
                $('member-title').focus();
            } finally { form.elements.password.value = ''; }
        });
    });
    $('accept-invitation').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        run(async () => {
            const password = form.elements.password.value;
            if (password !== form.elements.confirmation.value) throw new Error('De wachtwoorden komen niet overeen.');
            if (password.length < 12 || new TextEncoder().encode(password).length > 72)
                throw new Error('Gebruik minstens 12 tekens. Je wachtwoord mag maximaal 72 UTF-8 bytes lang zijn; speciale tekens kunnen meer ruimte innemen.');
            const account = await post('/accept', { token: invitationToken, name: form.elements.name.value.trim(), password });
            invitationToken = null;
            form.reset();
            showMember(null);
            $('real-login').elements.email.value = account.email;
            message('Je account is aangemaakt. Log in met je eigen wachtwoord.');
            $('real-login').elements.password.focus();
        });
    });
    $('create-invitation').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        run(async () => {
            clearInvitation();
            const email = form.elements.email.value.trim();
            const invitation = await post('/invitations', { email });
            const link = new URL('index.html', location.href);
            link.hash = new URLSearchParams({ invite: invitation.token }).toString();
            $('invitation-link').value = link.href;
            $('invitation-recipient').textContent = `Uitnodiging voor ${email}`;
            $('invitation-result').hidden = false;
            message('De uitnodigingslink is klaar. Deel hem alleen met de juiste sporter.');
            $('invitation-link').focus();
        });
    });
    $('copy-invitation').addEventListener('click', async () => {
        const link = $('invitation-link');
        try { await navigator.clipboard.writeText(link.value); message('Uitnodigingslink gekopieerd.'); }
        catch { link.focus(); link.select(); message('Kopieer de geselecteerde link met de kopieerfunctie van je apparaat.'); }
    });
    $('real-logout').addEventListener('click', () => run(async () => {
        await post('/logout', {});
        invitationToken = null;
        document.querySelectorAll('form').forEach(form => form.reset());
        showMember(null);
        message('Je bent uitgelogd.');
        $('real-login').elements.email.focus();
    }));
    async function refreshSession() {
        if (busy) return;
        await run(async () => {
            if (invitationToken && !/^[0-9a-f]{64}$/.test(invitationToken)) {
                invitationToken = null;
                showMember(null);
                throw new Error('Deze uitnodigingslink is ongeldig. Vraag de coach om een nieuwe link.');
            }
            const account = await request('/me');
            showMember(account);
            message(account ? 'Je bent ingelogd.' : invitationToken ? 'Kies je naam en wachtwoord om je uitnodiging te accepteren.' : '');
            if (account && invitationToken) message('Je bent al ingelogd. Log eerst uit en open daarna de uitnodigingslink opnieuw voor het andere account.');
        });
    }
    window.addEventListener('pageshow', refreshSession);
    window.addEventListener('hashchange', () => {
        invitationToken = new URLSearchParams(location.hash.slice(1)).get('invite');
        if (invitationToken) history.replaceState(null, '', location.pathname);
        refreshSession();
    });
})();
