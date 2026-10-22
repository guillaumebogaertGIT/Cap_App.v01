// Run with Node.js: node tools/test_nutrition.mjs
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const context = { window: {} };
vm.createContext(context);
vm.runInContext(await fs.readFile(new URL('../frontend/js/nutrition-calculator.js', import.meta.url), 'utf8'), context);
const calculator = context.window.capNutritionCalculator;
const input = { sex: 'male', age: 35, weightKg: 110, heightCm: 180, goalKg: 90,
    scaling: 'auto', bodyFatBand: '5', activity: 'light', calorieGoal: 'deficit25',
    strategy: 'standard', muscularity: 'average' };
// Cached B17:B24 from the supplied Smart Macro Planning Tool.xlsx.
const reference = { effectiveLb: 209.437, bmr: 1904.9902519569, tdee: 2619.3615964407377,
    calories: 1964.5211973305532, proteinG: 198.414, fatG: 65.04806651836407,
    carbsG: 146.35814966631915, waterMl: 3097 };
const result = calculator.calculate(input);
for (const [key, value] of Object.entries(reference)) {
    assert.ok(Math.abs(result[key] - value) < 1e-8, `Workbook reference: ${key}`);
}
let cases = 0;
for (const sex of ['male', 'female']) {
    for (const [activity] of calculator.activity) {
        for (const [strategy] of calculator.strategies) {
            const targets = calculator.calculate({ ...input, sex, activity, strategy, calorieGoal: 'maintenance' });
            assert.ok(Math.abs(targets.proteinG * 4 + targets.carbsG * 4 + targets.fatG * 9 - targets.calories) < 1e-8);
            cases++;
        }
    }
}
for (const invalid of [{ weightKg: '' }, { age: 'invalid' }, { bodyFatBand: '' },
    { scaling: 'forced', goalKg: '' }, { strategy: 'unknown' },
    { heightCm: NaN }, { goalKg: 500, weightKg: 20, scaling: 'standard', strategy: 'high', calorieGoal: 'deficit35' }]) {
    assert.throws(() => calculator.calculate({ ...input, ...invalid }));
}
assert.equal(calculator.calculate({ ...input, scaling: 'standard', goalKg: '' }).proteinG, input.weightKg * 2.2046);
assert.throws(() => calculator.validateTargets({ calories: '', proteinG: 0, carbsG: 0, fatG: 0, waterMl: 0 }));
assert.throws(() => calculator.validateTargets({ calories: 2000, proteinG: -1, carbsG: 200, fatG: 60, waterMl: 2000 }));
console.log(`Nutrition checks passed: workbook reference, ${cases} energy-reconciliation cases and invalid/missing-input guards.`);
