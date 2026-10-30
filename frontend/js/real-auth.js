// Real authorization is enforced by Spring Security; this object only drives the UI.
window.capAccess = { signedIn: true, plan: 'complete', can() { return this.signedIn; } };
let accountProfileValues = {};
window.capProfile = { get: () => ({ ...accountProfileValues }), weights: () => [], measurements: () => [],
    logWeight() { throw new Error('Profielopslag is nog niet aangesloten.'); },
    logMeasurement() { throw new Error('Profielopslag is nog niet aangesloten.'); } };
window.capSignOut = async () => {
    try { await window.capReal.request('/api/accounts/logout', 'POST'); location.replace('/accounts/index.html'); }
    catch (error) { alert(error.message); }
};
document.addEventListener('DOMContentLoaded', () => {
    document.body.classList.remove('is-signed-out');
    document.querySelector('.preview-tools').remove();
    const identity = document.querySelectorAll('#account-toggle span');
    identity[0].textContent = window.capReal.account.name.slice(0,1);
    identity[1].textContent = window.capReal.account.name;
    document.querySelector('.account-preview-label').textContent = window.capReal.account.email;
    document.getElementById('welcome-name').textContent = window.capReal.account.name;
    mountAccountProfile();
    showCurrentView(false);
});

function mountAccountProfile() {
    const real = window.capReal;
    const dialog = document.createElement('dialog');
    dialog.className = 'club-dialog'; dialog.setAttribute('aria-labelledby', 'edit-profile-title');
    dialog.innerHTML = `<div class="session-dialog-header"><h2 id="edit-profile-title">Profiel</h2><button type="button" class="secondary-button session-dialog-close" data-close-profile aria-label="Sluiten" title="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <form id="edit-profile-form" class="club-form">
            <div class="profile-fields">
                <label>Naam<input name="name" required maxlength="100" autocomplete="name"></label>
                <label>E-mailadres<input name="email" type="email" readonly></label>
                <label${real.account.role === 'COACH' ? '' : ' hidden'}>Telefoonnummer (optioneel)<input name="phone" type="tel" maxlength="40" autocomplete="tel"><small>Dit nummer is zichtbaar voor je collega-coaches.</small></label>
                <label>Geboortedatum<input name="birthDate" type="date" min="1900-01-01"></label>
                <label>Geslacht<select name="sex"><option value="">Zeg ik liever niet</option><option value="female">Vrouw</option><option value="male">Man</option><option value="other">Anders</option></select></label>
                <label>Lengte (cm)<input name="heightCm" type="number" min="50" max="300" step="0.1" inputmode="decimal"></label>
                <label>Gewicht (kg)<input name="weightKg" type="number" min="20" max="500" step="0.1" inputmode="decimal"></label>
                <label>Trainingsdoel<select name="goal"><option value="">Kies je doel</option><option value="strength">Sterker worden</option><option value="fitness">Conditie verbeteren</option><option value="weight-loss">Afvallen</option><option value="health">Algemene gezondheid</option></select></label>
                <label>Ervaring<select name="experience"><option value="">Kies je niveau</option><option value="beginner">Beginner</option><option value="intermediate">Gemiddeld</option><option value="advanced">Ervaren</option></select></label>
            </div>
            <img class="account-profile-photo" alt="Jouw profielfoto" hidden width="96" height="96">
            <label>Profielfoto<input name="photo" type="file" accept="image/*" aria-describedby="profile-photo-help"></label>
            <p id="profile-photo-help" class="sample-notice">Kies een foto tot 8 MB. CAP bewaart een kleine versie voor je profiel.</p>
            <button type="button" class="secondary-button" data-remove-photo hidden>Foto verwijderen</button>
            <p role="status" aria-live="polite"></p>
            <div class="editor-actions"><button type="submit" class="primary-button">Profiel bewaren</button><button type="button" class="secondary-button" data-close-profile>Sluiten</button></div>
        </form>`;
    document.body.append(dialog);
    const form = dialog.querySelector('form');
    form.elements.birthDate.max = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Brussels' }).format(new Date());
    const status = form.querySelector('[role="status"]');
    const preview = form.querySelector('img');
    const remove = form.querySelector('[data-remove-photo]');
    let hasPhoto = false;
    let photoData = null;
    let deletePhoto = false;
    let busy = false;
    let photoVersion = Date.now();
    let photoSelection = 0;
    function updateIdentity() {
        const name = accountProfileValues.name || real.account.name;
        real.account.name = name;
        document.getElementById('welcome-name').textContent = name;
        const spans = document.querySelectorAll('#account-toggle span');
        spans[1].textContent = name;
        spans[0].replaceChildren();
        if (hasPhoto) {
            const image = document.createElement('img'); image.alt = ''; image.src = `/api/accounts/profile/photo?v=${photoVersion}`;
            spans[0].append(image);
        } else spans[0].textContent = name.slice(0, 1);
    }
    function apply(profile) {
        accountProfileValues = profile.values; hasPhoto = profile.hasPhoto;
        updateIdentity();
        document.dispatchEvent(new CustomEvent('cap:profile-changed'));
    }
    async function load() { apply(await real.request('/api/accounts/profile')); }
    window.capOpenProfile = async () => {
        if (busy || dialog.open) return;
        dialog.showModal(); status.textContent = 'Profiel laden…';
        const controls = [...form.querySelectorAll('input,select,button[type="submit"]')];
        controls.forEach(control => { control.disabled = true; });
        try {
            await load();
            form.reset();
            for (const [key, value] of Object.entries(accountProfileValues)) form.elements.namedItem(key).value = value ?? '';
            form.elements.email.value = real.account.email;
            photoData = null; deletePhoto = false; photoSelection++;
            preview.hidden = !hasPhoto; remove.hidden = !hasPhoto;
            if (hasPhoto) preview.src = `/api/accounts/profile/photo?v=${photoVersion}`;
            status.textContent = '';
        } catch (error) { status.textContent = error.message; }
        finally { controls.forEach(control => { control.disabled = false; }); }
    };
    form.elements.photo.addEventListener('change', async () => {
        const selection = ++photoSelection;
        const file = form.elements.photo.files[0];
        if (!file) return;
        busy = true; form.elements.photo.disabled = true; remove.disabled = true;
        form.querySelector('[type="submit"]').disabled = true;
        status.textContent = 'Foto verwerken…';
        try {
            if (file.size > 8 * 1024 * 1024) throw new Error('Kies een foto van maximaal 8 MB.');
            const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
            const canvas = document.createElement('canvas'); canvas.width = 256; canvas.height = 256;
            const context = canvas.getContext('2d');
            context.fillStyle = '#ffffff'; context.fillRect(0, 0, 256, 256);
            const side = Math.min(bitmap.width, bitmap.height);
            context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, 256, 256);
            bitmap.close();
            if (selection !== photoSelection) return;
            photoData = canvas.toDataURL('image/jpeg', 0.82); deletePhoto = false;
            preview.src = photoData; preview.hidden = false; remove.hidden = false;
            status.textContent = 'Foto klaar. Kies Profiel bewaren om op te slaan.';
        } catch (error) {
            if (selection === photoSelection) { form.elements.photo.value = ''; status.textContent = error.message || 'Kies een leesbare foto.'; }
        } finally {
            busy = false; form.elements.photo.disabled = false; remove.disabled = false;
            form.querySelector('[type="submit"]').disabled = false;
        }
    });
    remove.addEventListener('click', () => {
        photoSelection++; photoData = null; deletePhoto = true;
        form.elements.photo.value = ''; preview.hidden = true; remove.hidden = true;
        status.textContent = 'Kies Profiel bewaren om de foto te verwijderen.';
    });
    form.addEventListener('submit', async event => {
        event.preventDefault(); if (busy || !form.reportValidity()) return;
        busy = true; status.textContent = 'Profiel bewaren…';
        const buttons = [...form.querySelectorAll('button')]; buttons.forEach(button => { button.disabled = true; });
        let profileSaved = false;
        try {
            const values = Object.fromEntries(new FormData(form)); delete values.photo; delete values.email;
            for (const key of ['heightCm', 'weightKg']) values[key] = values[key] === '' ? null : Number(values[key]);
            apply(await real.request('/api/accounts/profile', 'PUT', values)); profileSaved = true;
            if (photoData) { await real.request('/api/accounts/profile/photo', 'PUT', { image: photoData }); hasPhoto = true; }
            else if (deletePhoto) { await real.request('/api/accounts/profile/photo', 'DELETE'); hasPhoto = false; }
            photoData = null; deletePhoto = false; photoVersion = Date.now(); updateIdentity();
            status.textContent = 'Profiel bewaard in je account.';
        } catch (error) { status.textContent = (profileSaved ? 'Profielgegevens bewaard; de foto is niet bijgewerkt. ' : '') + error.message; }
        finally { busy = false; buttons.forEach(button => { button.disabled = false; }); }
    });
    dialog.querySelectorAll('[data-close-profile]').forEach(button => button.addEventListener('click', () => { if (!busy) dialog.close(); }));
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    dialog.addEventListener('close', () => document.getElementById('account-toggle').focus());
    load().catch(() => { /* Opening Profile shows a retryable connection error. */ });
}
