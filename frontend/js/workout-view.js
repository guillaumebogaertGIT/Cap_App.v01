// Preserve exercise indexes for logging while presenting linked exercises as blocks.
window.capWorkoutView = {
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
    render(exercises) {
        const container = document.createElement('div'); container.className = 'workout-blocks';
        this.blocks(exercises).forEach(block => {
            const section = document.createElement('section'); section.className = 'workout-display-block';
            const heading = document.createElement('h4'); heading.textContent = block.title;
            const list = document.createElement('ol');
            block.items.forEach(({ exercise: e }) => {
                const item = document.createElement('li');
                item.textContent = `${e.name} · ${e.sets} × ${e.reps}${e.targetKg != null ? ` · ${e.targetKg} kg` : ''}${e.restSeconds != null ? ` · Rust ${e.restSeconds} s` : ''}${e.tempo ? ` · Tempo ${e.tempo}` : ''}`;
                list.append(item);
            });
            section.append(heading, list); container.append(section);
        });
        return container;
    }
};
