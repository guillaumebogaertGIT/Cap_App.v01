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
    render(exercises, onRemove = null) {
        const container = document.createElement('div'); container.className = 'workout-blocks';
        this.blocks(exercises).forEach(block => {
            const section = document.createElement('section'); section.className = 'workout-display-block';
            const heading = document.createElement('h4'); heading.textContent = block.title;
            const list = document.createElement('ol');
            block.items.forEach(({ exercise: e, index }) => {
                const item = document.createElement('li');
                item.textContent = `${e.name} · ${e.sets} × ${e.reps}${e.targetKg != null ? ` · ${e.targetKg} kg` : ''}${e.restSeconds != null ? ` · Rust ${e.restSeconds} s` : ''}${e.tempo ? ` · Tempo ${e.tempo}` : ''}`;
                if (e.setPlan?.length) {
                    item.textContent = e.name + (e.restSeconds != null ? ' · Rust ' + e.restSeconds + ' s' : '') + (e.tempo ? ' · Tempo ' + e.tempo : '');
                    const sets = document.createElement('ul');
                    this.setTargets(e).forEach((set, i) => { const line = document.createElement('li'); line.textContent = 'Set ' + (i + 1) + ': ' + this.targetText(set); sets.append(line); });
                    item.append(sets);
                }
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
