// Arithmetic translated from Smart Macro Planning Tool.xlsx, not dietary advice.
(() => {
    const activity = [
        ['sedentary', 'Weinig beweging', 1.2, 30],
        ['light', 'Licht actief · 1–3 trainingen/week', 1.375, 50],
        ['moderate', 'Gemiddeld actief · 3–5 trainingen/week', 1.55, 75],
        ['very', 'Zeer actief · 6–7 trainingen/week', 1.725, 125],
        ['extreme', 'Zeer hoge activiteit · sport/fysiek werk', 1.9, 150]
    ];
    const calorieGoals = [
        ['maintenance', 'Onderhoud', 1], ['deficit5', '5% tekort', 0.95],
        ['deficit10', '10% tekort', 0.9], ['deficit15', '15% tekort', 0.85],
        ['deficit20', '20% tekort', 0.8], ['deficit25', '25% tekort', 0.75],
        ['deficit30', '30% tekort', 0.7], ['deficit35', '35% tekort', 0.65],
        ['surplus10', '10% overschot', 1.1]
    ];
    const strategies = [
        ['standard', 'Standard · 1 g eiwit/lb', 1], ['balanced', 'Balanced · 0,8 g eiwit/lb', 0.8],
        ['high', 'High-protein · 1,2 g eiwit/lb', 1.2], ['carb', 'Carb-based', 1],
        ['fat', 'Fat-based', 1], ['keto', 'Keto', 1]
    ];
    const muscularity = [['average', 'Gemiddeld', 1], ['muscular', 'Gespierd', 1.1], ['high', 'Zeer gespierd', 1.15]];
    const bodyFat = {
        male: [['<15%', 0], ['15–19,9%', 0.15], ['20–24,9%', 0.33], ['25–29,9%', 0.5], ['30–34,9%', 0.66], ['35%+', 1]],
        female: [['<25%', 0], ['25–29,9%', 0.15], ['30–34,9%', 0.33], ['35–39,9%', 0.5], ['40–44,9%', 0.66], ['45%+', 1]]
    };
    function numeric(raw, label, min, max) {
        const value = Number(raw);
        if (raw == null || String(raw).trim() === '' || !Number.isFinite(value) || value < min || (max != null && value > max)) throw new Error(`Controleer ${label}.`);
        return value;
    }
    function lookup(rows, key, label) {
        const row = rows.find(item => item[0] === key);
        if (!row) throw new Error(`Kies ${label}.`);
        return row;
    }
    function calculate(input) {
        if (!['male', 'female'].includes(input.sex)) throw new Error('Kies een formule (man/vrouw) voor de berekening.');
        const age = numeric(input.age, 'de leeftijd', 1, 120);
        const weight = numeric(input.weightKg, 'het gewicht', 20, 500);
        const height = numeric(input.heightCm, 'de lengte', 50, 250);
        const weightLb = weight * 2.2046;
        const hasGoal = String(input.goalKg ?? '').trim() !== '';
        const goalLb = hasGoal ? numeric(input.goalKg, 'het streefgewicht', 20, 500) * 2.2046 : weightLb;
        if (!['standard', 'auto', 'forced'].includes(input.scaling)) throw new Error('Kies de gewichtscorrectie.');
        let factor = 0;
        if (input.scaling !== 'standard') {
            if (!hasGoal) throw new Error('Vul een streefgewicht in voor de gewichtscorrectie.');
            if (input.scaling === 'forced') factor = 1;
            else {
                const band = numeric(input.bodyFatBand, 'de vetpercentagecategorie', 0, 5);
                if (!Number.isInteger(band)) throw new Error('Kies de vetpercentagecategorie.');
                factor = bodyFat[input.sex][band][1];
            }
        }
        const effectiveLb = weightLb * (1 - factor) + (goalLb + 0.25 * (weightLb - goalLb)) * factor;
        const bmr = 10 * (effectiveLb * 0.45359237) + 6.25 * height - 5 * age + (input.sex === 'male' ? 5 : -161);
        const level = lookup(activity, input.activity, 'een activiteitsniveau');
        const tdee = bmr * level[2] * lookup(muscularity, input.muscularity, 'de spierbouw')[2];
        const calories = tdee * lookup(calorieGoals, input.calorieGoal, 'een caloriedoel')[2];
        const proteinG = goalLb * lookup(strategies, input.strategy, 'een macrostrategie')[2];
        let carbsG;
        let fatG;
        if (input.strategy === 'keto') {
            carbsG = level[3]; fatG = (calories - proteinG * 4 - carbsG * 4) / 9;
        } else {
            fatG = ['standard', 'balanced'].includes(input.strategy) ? (calories - proteinG * 4) / 18
                : calories * (input.strategy === 'fat' ? 0.45 : 0.25) / 9;
            carbsG = (calories - proteinG * 4 - fatG * 9) / 4;
        }
        const waterMl = Math.round(effectiveLb * 14.7868);
        if (![bmr, tdee, calories, proteinG, carbsG, fatG, waterMl].every(Number.isFinite) || calories <= 0 || bmr <= 0 || [proteinG, carbsG, fatG].some(value => value < 0)) {
            throw new Error('Deze combinatie levert ongeldige of negatieve doelen op. Controleer de invoer en de gekozen strategie.');
        }
        return { calories, proteinG, carbsG, fatG, waterMl, bmr, tdee, effectiveLb };
    }
    function validateTargets(values) {
        return Object.fromEntries(['calories', 'proteinG', 'carbsG', 'fatG', 'waterMl'].map(key => [key, numeric(values[key], { calories: 'het caloriedoel', proteinG: 'het eiwitdoel', carbsG: 'het koolhydraatdoel', fatG: 'het vetdoel', waterMl: 'het waterdoel' }[key], key === 'calories' ? 1 : 0)]));
    }
    window.capNutritionCalculator = { calculate, validateTargets, activity, calorieGoals, strategies, muscularity, bodyFat };
})();
