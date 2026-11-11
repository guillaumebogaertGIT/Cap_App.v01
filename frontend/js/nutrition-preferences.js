document.addEventListener('DOMContentLoaded',()=>{
    const real=window.capReal,section=document.getElementById('nutrition-preferences'),form=document.getElementById('nutrition-preferences-form');
    const status=form.querySelector('[role=status]'),controls=[...form.elements];let version=0,loaded=false,busy=false,generation=0;
    const disable=value=>controls.forEach(control=>control.disabled=value);disable(true);
    const isAthlete=()=>real?real.account?.role==='ATHLETE':previewRole==='athlete';
    async function load(){const current=++generation;if(!isAthlete()||!real){section.hidden=true;return;}section.hidden=false;if(real.fullAccess===false&&section.parentElement.id!=='planning')document.getElementById('planning').append(section);if(loaded||busy)return;
        try{status.textContent='Voorkeuren laden…';const value=await real.request('/api/nutrition-preferences/self');if(current!==generation||!isAthlete())return;form.elements.allergies.value=value.allergies;form.elements.avoidedFoods.value=value.avoidedFoods;form.elements.aiShare.checked=value.aiShare;version=value.version;loaded=true;disable(false);status.textContent='';}
        catch(error){if(current===generation)status.textContent=error.message||'Voorkeuren konden niet laden.';}
    }
    form.onsubmit=async event=>{event.preventDefault();if(!loaded||busy||!isAthlete())return;const input={allergies:form.elements.allergies.value,avoidedFoods:form.elements.avoidedFoods.value,aiShare:form.elements.aiShare.checked,version};busy=true;disable(true);status.textContent='Bewaren…';
        try{const value=await real.request('/api/nutrition-preferences/self','PUT',input);version=value.version;form.elements.allergies.value=value.allergies;form.elements.avoidedFoods.value=value.avoidedFoods;form.elements.aiShare.checked=value.aiShare;status.textContent='Je voorkeuren zijn bewaard.';}
        catch(error){status.textContent=error.message||'Voorkeuren zijn niet bewaard.';}finally{busy=false;disable(false);}
    };
    document.addEventListener('cap:viewchange',load);section.addEventListener('toggle',()=>{if(section.open)load();});load();
});
