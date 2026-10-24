// Health preview shares browser-local profile data; no Apple Health connection yet.
window.capBodyMetrics = [
    ['weightKg', 'Gewicht', 'kg', 500],
    ['bodyFatPercent', 'Vetpercentage', '%', 100],
    ['muscleKg', 'Spiermassa', 'kg', 500],
    ['boneKg', 'Botmassa', 'kg', 100],
    ['waterPercent', 'Lichaamswater', '%', 100],
    ['fatKg', 'Vetmassa', 'kg', 500],
    ['fatFreeKg', 'Vetvrije massa', 'kg', 500],
    ['musclePercent', 'Spierpercentage', '%', 100],
    ['waterKg', 'Totaal lichaamswater', 'kg', 500],
    ['visceralFat', 'Visceraal vet', 'niveau', 100],
    ['bmr', 'Ruststofwisseling (BMR)', 'kcal/dag', 10000],
    ['metabolicAge', 'Metabolische leeftijd', 'jaar', 150],
    ['bmi', 'BMI', 'kg/m²', 200]
].map(([key, label, unit, max]) => ({ key, label, unit, max }));
for (const [part, name] of [['rightArm', 'Rechterarm'], ['leftArm', 'Linkerarm'], ['trunk', 'Romp'], ['rightLeg', 'Rechterbeen'], ['leftLeg', 'Linkerbeen']]) {
    for (const [suffix, label, unit, max] of [['FatPercent', 'Vetpercentage', '%', 100], ['FatKg', 'Vetmassa', 'kg', 500], ['FatFreeKg', 'Vetvrije massa', 'kg', 500], ['MuscleKg', 'Spiermassa', 'kg', 500]]) {
        window.capBodyMetrics.push({ key: part + suffix, label: `${name} · ${label}`, unit, max, segment: true });
    }
}
document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const format = value => new Intl.NumberFormat('nl-BE', { maximumFractionDigits: 1 }).format(value);
    const metrics = window.capBodyMetrics;
    const measurementForm = $('body-measurement-form');
    metrics.forEach(metric => {
        const label = document.createElement('label'); label.textContent = `${metric.label} (${metric.unit})`;
        const input = document.createElement('input'); input.type = 'number'; input.name = metric.key;
        input.min = metric.key === 'weightKg' ? '20' : '0'; input.max = String(metric.max);
        input.step = ['bmr', 'metabolicAge'].includes(metric.key) ? '1' : '0.1';
        input.inputMode = 'decimal'; input.placeholder = 'Optioneel'; label.append(input);
        $(metric.segment ? 'body-segment-fields' : 'body-total-fields').append(label);
    });
    function displayMeasurement(entry, container) {
        const values = document.createElement('dl'); values.className = 'body-metric-values';
        metrics.filter(metric => entry[metric.key] != null).forEach(metric => {
            const group = document.createElement('div');
            const title = document.createElement('dt'); title.textContent = metric.label;
            const value = document.createElement('dd'); value.textContent = `${format(entry[metric.key])} ${metric.unit}`;
            group.append(title, value); values.append(group);
        });
        container.append(values);
    }
    function renderHealth() {
        const profile = window.capProfile.get();
        $('health-height').textContent = profile.heightCm ? `${format(Number(profile.heightCm))} cm` : 'Nog niet ingevuld';
        $('health-weight').textContent = profile.weightKg ? `${format(Number(profile.weightKg))} kg` : 'Nog niet ingevuld';
        const history = $('weight-history'); history.replaceChildren();
        const weights = window.capProfile.weights();
        weights.forEach(entry => {
            const item = document.createElement('li');
            const date = new Intl.DateTimeFormat('nl-BE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Brussels' }).format(new Date(entry.recordedAt));
            item.textContent = `${format(entry.kg)} kg · ${date}`;
            history.append(item);
        });
        if (!weights.length) {
            const empty = document.createElement('li'); empty.textContent = 'Nog geen gewicht bijgehouden.'; history.append(empty);
        }
        const measurements = window.capProfile.measurements();
        const primary = ['bodyFatPercent', 'muscleKg', 'boneKg', 'waterPercent'];
        for (const [id, list] of [['health-composition-metrics', metrics.filter(metric => primary.includes(metric.key))],
            ['health-extra-metrics', metrics.filter(metric => !primary.includes(metric.key) && metric.key !== 'weightKg')]]) {
            const container = $(id); container.replaceChildren();
            const values = document.createElement('dl'); values.className = 'health-metrics';
            list.forEach(metric => {
                const latest = measurements.find(entry => entry[metric.key] != null);
                const group = document.createElement('div');
                group.dataset.metric = metric.key;
                const title = document.createElement('dt'); title.textContent = metric.label;
                const value = document.createElement('dd'); value.textContent = latest ? `${format(latest[metric.key])} ${metric.unit}` : '—';
                if (latest) value.title = new Date(latest.recordedAt).toLocaleString('nl-BE');
                group.append(title, value); values.append(group);
            });
            container.append(values);
        }
        $('body-latest').replaceChildren(); $('body-history').replaceChildren();
        if (!measurements.length) $('body-latest').textContent = 'Nog geen lichaamsmeting toegevoegd.';
        measurements.forEach((entry, index) => {
            const date = new Intl.DateTimeFormat('nl-BE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Brussels' }).format(new Date(entry.recordedAt));
            if (index === 0) {
                const stamp = document.createElement('p'); stamp.textContent = date; $('body-latest').append(stamp);
                displayMeasurement(entry, $('body-latest'));
            } else {
                const details = document.createElement('details'); details.className = 'saved-workout';
                const summary = document.createElement('summary'); summary.textContent = date;
                details.append(summary); displayMeasurement(entry, details); $('body-history').append(details);
            }
        });
    }
    $('health-edit-profile').addEventListener('click', () => window.capOpenProfile());
    measurementForm.addEventListener('submit', event => {
        event.preventDefault();
        if (!measurementForm.reportValidity()) return;
        try {
            window.capProfile.logMeasurement(Object.fromEntries(new FormData(measurementForm)));
            measurementForm.reset(); $('body-measurement-status').textContent = 'Meting toegevoegd aan deze preview.';
        } catch (error) { $('body-measurement-status').textContent = error.message; }
    });
    $('weight-log-form').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        try {
            window.capProfile.logWeight(form.elements.namedItem('weightKg').value);
            form.reset();
            $('weight-log-status').textContent = 'Gewicht toegevoegd en bijgewerkt in je profiel voor deze preview.';
        } catch (error) { $('weight-log-status').textContent = error.message; }
    });
    document.addEventListener('cap:profile-changed', renderHealth);
    document.addEventListener('cap:viewchange', () => {
        if (!window.capAccess.signedIn) { $('weight-log-form').reset(); $('weight-log-status').textContent = ''; measurementForm.reset(); $('body-measurement-status').textContent = ''; }
        if (location.hash === '#health') renderHealth();
    });
    renderHealth();
});
