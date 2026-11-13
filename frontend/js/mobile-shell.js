// Move the existing account controls; never create a second menu or authentication flow.
document.addEventListener('DOMContentLoaded',()=>{
    const sidebar=document.querySelector('.sidebar'),brand=document.querySelector('.sidebar-brand'),account=document.querySelector('.account-bar'),menu=document.querySelector('.menu-toggle');
    const brandSlot=document.createComment('brand position'),accountSlot=document.createComment('account position'),menuSlot=document.createComment('menu position');brand.before(brandSlot);account.before(accountSlot);menu.before(menuSlot);
    const header=document.createElement('div');header.className='mobile-app-header';sidebar.prepend(header);const media=matchMedia('(max-width:800px)'),originalMenu=menu.innerHTML;
    function apply(){if(media.matches){header.append(brand,account,menu);menu.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg>';menu.setAttribute('aria-label','Menu');document.getElementById('account-toggle').setAttribute('aria-label','Accountmenu');}else{brandSlot.after(brand);accountSlot.after(account);menuSlot.after(menu);menu.innerHTML=originalMenu;document.getElementById('account-toggle').removeAttribute('aria-label');}header.hidden=!media.matches;}
    function keyboard(){const viewport=window.visualViewport;document.body.classList.toggle('keyboard-open',media.matches&&Boolean(viewport)&&window.innerHeight-viewport.height>140&&document.activeElement.matches('input,textarea,select'));}
    function drawMenu(){if(!media.matches)return;const open=menu.getAttribute('aria-expanded')==='true';menu.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="'+(open?'m6 6 12 12M18 6 6 18':'M4 6h16M4 12h16M4 18h16')+'"/></svg>';menu.setAttribute('aria-label',open?'Menu sluiten':'Menu openen');}
    new MutationObserver(drawMenu).observe(menu,{attributes:true,attributeFilter:['aria-expanded']});
    // Safari gestures are cancelled only inside the phone workout flow.
    const workoutZoomLocked=()=>media.matches&&['#workouts','#training'].includes(location.hash);
    const updateWorkoutZoom=()=>document.body.classList.toggle('workout-zoom-locked',workoutZoomLocked());
    for(const name of ['gesturestart','gesturechange'])document.addEventListener(name,event=>{if(workoutZoomLocked())event.preventDefault();},{passive:false});
    document.addEventListener('touchmove',event=>{if(workoutZoomLocked()&&event.touches.length>1)event.preventDefault();},{passive:false});
    media.addEventListener('change',updateWorkoutZoom);window.addEventListener('hashchange',updateWorkoutZoom);document.addEventListener('cap:viewchange',updateWorkoutZoom);updateWorkoutZoom();
    media.addEventListener('change',apply);window.visualViewport?.addEventListener('resize',keyboard);document.addEventListener('focusin',keyboard);document.addEventListener('focusout',()=>setTimeout(keyboard,0));document.addEventListener('cap:viewchange',drawMenu);apply();drawMenu();
});
