const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');
const sidebar = document.querySelector('.sidebar');
const sidebarToggle = document.querySelector('.sidebar-toggle');
const mobileLayout = window.matchMedia('(max-width: 800px)');
const originalSidebarLinks = Array.from(sidebarNavigation.children);
const coachSidebarOrder = ['dashboard', 'planning', 'bookings', 'create-workout', 'athletes', 'coach-nutrition'];
const moreViews = ['colleagues', 'finance', 'saved-workouts', 'exercises'];
const moreLinks = moreViews.map(view => originalSidebarLinks.find(link => link.dataset.viewLink === view));
const moreNavigation = document.createElement('details');
moreNavigation.className = 'sidebar-more';
const moreSummary = document.createElement('summary');
moreSummary.textContent = 'Meer';
moreNavigation.append(moreSummary);
let previewRole = window.capReal?.account.role === 'COACH' ? 'coach' : 'athlete';

document.querySelectorAll('[name="preview-role"]').forEach((input) => {
    input.checked = input.value === previewRole;
    input.addEventListener('change', () => {
        previewRole = input.value;
        // Both roles use the dashboard route; the preview chooses its content.
        if (window.location.hash !== '#dashboard') {
            window.location.hash = 'dashboard';
        } else {
            showCurrentView();
        }
    });
});

sidebarToggle.addEventListener('click', () => {
    const isCollapsed = sidebar.classList.toggle('is-collapsed');
    sidebarToggle.setAttribute('aria-expanded', String(!isCollapsed));
    const label = isCollapsed ? 'Zijbalk uitklappen' : 'Zijbalk inklappen';
    sidebarToggle.setAttribute('aria-label', label);
    sidebarToggle.title = label;
});

// Each layout has its own control; keep focus on a visible control on resize.
mobileLayout.addEventListener('change', () => {
    const focusWasInSidebar = sidebar.contains(document.activeElement);
    setMenuOpen(false);
    if (focusWasInSidebar) {
        (mobileLayout.matches ? menuToggle : sidebarToggle).focus();
    }
});

function setMenuOpen(isOpen) {
    sidebarNavigation.classList.toggle('is-open', isOpen);
    menuToggle.setAttribute('aria-expanded', String(isOpen));
    menuToggle.textContent = isOpen ? 'Sluit menu' : 'Menu';
}

menuToggle.addEventListener('click', () => {
    const isOpen = menuToggle.getAttribute('aria-expanded') === 'true';
    setMenuOpen(!isOpen);
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') {
        setMenuOpen(false);
        if (window.matchMedia('(max-width: 800px)').matches) {
            menuToggle.focus();
        }
    }
});

