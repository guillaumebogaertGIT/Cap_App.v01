document.addEventListener('DOMContentLoaded',()=>{
    const isCoach=()=>window.capReal?window.capReal.account.role==='COACH':previewRole==='coach';
    const navigations=[];
    for(const id of ['health','coach-health']){
        const nav=document.createElement('nav');nav.className='club-actions';nav.setAttribute('aria-label','Gezondheidsweergave');nav.hidden=true;
        const own=document.createElement('a');own.href='#health';own.className='secondary-button';own.textContent='Mijn gezondheid';nav.append(own);
        if(!window.capReal||window.capCan('CLIENT_HEALTH')){const clients=document.createElement('a');clients.href='#coach-health';clients.className='secondary-button';clients.textContent='Gezondheid sporters';nav.append(clients);}
        document.getElementById(id).querySelector('header').after(nav);navigations.push(nav);
    }
    function refresh(){for(const nav of navigations){nav.hidden=!isCoach();for(const link of nav.children){if(link.hash===location.hash)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');}}}
    document.addEventListener('cap:viewchange',refresh);refresh();
});
