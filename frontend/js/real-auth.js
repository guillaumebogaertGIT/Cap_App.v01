// Real authorization is enforced by Spring Security; this object only drives the UI.
window.capAccess = { signedIn: true, plan: 'complete', can() { return this.signedIn; } };
window.capProfile = { get: () => ({}), weights: () => [], measurements: () => [],
    logWeight() { throw new Error('Profielopslag is nog niet aangesloten.'); },
    logMeasurement() { throw new Error('Profielopslag is nog niet aangesloten.'); } };
window.capSignOut = async () => {
    try { await window.capReal.request('/api/accounts/logout', 'POST'); location.replace('/accounts/index.html'); }
    catch (error) { alert(error.message); }
};
window.capOpenProfile = () => {
    document.getElementById('account-dialog-title').textContent = window.capReal.account.name;
    document.getElementById('account-dialog-description').textContent = window.capReal.account.email;
    document.getElementById('account-dialog').showModal();
};
document.addEventListener('DOMContentLoaded', () => {
    document.body.classList.remove('is-signed-out');
    document.querySelector('.preview-tools').remove();
    const identity = document.querySelectorAll('#account-toggle span');
    identity[0].textContent = window.capReal.account.name.slice(0,1);
    identity[1].textContent = window.capReal.account.name;
    document.querySelector('.account-preview-label').textContent = window.capReal.account.email;
    document.getElementById('welcome-name').textContent = window.capReal.account.name;
    showCurrentView(false);
});
