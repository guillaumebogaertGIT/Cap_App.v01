// Explicit design simulation. Never store credentials or treat this as authorization.
window.capAccess = {
    signedIn: false,
    plan: 'none',
    plans: {
        none: { label: 'Geen abonnement', workouts: false, group: false, private: false, openGym: false },
        group: { label: 'Groepslessen (voorbeeld)', workouts: false, group: true, private: false, openGym: false },
        complete: { label: 'Uitgebreid (voorbeeld)', workouts: true, group: true, private: true, openGym: true }
    },
    can(feature) {
        // Staff access comes from the coach role, never a paid athlete plan.
        return this.signedIn && (previewRole === 'coach' || this.plans[this.plan][feature] === true);
    }
};
document.addEventListener('DOMContentLoaded', () => {
    const access = window.capAccess;
    document.body.insertAdjacentHTML('afterbegin', `
        <section id="auth-screen" class="auth-screen" aria-labelledby="auth-title">
            <div class="auth-story"><img class="auth-cap-logo" src="img/Cap-logo.png" alt="CAP"><span class="card-label">Complete Athletic Program</span><h1><img class="auth-wordmark" src="img/Stronger-Together.png" alt="Stronger together"><span>Elke dag opnieuw.</span></h1><p>Jouw trainingen, jouw club en jouw vooruitgang. Allemaal op één plek.</p><span class="status-label">Ontwerpvoorbeeld · Geen echte accounts</span></div>
            <div class="auth-card" id="signup-card">
                <div class="auth-tabs"><button type="button" id="show-login" class="secondary-button" aria-pressed="true">Inloggen</button><button type="button" id="show-signup" class="secondary-button" aria-pressed="false">Registreren</button></div>
                <h2 id="auth-title" tabindex="-1">Welkom bij CAP</h2>
                <p>Test deze schermen met fictieve gegevens. Je invoer wordt niet opgeslagen of verzonden. Er wordt geen account aangemaakt.</p>
                <form id="auth-form" class="club-form">
                    <label id="signup-name" hidden>Naam<input name="name" autocomplete="off" maxlength="80" disabled></label>
                    <label>E-mailadres<input name="email" type="email" required autocomplete="off" placeholder="naam@example.com"></label>
                    <label>Wachtwoord<input name="password" type="password" required minlength="8" autocomplete="off" aria-describedby="password-help"></label>
                    <p id="password-help">Gebruik een fictief wachtwoord van minimaal 8 tekens.</p>
                    <div id="signup-agreements" hidden>
                        <label class="auth-check"><input name="terms" type="checkbox" disabled> Ik ga in deze demo akkoord met de conceptvoorwaarden.</label>
                        <button class="auth-text-button" type="button" data-legal="terms">Lees de conceptvoorwaarden</button>
                        <p>Lees hoe gegevens in deze preview worden behandeld: <button class="auth-text-button" type="button" data-legal="privacy">Privacyverklaring (concept)</button>.</p>
                        <label class="auth-check"><input name="marketing" type="checkbox" disabled> Ik wil nieuws en aanbiedingen ontvangen (optioneel, alleen voorbeeld).</label>
                    </div>
                    <button class="primary-button" id="auth-submit" type="submit">Inloggen simuleren</button>
                </form>
                <button type="button" class="auth-text-button" id="forgot-password">Wachtwoord vergeten?</button>
                <p id="auth-status" role="status"></p>
                <button type="button" class="secondary-button" id="open-demo">Open ontwerp-preview zonder gegevens</button>
                <p class="sample-notice">De demo opent als voorbeeldsporter Guillaume, zonder abonnement. Naam en e-mail zijn alleen bedoeld om het formulier te testen.</p>
                <div class="auth-tabs"><button type="button" class="auth-text-button" data-legal="terms">Voorwaarden</button><button type="button" class="auth-text-button" data-legal="privacy">Privacy</button></div>
            </div>
            <div class="auth-card" id="profile-card" hidden>
                <h2 id="profile-title" tabindex="-1">Vertel iets over jezelf</h2>
                <p>Een korte start voor jouw begeleiding. Alles is optioneel.</p>
                <form id="profile-form" class="club-form">
                    <div class="profile-fields">
                        <label>Geboortedatum<input name="birthDate" type="date" min="1900-01-01"></label>
                        <label>Geslacht<select name="sex"><option value="">Zeg ik liever niet</option><option value="female">Vrouw</option><option value="male">Man</option><option value="other">Anders</option></select></label>
                        <label>Lengte (cm)<input name="heightCm" type="number" min="50" max="300" step="0.1" inputmode="decimal" placeholder="Bijv. 175"></label>
                        <label>Gewicht (kg)<input name="weightKg" type="number" min="20" max="500" step="0.1" inputmode="decimal" placeholder="Bijv. 75"></label>
                        <label>Trainingsdoel<select name="goal"><option value="">Kies je doel</option><option value="strength">Sterker worden</option><option value="fitness">Conditie verbeteren</option><option value="weight-loss">Afvallen</option><option value="health">Algemene gezondheid</option></select></label>
                        <label>Ervaring<select name="experience"><option value="">Kies je niveau</option><option value="beginner">Beginner</option><option value="intermediate">Gemiddeld</option><option value="advanced">Ervaren</option></select></label>
                    </div>
                    <p class="sample-notice">Ontwerpvoorbeeld: gebruik fictieve gegevens. Je profiel blijft alleen tijdens deze preview beschikbaar via Profiel. Bij vernieuwen of uitloggen wordt het gewist; er wordt niets verzonden. Geslacht kan later helpen bij voedingsberekeningen.</p>
                    <button type="submit" class="primary-button">Verder naar CAP</button>
                    <button type="button" class="secondary-button" id="skip-profile">Later invullen</button>
                    <button type="button" class="auth-text-button" id="back-to-signup">Terug naar registratie</button>
                </form>
            </div>
        </section>
        <dialog id="legal-dialog" class="club-dialog" aria-labelledby="legal-title"><h2 id="legal-title"></h2><div id="legal-content"></div><button type="button" class="secondary-button" id="close-legal">Sluiten</button></dialog>`);
    const $ = (id) => document.getElementById(id);
    const form = $('auth-form');
    const profileForm = $('profile-form');
    const profileValues = {};
    const weightHistory = [];
    const bodyMeasurements = [];
    const profileChanged = () => document.dispatchEvent(new CustomEvent('cap:profile-changed'));
    window.capProfile = {
        get() { return { ...profileValues }; },
        weights() { return weightHistory.map(entry => ({ ...entry })); },
        measurements() { return bodyMeasurements.map(entry => ({ ...entry })); },
        logMeasurement(values) {
            if (!access.signedIn) throw new Error('Open eerst de preview.');
            const entry = { recordedAt: new Date().toISOString() };
            let count = 0;
            for (const metric of window.capBodyMetrics) {
                const raw = values[metric.key];
                if (raw == null || String(raw).trim() === '') continue;
                const value = Number(raw);
                if (!Number.isFinite(value) || value < (metric.key === 'weightKg' ? 20 : 0) || value > metric.max) {
                    throw new Error(`Controleer ${metric.label.toLowerCase()}.`);
                }
                entry[metric.key] = value; count++;
            }
            if (!count) throw new Error('Vul minstens één meetwaarde in.');
            bodyMeasurements.unshift(entry);
            if (entry.weightKg != null) this.logWeight(entry.weightKg);
            else profileChanged();
        },
        logWeight(value) {
            const kg = Number(value);
            if (!access.signedIn || !Number.isFinite(kg) || kg < 20 || kg > 500) throw new Error('Vul een gewicht tussen 20 en 500 kg in.');
            const recordedAt = new Date().toISOString();
            weightHistory.unshift({ kg, recordedAt });
            profileValues.weightKg = String(kg);
            profileChanged();
        }
    };
    profileForm.elements.namedItem('birthDate').max = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const profileDialog = document.createElement('dialog');
    profileDialog.className = 'club-dialog';
    profileDialog.id = 'edit-profile-dialog';
    profileDialog.setAttribute('aria-labelledby', 'edit-profile-title');
    const profileHeading = document.createElement('h2');
    profileHeading.id = 'edit-profile-title'; profileHeading.textContent = 'Profiel';
    const editProfileForm = profileForm.cloneNode(true);
    editProfileForm.id = 'edit-profile-form';
    editProfileForm.querySelectorAll('button').forEach(button => button.remove());
    const profileStatus = document.createElement('p');
    profileStatus.setAttribute('role', 'status');
    const saveProfile = document.createElement('button');
    saveProfile.type = 'submit'; saveProfile.className = 'primary-button'; saveProfile.textContent = 'Profiel bewaren';
    const closeProfile = document.createElement('button');
    closeProfile.type = 'button'; closeProfile.className = 'secondary-button'; closeProfile.textContent = 'Sluiten';
    closeProfile.addEventListener('click', () => profileDialog.close());
    editProfileForm.append(profileStatus, saveProfile, closeProfile);
    profileDialog.append(profileHeading, editProfileForm);
    document.body.append(profileDialog);
    function retainProfile(source) {
        const previousWeight = profileValues.weightKg;
        Object.assign(profileValues, Object.fromEntries(new FormData(source)));
        if (profileValues.weightKg && profileValues.weightKg !== previousWeight) {
            weightHistory.unshift({ kg: Number(profileValues.weightKg), recordedAt: new Date().toISOString() });
        }
        profileChanged();
    }
    window.capOpenProfile = () => {
        if (!access.signedIn) return;
        editProfileForm.reset();
        Object.entries(profileValues).forEach(([name, value]) => { editProfileForm.elements.namedItem(name).value = value; });
        profileStatus.textContent = '';
        profileDialog.showModal();
    };
    editProfileForm.addEventListener('submit', event => {
        event.preventDefault();
        if (!editProfileForm.reportValidity()) return;
        retainProfile(editProfileForm);
        profileStatus.textContent = 'Profiel bijgewerkt voor deze preview. Bij vernieuwen of uitloggen worden deze gegevens gewist.';
    });
    profileDialog.addEventListener('close', () => {
        editProfileForm.reset();
        document.querySelector('#account-toggle').focus();
    });
    let signup = false;
    function mode(register) {
        signup = register;
        $('signup-name').hidden = !register;
        $('signup-agreements').hidden = !register;
        form.elements.name.disabled = !register;
        form.elements.name.required = register;
        form.elements.terms.disabled = !register;
        form.elements.terms.required = register;
        form.elements.marketing.disabled = !register;
        $('show-login').setAttribute('aria-pressed', String(!register));
        $('show-signup').setAttribute('aria-pressed', String(register));
        $('auth-title').textContent = register ? 'Maak jouw start bij CAP' : 'Welkom terug bij CAP';
        $('auth-submit').textContent = register ? 'Registratie simuleren' : 'Inloggen simuleren';
        $('auth-status').textContent = '';
        $('auth-title').focus();
    }
    const controls = document.createElement('div');
    controls.className = 'access-preview';
    controls.innerHTML = `<label>Abonnement testen<select id="preview-plan"></select></label><p id="access-status" role="status"></p><p>Ontwerptest: rollen en rechten zijn hier vrij te wisselen. Dit zijn geen echte toegangscontroles of definitieve pakketten.</p>`;
    document.querySelector('#preview-controls').append(controls);
    Object.entries(access.plans).forEach(([key, plan]) => $('preview-plan').add(new Option(plan.label, key)));
    function updateWorkoutLinkLabels() {
        const label = access.can('workouts') ? 'Workout maken' : 'Workout maken · abonnement nodig';
        document.querySelectorAll('a[href="#create-workout"]').forEach((link) => {
            // Change only the label, preserving tile captions and navigation icons.
            const labelElement = link.querySelector('strong, span');
            if (labelElement) {
                labelElement.textContent = label;
            } else {
                const textNode = Array.from(link.childNodes).find(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim());
                if (textNode) textNode.textContent = ` ${label}`;
            }
        });
    }
    function updateAccess() {
        $('access-status').textContent = access.plan === 'none' ? 'Geen abonnement: workouts maken en sessies boeken zijn vergrendeld.' : `${access.plans[access.plan].label} actief in deze preview.`;
        updateWorkoutLinkLabels();
        document.dispatchEvent(new CustomEvent('cap:viewchange'));
    }
    function enter() {
        profileForm.reset();
        $('profile-card').hidden = true;
        $('signup-card').hidden = false;
        access.signedIn = true;
        access.plan = 'none';
        $('preview-plan').value = 'none';
        previewRole = 'athlete';
        document.querySelectorAll('[name="preview-role"]').forEach((input) => { input.checked = input.value === 'athlete'; });
        document.body.classList.remove('is-signed-out');
        $('auth-screen').hidden = true;
        form.reset();
        window.location.hash = 'dashboard';
        showCurrentView();
        updateAccess();
    }
    window.capSignOut = () => {
        Object.keys(profileValues).forEach(key => delete profileValues[key]);
        weightHistory.length = 0;
        bodyMeasurements.length = 0;
        profileChanged();
        document.querySelectorAll('dialog[open]').forEach((dialog) => dialog.close());
        access.signedIn = false;
        access.plan = 'none';
        document.body.classList.add('is-signed-out');
        $('auth-screen').hidden = false;
        form.reset(); mode(false);
        $('auth-status').textContent = 'Preview afgesloten. Voorbeeldgegevens van de club blijven in deze browser bewaard.';
    };
    $('preview-plan').addEventListener('change', (event) => { access.plan = event.target.value; updateAccess(); });
    $('show-login').addEventListener('click', () => mode(false));
    $('show-signup').addEventListener('click', () => mode(true));
    $('open-demo').addEventListener('click', enter);
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (signup && !form.elements.name.value.trim()) { $('auth-status').textContent = 'Vul een naam in.'; return; }
        if (!form.reportValidity()) return;
        if (signup) {
            form.reset();
            $('signup-card').hidden = true;
            $('profile-card').hidden = false;
            $('profile-title').focus();
        } else enter();
    });
    profileForm.addEventListener('submit', event => {
        event.preventDefault();
        if (profileForm.reportValidity()) { retainProfile(profileForm); enter(); }
    });
    $('skip-profile').addEventListener('click', enter);
    $('back-to-signup').addEventListener('click', () => {
        profileForm.reset();
        $('profile-card').hidden = true;
        $('signup-card').hidden = false;
        mode(true);
    });
    $('forgot-password').addEventListener('click', () => { $('auth-status').textContent = 'Wachtwoordherstel is nog niet aangesloten. Er wordt geen e-mail verstuurd.'; });
    const drafts = {
        terms: ['Gebruiksvoorwaarden — concept', '<p><strong>Werkdocument voor bespreking met CAP. Niet vastgesteld en niet geschikt voor echte registraties.</strong></p><h3>Voorgestelde werking</h3><p>Registratie geeft een sporteraccount. Coachrechten worden apart toegekend. Een account geeft niet automatisch toegang tot betaalde trainingen of groepslessen; daarvoor gelden de rechten van het gekozen abonnement.</p><h3>Nog vast te leggen met CAP</h3><ul><li>Juridische bedrijfsnaam, adres, ondernemingsnummer en contactgegevens.</li><li>Pakketten, prijzen, looptijd, verlenging, opzegging en toepasselijke consumentenrechten.</li><li>Boekings-, annulerings-, terugbetalings- en afwezigheidsregels.</li><li>Veilig gebruik, minderjarigen, verantwoordelijkheden en klachtenprocedure.</li><li>Toepasselijk recht, versienummer en ingangsdatum na juridische beoordeling.</li></ul><p>Deze preview sluit geen abonnement af en verwerkt geen betaling. Vóór lancering zijn definitieve documenten en een serverregistratie van de geaccepteerde voorwaardenversie nodig.</p>'],
        privacy: ['Privacyverklaring — concept', '<p><strong>Werkdocument. CAP moet dit vóór lancering laten aanvullen en beoordelen.</strong></p><h3>Deze preview</h3><p>Naam, e-mail, wachtwoord en selectievakjes uit het aanmeldformulier worden niet door de app opgeslagen of verzonden. De app bewaart de themakeuze en voorbeeldlessen, boekingen en producten lokaal in deze browser. Gebruik geen echte persoonsgegevens.</p><h3>Voor de echte app nog vast te leggen</h3><ul><li>Wie verantwoordelijk is en hoe je die organisatie bereikt.</li><li>Welke gegevens voor welk doel worden verwerkt, met de juiste rechtsgrond.</li><li>Hosting, betaal- en andere dienstverleners, ontvangers en eventuele internationale doorgiften.</li><li>Bewaartermijnen, beveiliging en verwerking van eventuele gezondheidsgegevens.</li><li>Rechten op inzage, correctie, verwijdering en overige toepasselijke rechten; contact en klachten bij de toezichthouder.</li><li>Omgang met minderjarigen en optionele marketing.</li></ul><p>Marketing is een aparte, niet vooraf aangevinkte keuze. Deze demo legt geen echte toestemming vast.</p>']
    };
    document.querySelectorAll('[data-legal]').forEach((button) => button.addEventListener('click', () => {
        const [title, content] = drafts[button.dataset.legal];
        $('legal-title').textContent = title; $('legal-content').innerHTML = content; $('legal-dialog').showModal();
    }));
    $('close-legal').addEventListener('click', () => $('legal-dialog').close());
    // Prevent accessing the editor through direct links as well as its buttons.
    document.addEventListener('cap:viewchange', () => {
        controls.hidden = previewRole === 'coach';
        updateWorkoutLinkLabels();
        const editor = document.querySelector('#coach-workout-form');
        editor.hidden = !access.can('workouts');
        let notice = $('workout-access-note');
        if (!notice) { notice = document.createElement('p'); notice.id = 'workout-access-note'; notice.className = 'club-empty'; editor.before(notice); }
        notice.hidden = access.can('workouts');
        notice.textContent = 'Voor workouts maken is een geschikt abonnement nodig. Test dit met het voorbeeldpakket Uitgebreid bovenaan.';
    });
    updateAccess();
});
