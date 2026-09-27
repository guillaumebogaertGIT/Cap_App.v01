// Account navigation preview; no authentication or sensitive account data yet.
(() => {
    const menu = document.querySelector('.account-menu');
    const toggle = document.querySelector('#account-toggle');
    const links = document.querySelector('#account-links');
    const dialog = document.querySelector('#account-dialog');
    const previews = {
        profile: ['Profiel', 'Guillaume is het voorbeeldaccount. Hier kun je later je naam, profielfoto en persoonlijke gegevens beheren.'],
        notifications: ['Meldingen', 'Hier verschijnen later meldingen over jouw trainingen en boekingen. Meldingen zijn nog niet gekoppeld.'],
        password: ['Wachtwoord', 'Een wachtwoord wijzigen wordt beschikbaar zodra echte accounts zijn aangesloten.'],
        messages: ['Berichten', 'Hier komt je inbox voor berichten van de club en coaches. Berichten versturen is nog niet beschikbaar.'],
        chat: ['Chat', 'Hier kun je later rechtstreeks met jouw coach of sporters chatten. Chat is nog niet aangesloten.'],
        payment: ['Betalingen', 'Hier komen je betalingen en facturen. Er worden in deze preview geen betalingen verwerkt.'],
        logout: ['Uitloggen', 'Je gebruikt een voorbeeldaccount. Er is nog geen echte inlogsessie om af te sluiten.']
    };
    function closeMenu(returnFocus = false) {
        links.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        if (returnFocus) toggle.focus();
    }
    toggle.addEventListener('click', () => {
        const open = links.hidden;
        links.hidden = !open;
        toggle.setAttribute('aria-expanded', String(open));
    });
    menu.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') { closeMenu(true); event.stopPropagation(); }
        if (event.key === 'ArrowDown' && event.target === toggle) {
            event.preventDefault();
            links.hidden = false;
            toggle.setAttribute('aria-expanded', 'true');
            links.querySelector('button').focus();
        }
    });
    menu.addEventListener('focusout', (event) => {
        if (!menu.contains(event.relatedTarget)) closeMenu();
    });
    document.addEventListener('pointerdown', (event) => {
        if (!menu.contains(event.target)) closeMenu();
    });
    links.querySelector('a').addEventListener('click', () => {
        closeMenu();
        if (window.location.hash === '#settings') document.querySelector('#settings-title').focus();
    });
    links.querySelectorAll('[data-account]').forEach((button) => {
        button.addEventListener('click', () => {
            if (button.dataset.account === 'logout' && window.capSignOut) {
                closeMenu();
                window.capSignOut();
                return;
            }
            const [title, description] = previews[button.dataset.account];
            document.querySelector('#account-dialog-title').textContent = title;
            document.querySelector('#account-dialog-description').textContent = description;
            closeMenu();
            dialog.showModal();
        });
    });
    document.querySelector('#account-dialog-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => toggle.focus());
    window.addEventListener('hashchange', () => closeMenu());
})();
