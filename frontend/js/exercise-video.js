// Workouts currently store exercise names, so never guess between duplicate definitions.
window.capExerciseVideo = {
    append(container, name) {
        const slot = document.createElement('div');
        slot.className = 'exercise-video';
        container.append(slot);
        const load = async () => {
            slot.replaceChildren();
            try {
                const entries = await window.capExerciseCatalog.load();
                const key = value => value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('nl-BE');
                const matches = entries.filter(entry => key(entry.name) === key(name));
                if (matches.length !== 1) return;
                if(matches[0].instructions?.trim()){
                    const details=document.createElement('details'),summary=document.createElement('summary'),copy=document.createElement('p');
                    details.className='exercise-instructions';summary.textContent='Uitleg van de oefening';copy.textContent=matches[0].instructions;details.append(summary,copy);slot.append(details);
                }
                if(!matches[0].videoUrl)return;
                const url = new URL(matches[0].videoUrl);
                if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return;
                const link = document.createElement('a');
                link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer';
                link.textContent = 'Bekijk video';
                link.setAttribute('aria-label', `Bekijk video van ${name} (nieuw tabblad)`);
                slot.append(link);
            } catch {
                const retry = document.createElement('button');
                retry.type = 'button'; retry.className = 'secondary-button';
                retry.textContent = 'Videolink opnieuw laden';
                retry.setAttribute('aria-label', `Videolink voor ${name} opnieuw laden`);
                retry.addEventListener('click', load);
                slot.append(retry);
            }
        };
        load();
    }
};
