// Apply the saved theme before the page paints to avoid a flash of the wrong colors.
(() => {
    const storageKey = 'cap-theme';
    const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
    let preference = 'system';

    try {
        const saved = localStorage.getItem(storageKey);
        if (['light', 'dark', 'system'].includes(saved)) preference = saved;
    } catch {
        // The theme still works when browser storage is unavailable.
    }

    function applyTheme() {
        document.documentElement.dataset.theme = preference === 'system'
            ? (systemTheme.matches ? 'dark' : 'light') : preference;
    }

    applyTheme();
    systemTheme.addEventListener('change', applyTheme);

    document.addEventListener('DOMContentLoaded', () => {
        const status = document.querySelector('#theme-status');
        document.querySelectorAll('input[name="theme"]').forEach((input) => {
            input.checked = input.value === preference;
            input.addEventListener('change', () => {
                preference = input.value;
                applyTheme();
                try {
                    localStorage.setItem(storageKey, preference);
                    status.textContent = 'Je voorkeur is bewaard in deze browser.';
                } catch {
                    status.textContent = 'Je weergave is aangepast, maar deze browser kan je voorkeur niet bewaren.';
                }
            });
        });
    });
})();
