// Display Belgian dates/times consistently; storage and API values remain ISO.
(() => {
    const timeZone = 'Europe/Brussels';
    const dateFormat = new Intl.DateTimeFormat('nl-BE', { timeZone, day: '2-digit', month: '2-digit', year: 'numeric' });
    const timeFormat = new Intl.DateTimeFormat('nl-BE', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    const weekdayFormat = new Intl.DateTimeFormat('nl-BE', { timeZone, weekday: 'long' });
    const instant = value => /^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? new Date(value + 'T12:00:00Z') : new Date(value);
    function date(value) {
        if (!value) return '';
        const parsed = instant(value);
        if (!Number.isFinite(parsed.getTime())) return '';
        const parts = Object.fromEntries(dateFormat.formatToParts(parsed).map(part => [part.type, part.value]));
        return `${parts.day}/${parts.month}/${parts.year}`;
    }
    function time(value) {
        const parsed = instant(value);
        return Number.isFinite(parsed.getTime()) ? timeFormat.format(parsed) : '';
    }
    function parseDate(value) {
        let text = value.trim();
        if (/^\d{8}$/.test(text)) text = `${text.slice(0, 2)}/${text.slice(2, 4)}/${text.slice(4)}`;
        const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
        if (!match) return '';
        const iso = `${match[3]}-${match[2]}-${match[1]}`;
        const parsed = new Date(iso + 'T12:00:00Z');
        return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === iso ? iso : '';
    }
    function parseTime(value) {
        let text = value.trim();
        if (/^\d{4}$/.test(text)) text = text.slice(0, 2) + ':' + text.slice(2);
        return /^([01]\d|2[0-3]):[0-5]\d$/.test(text) ? text : '';
    }
    window.capDateTime = {
        timeZone, date, time, parseDate, parseTime,
        dateTime: value => value ? `${date(value)} · ${time(value)}` : '',
        dayLabel: value => value ? `${weekdayFormat.format(instant(value))} ${date(value)}` : ''
    };
})();
