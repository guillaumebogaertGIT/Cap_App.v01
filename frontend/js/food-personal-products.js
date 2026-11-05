// Athlete-owned label data. Public products are never overwritten or uploaded.
document.addEventListener('DOMContentLoaded',()=>{
    const real=window.capReal,$=id=>document.getElementById(id);
    const allowed=()=>real?.account.role==='ATHLETE'&&window.capAccess.signedIn;
    let version=0,loading=false,busy=false,current=null,original=null;
    const button=document.createElement('button');button.type='button';button.className='secondary-button';button.hidden=true;
    $('food-barcode-result').after(button);
    const libraryStatus=document.createElement('p');libraryStatus.className='sample-notice';libraryStatus.setAttribute('role','status');libraryStatus.hidden=true;button.after(libraryStatus);
    const dialog=document.createElement('dialog');dialog.className='club-dialog';dialog.id='personal-food-dialog';dialog.setAttribute('aria-labelledby','personal-food-title');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="personal-food-title">Mijn product bewaren</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
      <p>Bewaard in jouw eigen productlijst. Neem de gegevens over van je verpakking.</p>
      <form class="club-form"><label>Barcode<input name="barcode" readonly required></label>
      <div class="profile-fields"><label>Productnaam<input name="name" maxlength="120" required></label><label>Merk (optioneel)<input name="brand" maxlength="160"></label>
      <label>Waarden op het etiket voor<input name="baseAmount" type="number" min="0.001" max="100000" step="any" inputmode="decimal" value="100" required></label>
      <label>Eenheid<select name="unit" required><option value="">Kies g of ml</option><option value="g">g</option><option value="ml">ml</option></select></label>
      <label>Energie (kcal)<input name="calories" type="number" min="0" max="1000000" step="any" inputmode="decimal"></label>
      <label>Eiwitten (g)<input name="proteinG" type="number" min="0" max="1000000" step="any" inputmode="decimal"></label>
      <label>Koolhydraten (g)<input name="carbsG" type="number" min="0" max="1000000" step="any" inputmode="decimal"></label>
      <label>Vetten (g)<input name="fatG" type="number" min="0" max="1000000" step="any" inputmode="decimal"></label></div>
      <p class="sample-notice">Bijvoorbeeld: een portie poeder van 30 g. Laat niet-vermelde waarden leeg; vul 0 alleen in als het etiket dat aangeeft. Met ontbrekende waarden wordt het product wel herkend, maar nog niet als calorieportie gelogd.</p>
      <label class="personal-food-confirm"><input name="confirmed" type="checkbox" required><span>Ik heb deze gegevens gecontroleerd op mijn verpakking.</span></label>
      <p role="status" aria-live="polite"></p><div class="editor-actions"><button type="submit" class="primary-button">Bewaren in mijn producten</button><button type="button" class="secondary-button" data-delete hidden>Eigen product verwijderen</button></div></form>`;
    document.body.append(dialog);const form=dialog.querySelector('form'),status=dialog.querySelector('[role=status]'),remove=dialog.querySelector('[data-delete]');
    function setBusy(value){busy=value;dialog.querySelectorAll('button,input,select').forEach(control=>control.disabled=value);}
    function edit(value){
        if(!allowed()||busy||!value?.barcode)return;
        original=value.source==='personal'?value:null;form.reset();
        form.elements.barcode.value=value.barcode;form.elements.name.value=value.name||'';form.elements.brand.value=value.brand||'';
        form.elements.unit.value=['g','ml'].includes(value.unit)?value.unit:'';
        for(const key of ['calories','proteinG','carbsG','fatG'])form.elements[key].value=value[key]??'';
        remove.hidden=!original;status.textContent='';
        if(!dialog.open)dialog.showModal();form.elements.name.focus();
    }
    async function refresh(force=false){
        if(!allowed()||loading&&!force)return;loading=true;const request=++version;
        try{const products=await real.request('/api/food-products/personal');if(request===version&&allowed()){window.capFoodDiary.syncPersonalProducts(products);libraryStatus.hidden=true;}}
        catch{if(request===version&&allowed()){libraryStatus.textContent='Je eigen producten konden niet laden. Open Voeding opnieuw om te proberen; opzoeken met barcode blijft beschikbaar.';libraryStatus.hidden=false;}}
        finally{if(request===version)loading=false;}
    }
    button.addEventListener('click',()=>edit(current));
    document.addEventListener('cap:food-product-result',event=>{
        if(!allowed())return;
        current=event.detail.value||{barcode:event.detail.barcode};
        button.textContent=current.source==='personal'?'Eigen product bewerken':'Bewaren als mijn product';button.hidden=false;
    });
    function reset(){button.hidden=true;current=null;}
    $('food-barcode').addEventListener('input',()=>{reset();const code=$('food-barcode').value.trim().replace(/[ -]/g,'');if(allowed()&&/^(?:[0-9]{8}|[0-9]{12,14})$/.test(code)){current={barcode:code};button.hidden=false;button.textContent='Eigen product toevoegen';}});document.addEventListener('cap:food-lookup-reset',reset);
    dialog.querySelector('.session-dialog-close').addEventListener('click',()=>{if(!busy)dialog.close();});
    dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    form.addEventListener('submit',async event=>{
        event.preventDefault();if(!allowed()||busy||!form.reportValidity())return;
        const input={name:form.elements.name.value.trim(),brand:form.elements.brand.value.trim(),unit:form.elements.unit.value,baseAmount:Number(form.elements.baseAmount.value)};
        for(const key of ['calories','proteinG','carbsG','fatG'])input[key]=form.elements[key].value.trim()===''?null:Number(form.elements[key].value);
        setBusy(true);status.textContent='Product bewaren…';
        try{
            const saved=await real.request('/api/food-products/personal/'+form.elements.barcode.value,'PUT',input);
            dialog.close();await refresh(true);
            $('food-barcode-status').textContent=saved.complete?'Eigen product bewaard. Vul je hoeveelheid in.':'Eigen product bewaard. De barcode wordt voortaan herkend; vul ontbrekende voedingswaarden later aan.';
            if(saved.complete)window.capFoodDiary.selectBarcodeProduct(saved);
            current=saved;button.hidden=false;button.textContent='Eigen product bewerken';
        }catch(error){status.textContent=error.message;}
        finally{setBusy(false);}
    });
    remove.addEventListener('click',async()=>{
        if(!allowed()||busy||!original||!confirm('Dit eigen product verwijderen? Je bestaande dagboek blijft behouden.'))return;
        setBusy(true);
        try{await real.request('/api/food-products/personal/'+original.barcode,'DELETE');dialog.close();reset();await refresh(true);$('food-barcode-status').textContent='Eigen product verwijderd. Bestaande dagboekporties blijven behouden.';}
        catch(error){status.textContent=error.message;}finally{setBusy(false);}
    });
    document.addEventListener('cap:viewchange',()=>{if(allowed()&&location.hash==='#nutrition')refresh();else{++version;loading=false;if(dialog.open&&!busy)dialog.close();}});
    window.addEventListener('focus',()=>{if(location.hash==='#nutrition')refresh();});
    window.capPersonalFoods={edit,refresh};if(location.hash==='#nutrition')refresh();
});
