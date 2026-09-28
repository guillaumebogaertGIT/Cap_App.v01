// First frontend/API connection. Other app data is still local preview data.
const connectionPanel = document.createElement('section');
connectionPanel.className = 'settings-card';
connectionPanel.innerHTML = `
    <h3>Backendverbinding</h3>
    <p id="backend-status" role="status">Verbinding controleren…</p>
    <p>Workouts en toewijzingen gebruiken Java-opslag. Accounts blijven een preview; planning, boekingen en shop gebruiken browseropslag.</p>
    <button type="button" class="secondary-button" id="check-backend">Opnieuw controleren</button>`;
document.querySelector('#settings').append(connectionPanel);

async function checkBackend() {
    const status = document.querySelector('#backend-status');
    const button = document.querySelector('#check-backend');
    button.disabled = true;
    status.textContent = 'Verbinding controleren…';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
        const response = await fetch('http://localhost:8080/api/health', {
            signal: controller.signal,
            cache: 'no-store'
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        if (data.status !== 'ok') throw new Error('Unexpected health response');
        status.textContent = 'Backend verbonden';
    } catch {
        status.textContent = 'Geen verbinding. Controleer of Spring Boot draait en open de app via http://127.0.0.1:5500.';
    } finally {
        clearTimeout(timeout);
        button.disabled = false;
    }
}
document.querySelector('#check-backend').addEventListener('click', checkBackend);
checkBackend();
