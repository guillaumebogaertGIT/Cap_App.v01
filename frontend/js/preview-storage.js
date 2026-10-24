// Browser-local preview data; credentials are never stored here.
window.capStorage = {
    read(key, fallback) {
        try { return JSON.parse(localStorage.getItem(`cap-${key}-v1`)) ?? fallback; }
        catch { return fallback; }
    },
    write(key, value) {
        try { localStorage.setItem(`cap-${key}-v1`, JSON.stringify(value)); }
        catch {
            let notice = document.getElementById('storage-error');
            if (!notice) {
                notice = document.createElement('p'); notice.id = 'storage-error';
                notice.setAttribute('role', 'alert'); document.querySelector('main').prepend(notice);
            }
            notice.textContent = 'Bewaren in deze browser is mislukt. Houd deze pagina open en controleer je browseropslag.';
        }
    },
    map(key) {
        const saved = this.read(key, []);
        const map = new Map(Array.isArray(saved) ? saved.filter(entry => Array.isArray(entry) && entry.length === 2) : []);
        map.set = (id, value) => {
            Map.prototype.set.call(map, id, value);
            this.write(key, [...map]);
            return map;
        };
        return map;
    }
};
