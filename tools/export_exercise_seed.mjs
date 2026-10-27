// Run after importing a new workbook: node tools/export_exercise_seed.mjs
// Keep the same duplicate grouping and stable IDs as the browser's imported catalog.
import { readFileSync, writeFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const context = { window: {} };
runInNewContext(readFileSync(new URL('../frontend/js/exercise-data.js', import.meta.url), 'utf8'), context);
const database = context.window.capExerciseDatabase;
const fields = Object.keys(database.source.columns);
const seen = new Set();
const exercises = database.exercises.filter(exercise => {
    const key = JSON.stringify(fields.map(field => exercise[field]));
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
}).map(({ id, name, region, category, subcategory, instructions, notes }) => ({
    id, name, region, category, subcategory: subcategory || '',
    instructions: instructions || '', notes: notes || '', archived: false
}));
writeFileSync(new URL('../backend/src/main/resources/exercise-catalog.json', import.meta.url), JSON.stringify(exercises, null, 2) + '\n');
console.log(`Exported ${exercises.length} exercise definitions; imported source stays unchanged.`);
