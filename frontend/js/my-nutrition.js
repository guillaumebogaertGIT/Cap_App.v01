document.addEventListener('DOMContentLoaded',()=>{
    const real=window.capReal,$=id=>document.getElementById(id),isCoach=()=>real?real.account.role==='COACH':previewRole==='coach';
    const form=document.createElement('form');form.className='club-form';form.hidden=true;
    form.innerHTML='<h3>Mijn dagdoelen aanpassen</h3><div class="club-form-grid"></div><p role="status"></p><button class="primary-button" type="submit">Mijn doelen bewaren</button>';
    const fields=[['calories','Calorieën (kcal)',1],['proteinG','Eiwitten (g)',0],['carbsG','Koolhydraten (g)',0],['fatG','Vetten (g)',0],['waterMl','Water (ml)',0]];
    for(const [name,title,min] of fields){const label=document.createElement('label');label.textContent=title;const input=document.createElement('input');Object.assign(input,{type:'number',name,required:true,min:String(min),max:'100000',step:'0.1'});label.append(input);form.querySelector('div').append(label);}
    $('athlete-nutrition-targets').closest('article').append(form);
    // Reuse the same formula and fields, with a separate form so client drafts stay untouched.
    const calculator=window.capNutritionCalculator;
    const ownCalculator=document.createElement('details');ownCalculator.className='saved-workout';ownCalculator.hidden=true;
    ownCalculator.innerHTML='<summary>Mijn dagdoelen berekenen</summary><p class="sample-notice">Bereken een schatting voor jezelf. Controleer de uitkomst en kies daarna Mijn doelen bewaren.</p>';
    const calculateForm=$('nutrition-calculator-form').cloneNode(true);
    const bodyFatLabel=calculateForm.querySelector('#nutrition-body-fat-label');
    calculateForm.removeAttribute('id');calculateForm.querySelectorAll('[id]').forEach(element=>element.removeAttribute('id'));
    calculateForm.querySelectorAll('input,select,button').forEach(element=>element.disabled=false);
    calculateForm.reset();
    const calculationStatus=calculateForm.querySelector('[role=status]');calculationStatus.textContent='';
    ownCalculator.append(calculateForm);form.before(ownCalculator);
    let calculatedTargets=null;
    function syncBodyFat(){
        const select=calculateForm.elements.bodyFatBand,value=select.value;
        select.replaceChildren(new Option('Kies een categorie',''));
        for(const [index,[label]] of (calculator.bodyFat[calculateForm.elements.sex.value]||[]).entries())select.add(new Option(label,String(index)));
        select.value=value;bodyFatLabel.hidden=calculateForm.elements.scaling.value!=='auto';
    }
    calculateForm.elements.sex.addEventListener('change',syncBodyFat);
    calculateForm.elements.scaling.addEventListener('change',syncBodyFat);syncBodyFat();
    calculateForm.addEventListener('input',()=>{calculationStatus.textContent='Invoer gewijzigd. Kies Bereken dagdoelen om de doelen opnieuw in te vullen.';});
    calculateForm.onsubmit=event=>{
        event.preventDefault();if(!isCoach()||busy||!calculateForm.reportValidity())return;
        try{
            const result=calculator.calculate(Object.fromEntries(new FormData(calculateForm)));
            for(const [name] of fields)form.elements[name].value=Math.round(result[name]*10)/10;
            calculatedTargets=JSON.stringify(Object.fromEntries(fields.map(([name])=>[name,Number(form.elements[name].value)])));
            calculationStatus.textContent='Berekend. Controleer de ingevulde dagdoelen hieronder en kies Mijn doelen bewaren.';
            form.querySelector('[role=status]').textContent='Nog niet bewaard.';
            form.elements.calories.focus();
        }catch(error){calculationStatus.textContent=error.message;}
    };
    let loaded=false,busy=false;
    for(const id of ['nutrition','coach-nutrition']){
        const nav=document.createElement('nav');nav.className='club-actions';nav.setAttribute('aria-label','Voedingsweergave');
        const own=document.createElement('a');own.href='#nutrition';own.className='secondary-button';own.textContent='Mijn voeding';nav.append(own);
        if(!real||window.capCan('NUTRITION_MANAGE')){const clients=document.createElement('a');clients.href='#coach-nutrition';clients.className='secondary-button';clients.textContent='Klantvoeding';nav.append(clients);}
        document.getElementById(id).querySelector('header').after(nav);nav.dataset.coachNutritionNav='';
    }
    async function refresh(){
        form.hidden=!isCoach();ownCalculator.hidden=!isCoach();$('nutrition-title').textContent=isCoach()?'Mijn voeding':'Voeding';
        document.querySelectorAll('[data-coach-nutrition-nav]').forEach(nav=>{nav.hidden=!isCoach();for(const link of nav.children){if(link.hash===location.hash)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');}});
        if(!isCoach()||location.hash!=='#nutrition'||busy)return;
        const intro=document.querySelector('#nutrition .dashboard-intro');intro.textContent='Jouw eigen dagdoelen en voedingsdagboek.';
        if(loaded)return;
        try{const plan=real?await real.request('/api/nutrition-targets/self'):window.capStorage.read('own-nutrition-targets',null);if(plan)for(const [name] of fields)form.elements[name].value=plan.targets[name];loaded=true;}
        catch(error){form.querySelector('[role=status]').textContent=error.message;}
    }
    form.onsubmit=async event=>{event.preventDefault();if(busy||!isCoach())return;busy=true;form.querySelector('button').disabled=true;
        try{const targets=Object.fromEntries(fields.map(([name])=>[name,Number(form.elements[name].value)]));const plan=real?await real.request('/api/nutrition-targets/self','PUT',{targets,origin:calculatedTargets===JSON.stringify(targets)?'berekend':'handmatig'}):{targets};if(!real){window.capStorage.write('own-nutrition-targets',plan);window.capNutritionTargets.setPreviewOwn(plan);}form.querySelector('[role=status]').textContent='Jouw dagdoelen zijn bewaard.';document.dispatchEvent(new CustomEvent('cap:viewchange'));}
        catch(error){form.querySelector('[role=status]').textContent=error.message;}finally{busy=false;form.querySelector('button').disabled=false;}
    };
    document.addEventListener('cap:viewchange',refresh);refresh();
});
