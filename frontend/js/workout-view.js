// Preserve exercise indexes for logging while presenting linked exercises as blocks.
window.capWorkoutView = {
    setTargets(exercise) {
        return exercise.setPlan?.length ? exercise.setPlan : Array.from({ length: exercise.sets }, () => ({ reps: exercise.reps, loadMode: exercise.loadMode, loadValue: exercise.loadValue, targetKg: exercise.targetKg, referenceKg: exercise.referenceKg }));
    },
    targetText(set) {
        return set.reps + ' reps' + (set.targetKg != null ? ' · ' + set.targetKg + ' kg' : set.loadMode === 'fixed' ? ' · ' + set.loadValue + ' kg' : '') + (set.loadMode === 'percent' ? ' · ' + set.loadValue + '% van ' + (set.referenceKg != null ? set.referenceKg + ' kg' : 'rep-PR') : '');
    },
    blocks(exercises) {
        const blocks = [];
        const groups = new Map();
        exercises.forEach((exercise, index) => {
            const key = exercise.supersetGroup;
            let block = key ? groups.get(key) : null;
            if (!block) {
                block = { title: key ? `Blok ${key} · Superset` : `Oefening ${index + 1}`, items: [] };
                blocks.push(block);
                if (key) groups.set(key, block);
            }
            block.items.push({ exercise, index });
        });
        return blocks;
    },
    compact(e){const sets=this.setTargets(e);const same=sets.length&&sets.every(s=>s.reps===sets[0].reps&&s.targetKg===sets[0].targetKg&&s.loadMode===sets[0].loadMode&&s.loadValue===sets[0].loadValue);return (same?sets.length+' × '+this.targetText(sets[0]):sets.map((s,i)=>(i+1)+': '+this.targetText(s)).join(' / '))+(e.restSeconds!=null?' · Rust '+e.restSeconds+' s':'')+(e.tempo?' · Tempo '+e.tempo:'');},
    render(exercises, onRemove = null) {
        const container = document.createElement('div'); container.className = 'workout-blocks';
        this.blocks(exercises).forEach(block => {
            const section = document.createElement('section'); section.className = 'workout-display-block';
            const heading = document.createElement('h4'); heading.textContent = block.title;
            const list = document.createElement('ol');
            block.items.forEach(({ exercise: e, index }) => {
                const item = document.createElement('li');
                item.textContent = `${e.name} · ${e.sets} × ${e.reps}${e.targetKg != null ? ` · ${e.targetKg} kg` : ''}${e.restSeconds != null ? ` · Rust ${e.restSeconds} s` : ''}${e.tempo ? ` · Tempo ${e.tempo}` : ''}`;
                const name=document.createElement('strong');name.textContent=e.name;const targets=document.createElement('span');targets.textContent=this.compact(e);item.replaceChildren(name,targets);
                if(e.rir!=null)item.append(document.createTextNode(' · Doel-RIR '+e.rir));
                window.capExerciseVideo.append(item, e.name);
                if (onRemove) {
                    item.classList.add('review-exercise');
                    const remove = document.createElement('button');
                    remove.type = 'button'; remove.className = 'secondary-button review-remove-exercise';
                    remove.textContent = 'Oefening verwijderen';
                    remove.setAttribute('aria-label', `Oefening ${e.name} verwijderen`);
                    remove.addEventListener('click', () => onRemove(index, remove));
                    item.append(remove);
                }
                list.append(item);
            });
            section.append(heading, list); container.append(section);
        });
        return container;
    }
};
