// Loaded only by the authenticated original app. Preview screens keep their own data.
(() => {
    const icon = document.createElement('link'); icon.rel = 'icon'; icon.href = '/app/img/Cap-logo.png'; document.head.append(icon);
    const data = JSON.parse(document.getElementById('cap-account-data').textContent);
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, options = {}) => {
        const url = new URL(typeof input === 'string' ? input : input.url, location.href);
        if (['localhost', '127.0.0.1'].includes(url.hostname) && url.port === '8080'
            && url.pathname.startsWith('/api/')) input = url.pathname + url.search;
        const target = new URL(typeof input === 'string' ? input : input.url, location.href);
        if (target.origin !== location.origin || !target.pathname.startsWith('/api/'))
            return nativeFetch(input, options);
        const headers = new Headers(options.headers);
        if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
            const tokenResponse = await nativeFetch('/api/accounts/csrf', { cache: 'no-store' });
            if (!tokenResponse.ok) throw new Error('Vernieuw de pagina en log opnieuw in.');
            const csrf = await tokenResponse.json();
            headers.set(csrf.headerName, csrf.token);
        }
        const response = await nativeFetch(input, { ...options, headers, credentials: 'same-origin' });
        if (response.status === 401) location.replace('/accounts/index.html');
        return response;
    };
    async function request(path, method = 'GET', body) {
        const response = await fetch(path, { method, cache: 'no-store',
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Opslaan mislukt. Vernieuw en probeer opnieuw.');
        }
        return response.status === 204 ? null : response.json();
    }
    window.capReal = { ...data, request,
        athletes: data.people.filter(person => person.role === 'ATHLETE'),
        coaches: data.people.filter(person => person.role === 'COACH') };
})();