// URL fragments let the browser's Back and Forward buttons switch screens too.
function syncHomeShortcuts(allowedViews) {
    const homes=[document.querySelector('#coach .home-tiles'),document.querySelector('#dashboard .home-tiles')];
    const financeHome=document.getElementById('finance-home');
    if(financeHome){
        let grid=financeHome.querySelector('.home-tiles');
        if(!grid){grid=document.createElement('nav');grid.className='home-tiles';grid.setAttribute('aria-label','Financiële taken');
            const shortcut=financeHome.querySelector('a[href="#finance"]');shortcut.className='home-tile';grid.append(shortcut);financeHome.querySelector('#finance-home-status').before(grid);}
        homes.push(grid);
    }
    const descriptions={colleagues:'Het team en contactgegevens',finance:'Betalingen en financieel overzicht',settings:'Jouw voorkeuren en instellingen',health:'Jouw metingen en vooruitgang'};
    for(const grid of homes.filter(Boolean)){
        grid.querySelectorAll('.home-tile').forEach(tile=>{tile.hidden=true;});
        for(const link of sidebarNavigation.querySelectorAll('a[data-view-link]')){
            const route=link.dataset.viewLink;
            if(link.hidden||route==='dashboard'||!allowedViews.includes(route))continue;
            let tile=grid.querySelector('a[href="#'+route+'"]');
            if(!tile){tile=document.createElement('a');tile.className='home-tile';tile.href='#'+route;grid.append(tile);}
            let heading=tile.querySelector('strong');
            if(!heading){tile.replaceChildren();heading=document.createElement('strong');tile.append(heading);}
            heading.textContent=link.textContent.trim()||'Instellingen';
            if(!tile.querySelector('svg')){
                const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 24 24');icon.setAttribute('aria-hidden','true');
                const path=document.createElementNS(icon.namespaceURI,'path');path.setAttribute('d','M4 4h16v16H4zM8 8h8m-8 4h8m-8 4h5');icon.append(path);tile.prepend(icon);
            }
            if(!tile.querySelector('span')){const description=document.createElement('span');description.textContent=descriptions[route]||'Open '+heading.textContent;tile.append(description);}
            tile.hidden=false;grid.append(tile);
        }
    }
}
function showCurrentView(moveFocus = true) {
    // Coach tasks follow the agreed order; sporters retain their original navigation.
    const coachLinks = coachSidebarOrder.map(view => originalSidebarLinks.find(link => link.dataset.viewLink === view));
    const orderedLinks = previewRole === 'coach'
        ? [...coachLinks, ...originalSidebarLinks.filter(link => !coachLinks.includes(link) && !moreLinks.includes(link))]
        : originalSidebarLinks;
    sidebarNavigation.append(...orderedLinks);
    if (previewRole === 'coach' && window.capReal?.staffRole !== 'FINANCE') {
        moreNavigation.append(...moreLinks);
        sidebarNavigation.insertBefore(moreNavigation, sidebarNavigation.querySelector('.settings-link'));
    } else { moreNavigation.remove(); if(window.capReal?.staffRole==='FINANCE')sidebarNavigation.insertBefore(originalSidebarLinks.find(link=>link.dataset.viewLink==='finance'),sidebarNavigation.querySelector('.settings-link')); }
    const requestedView = window.location.hash.slice(1);
    let allowedViews = previewRole === 'coach' ? ['dashboard', 'settings', 'create-workout', 'saved-workouts', 'exercises', 'athletes', 'coach-health', 'coach-nutrition', 'planning', 'bookings', 'training'] : ['dashboard', 'workouts', 'health', 'nutrition', 'settings', 'planning', 'bookings', 'shop', 'training'];
    document.querySelector('[data-account="payment"]').hidden = previewRole === 'coach';
    if (previewRole === 'coach' && window.capReal?.permissions?.reviewFinance) allowedViews.push('finance');
    if (previewRole === 'coach') allowedViews.push('colleagues');
    if (previewRole === 'coach') allowedViews.push('health');
    if (window.capReal && previewRole === 'coach') {
        const routes = {'create-workout':'WORKOUT_TEMPLATE_MANAGE','saved-workouts':'WORKOUT_VIEW','exercises':'WORKOUT_TEMPLATE_MANAGE',
            athletes:'WORKOUT_ASSIGN','coach-health':'CLIENT_HEALTH','coach-nutrition':'NUTRITION_MANAGE',planning:'CLASS_VIEW',
            bookings:'CLASS_MANAGE',training:'WORKOUT_VIEW',colleagues:'CLASS_VIEW',finance:'FINANCE_VIEW','staff-notes':'NOTE_VIEW'};
        allowedViews = ['dashboard','settings','health',...Object.keys(routes).filter(route=>window.capCan(routes[route]))];
        if(window.capReal.staffRole==='FINANCE')allowedViews=['dashboard','settings','finance'];
        const instructor=window.capReal.staffRole==='INSTRUCTOR';
        sidebarNavigation.querySelector('[data-view-link="planning"]').textContent=instructor?'Mijn uurrooster / Lessen':'Planning';
        sidebarNavigation.querySelector('[data-view-link="saved-workouts"]').textContent=instructor?'Workouts':'Opgeslagen workouts';
        sidebarNavigation.querySelector('[data-view-link="athletes"]').textContent=instructor?'Mijn klanten':'Sporters';
        document.querySelectorAll('#coach .home-tile').forEach(link=>{link.hidden=!allowedViews.includes(link.hash.slice(1));});
    }
    sidebarNavigation.querySelector('[data-view-link="health"]').textContent=previewRole==='coach'?'Mijn gezondheid':'Gezondheid & Progress';
    sidebarNavigation.querySelector('[data-view-link="coach-health"]').textContent='Gezondheid sporters';
    const routeId = allowedViews.includes(requestedView)
        ? requestedView : 'dashboard';
    const moreIsActive = previewRole === 'coach' && moreViews.includes(routeId);
    moreNavigation.classList.toggle('has-active-page', moreIsActive);
    if (moreIsActive) moreNavigation.open = true;
    const viewId = previewRole === 'coach' && routeId === 'dashboard' ? (window.capReal?.staffRole==='FINANCE'?'finance-home':'coach') : routeId;
    document.body.classList.toggle('is-workout-editor', viewId === 'create-workout');

    sidebarNavigation.querySelectorAll('a').forEach((link) => {
        if(window.capReal && previewRole==='coach'){link.hidden=!allowedViews.includes(link.dataset.viewLink);return;}
        if (link.dataset.viewLink === 'finance') {
            link.hidden = !allowedViews.includes('finance');
            return;
        }
        if (link.dataset.viewLink === 'colleagues') { link.hidden = previewRole !== 'coach'; return; }
        if (link.dataset.viewLink === 'health') { link.hidden=false;return; }
        link.hidden = previewRole === 'coach'
            ? !['dashboard', 'settings', 'create-workout', 'saved-workouts', 'exercises', 'athletes', 'coach-health', 'coach-nutrition', 'planning', 'bookings', 'training'].includes(link.dataset.viewLink)
            : link.hasAttribute('data-coach-only');
    });

    syncHomeShortcuts(allowedViews);
    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.id !== viewId;
    });
    document.querySelectorAll('[data-mobile-role]').forEach(link => {
        link.hidden = link.dataset.mobileRole !== previewRole;
    });
    if(window.capReal && previewRole==='coach')document.querySelectorAll('.mobile-bottom-nav [data-view-link]').forEach(link=>{link.hidden=link.dataset.mobileRole==='athlete'||!allowedViews.includes(link.dataset.viewLink);});

    document.querySelectorAll('[data-view-link]').forEach((link) => {
        const isActive = link.dataset.viewLink === routeId;
        link.classList.toggle('active', isActive);
        if (isActive) {
            link.setAttribute('aria-current', 'page');
        } else {
            link.removeAttribute('aria-current');
        }
    });

    setMenuOpen(false);
    if (moveFocus) {
        document.querySelector(`#${viewId}-title`)?.focus();
    }
    document.dispatchEvent(new CustomEvent('cap:viewchange'));
}

window.addEventListener('hashchange', () => showCurrentView());
showCurrentView(false);
