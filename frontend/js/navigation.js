const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');
const sidebar = document.querySelector('.sidebar');
const sidebarToggle = document.querySelector('.sidebar-toggle');
const mobileLayout = window.matchMedia('(max-width: 800px)');
const originalSidebarLinks = Array.from(sidebarNavigation.children);
const coachSidebarOrder = ['dashboard', 'planning', 'bookings', 'create-workout', 'athletes', 'coach-nutrition', 'health'];
const moreViews = ['colleagues', 'finance', 'saved-workouts', 'exercises'];
const moreLinks = moreViews.map(view => originalSidebarLinks.find(link => link.dataset.viewLink === view));
const moreNavigation = document.createElement('details');
moreNavigation.className = 'sidebar-more';
const moreSummary = document.createElement('summary');
moreSummary.textContent = 'Meer';
moreNavigation.append(moreSummary);
let previewRole = window.capReal?.account.role === 'COACH' ? 'coach' : 'athlete';
const mobileMorePage=document.createElement('section');mobileMorePage.id='more';mobileMorePage.dataset.view='';mobileMorePage.hidden=true;
mobileMorePage.setAttribute('aria-labelledby','more-title');
mobileMorePage.innerHTML='<header class="dashboard-header"><h2 id="more-title" tabindex="-1">Meer</h2></header><nav class="home-tiles" aria-label="Overige pagina’s"></nav>';
document.querySelector('main').append(mobileMorePage);

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
    const instructor=window.capReal?.staffRole==='INSTRUCTOR';
    const descriptions={colleagues:'Het team en contactgegevens',finance:'Betalingen en financieel overzicht',settings:'Jouw voorkeuren en instellingen',health:'Jouw metingen en vooruitgang',
        ...(instructor?{planning:'Jouw lessen en lesmomenten',athletes:'Sporters die aan jou gekoppeld zijn','saved-workouts':'Bekijk trainingen en wijs ze toe'}:{})};
    const icons={
        community:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8m8 1a4 4 0 0 1 0 7m5 10v-2a4 4 0 0 0-3-4',
        messages:'M21 4H3v13h5l4 4 4-4h5zM7 8h10M7 12h7',
        colleagues:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8m8 1a4 4 0 0 1 0 7m5 10v-2a4 4 0 0 0-3-4',
        finance:'M4 20h16M6 16v-5m6 5V5m6 11V8',
        health:'M3 12h4l3-7 4 14 3-7h4',
        settings:'M12 3v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6 2.1 2.1m0-12.8-2.1 2.1m-8.6 8.6-2.1 2.1M16 12a4 4 0 1 0-8 0 4 4 0 0 0 8 0'
    };
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
                const path=document.createElementNS(icon.namespaceURI,'path');path.setAttribute('d',icons[route]||'M4 4h16v16H4zM8 8h8m-8 4h8m-8 4h5');icon.append(path);tile.prepend(icon);
            }
            if(!tile.querySelector('span')){const description=document.createElement('span');description.textContent=descriptions[route]||'Open '+heading.textContent;tile.append(description);}
            if(descriptions[route]&&!tile.querySelector('span[id]'))tile.querySelector('span').textContent=descriptions[route];
            tile.hidden=false;grid.append(tile);
        }
    }
}
function syncSidebarIcons() {
    // Explicit route icons avoid inheriting an unrelated Home-tile fallback.
    const fallback = {
        'coach-glenn': 'M4 4h16v12H9l-5 4zM8 8h8m-8 4h5',
        dashboard: 'm3 10 9-7 9 7v10H3zM9 20v-7h6v7',
        planning: 'M4 5h16v16H4zM8 3v4m8-4v4M4 10h16M8 14h2m4 0h2m-8 4h2',
        bookings: 'M4 5h16v16H4zM8 3v4m8-4v4M4 10h16M8 16l3 3 5-5',
        'owner-agenda': 'M9 5H5v16h14V5h-4M9 3h6v4H9zM8 12l2 2 5-5M8 18h8',
        workouts: 'M8 5H5v16h14V5h-3M8 3h8v4H8zM8 12h8m-8 4h5',
        'create-workout': 'M3 7v10m3-12v14M6 12h8m0-7v10m3-8v6m-2 7h6m-3-3v6',
        'saved-workouts': 'M3 7V4h7l2 3h9v14H3zM14 11h4v7l-2-1-2 1z',
        exercises: 'M3 8v8m3-11v14m0-7h12m0-7v14m3-11v8',
        athletes: 'M9 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6M3 21v-3a6 6 0 0 1 12 0v3m1-17a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 4v3',
        colleagues: 'M4 5h16v16H4zM9 3h6v4H9zM12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4m-4 9a4 4 0 0 1 8 0',
        finance: 'M18 5a7 7 0 1 0 0 14M3 10h12M3 14h10',
        health: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8M3 12h5l2-4 3 8 2-4h6',
        nutrition: 'M4 3v5a3 3 0 0 0 6 0V3M7 3v18M19 3c-3 2-4 5-4 9h4m0-9v18',
        community: 'M3 3h14v11H9l-4 4v-4H3zM17 7h4v12h-3v3l-4-3h-3v-5',
        shop: 'M4 7h16l1 14H3zM8 7V6a4 4 0 0 1 8 0v1'
    };
    fallback['coach-health']=fallback.health;fallback['coach-nutrition']=fallback.nutrition;

    for (const link of sidebarNavigation.querySelectorAll('a[data-view-link]')) {
        if (link.classList.contains('settings-link') || link.querySelector('.sidebar-nav-icon')) continue;
        const route = link.dataset.viewLink;
        if(route==='coach-glenn'){
            const robot=document.createElement('img');robot.src='img/coach-glenn-robot.png';robot.alt='';robot.className='sidebar-nav-icon sidebar-glenn-icon';
            link.classList.add('sidebar-with-icon');link.prepend(robot);continue;
        }
        const source = document.querySelector(`.home-tiles a[href="#${route}"] svg`);
        const icon = source ? source.cloneNode(true) : document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        if (!source) {
            const path = document.createElementNS(icon.namespaceURI, 'path');
            path.setAttribute('d', fallback[route] || 'M4 4h16v16H4zM8 8h8m-8 4h8m-8 4h5');
            icon.append(path);
        }
        // Prefer the page symbol over a generic or unrelated Home-tile icon.
        if (fallback[route]) {
            const path = document.createElementNS(icon.namespaceURI, 'path');
            path.setAttribute('d', fallback[route]); icon.replaceChildren(path);
        }
        icon.removeAttribute('id');
        for (const [name, value] of Object.entries({viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':'1.8','stroke-linecap':'round','stroke-linejoin':'round','aria-hidden':'true',focusable:'false'})) icon.setAttribute(name,value);
        icon.classList.add('sidebar-nav-icon');
        link.classList.add('sidebar-with-icon');link.prepend(icon);
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
    if (previewRole === 'coach' && (!window.capReal||window.capReal.permissions?.reviewFinance)) allowedViews.push('finance');
    if (previewRole === 'coach') allowedViews.push('colleagues');
    if (previewRole === 'coach') allowedViews.push('health');
    if (window.capReal && previewRole === 'coach') {
        const routes = {'create-workout':'WORKOUT_TEMPLATE_MANAGE','saved-workouts':'WORKOUT_VIEW','exercises':'WORKOUT_TEMPLATE_MANAGE',
            athletes:'WORKOUT_ASSIGN','coach-health':'CLIENT_HEALTH','coach-nutrition':'NUTRITION_MANAGE',planning:'CLASS_VIEW',
            bookings:'CLASS_MANAGE',training:'WORKOUT_VIEW',colleagues:'CLASS_VIEW',finance:'FINANCE_VIEW','staff-notes':'NOTE_VIEW'};
        allowedViews = ['dashboard','settings','health','nutrition',...Object.keys(routes).filter(route=>window.capCan(routes[route]))];
        if(window.capReal.staffRole==='FINANCE')allowedViews=['dashboard','settings','finance','nutrition'];
        const instructor=window.capReal.staffRole==='INSTRUCTOR';
        sidebarNavigation.querySelector('[data-view-link="planning"]').textContent=instructor?'Mijn uurrooster / Lessen':'Planning';
        sidebarNavigation.querySelector('[data-view-link="saved-workouts"]').textContent=instructor?'Workouts':'Opgeslagen workouts';
        sidebarNavigation.querySelector('[data-view-link="athletes"]').textContent=instructor?'Mijn klanten':'Klanten';
        document.querySelectorAll('#coach .home-tile').forEach(link=>{link.hidden=!allowedViews.includes(link.hash.slice(1));});
    }
    allowedViews.push('coach-glenn');
    const adminBottom=previewRole==='coach'&&['OWNER','MANAGER'].includes(window.capReal?.staffRole);
    const ownerAgendaAllowed = previewRole === 'coach' && (window.capReal ? window.capReal.staffRole === 'OWNER' : true);
    if (ownerAgendaAllowed) allowedViews.push('owner-agenda');
    if(previewRole==='coach'&&!allowedViews.includes('nutrition'))allowedViews.push('nutrition');
    const socialAllowed=!window.capReal||window.capReal.account.role==='ATHLETE'||['OWNER','MANAGER','INSTRUCTOR'].includes(window.capReal.staffRole);
    if(socialAllowed)allowedViews.push('community','messages');
    const ownNutritionLink=sidebarNavigation.querySelector('[data-view-link="nutrition"]');
    ownNutritionLink.textContent='Voeding';
    document.querySelector('.mobile-bottom-nav').classList.toggle('has-admin-tabs',adminBottom);
    document.querySelector('.mobile-bottom-nav').classList.toggle('has-client-tabs',previewRole==='athlete');
    if(adminBottom)allowedViews.push('more');
    sidebarNavigation.querySelector('[data-view-link="health"]').textContent='Gezondheid & Progress';
    sidebarNavigation.querySelector('[data-view-link="coach-health"]').textContent='Gezondheid sporters';
    if(allowedViews.includes('finance'))allowedViews.push('invoices');
    const limited=window.capReal?.fullAccess===false;
    if(limited){allowedViews=['planning','shop'];if(!allowedViews.includes(requestedView))window.history.replaceState(null,'','#planning');}
    const routeId = allowedViews.includes(requestedView)
        ? requestedView : limited?'planning':'dashboard';
    document.querySelector('.welcome-bar').hidden = routeId !== 'dashboard';
    const moreIsActive = previewRole === 'coach' && (moreViews.includes(routeId)||routeId==='invoices');
    moreNavigation.classList.toggle('has-active-page', moreIsActive);
    if (moreIsActive) moreNavigation.open = true;
    const viewId = previewRole === 'coach' && routeId === 'dashboard' ? (window.capReal?.staffRole==='FINANCE'?'finance-home':'coach') : routeId;
    document.body.classList.toggle('is-workout-editor', viewId === 'create-workout');

    sidebarNavigation.querySelectorAll('a').forEach((link) => {
        if(limited){link.hidden=!allowedViews.includes(link.dataset.viewLink);return;}
        if(link.dataset.viewLink==='coach-glenn'){link.hidden=false;return;}
        if(link.dataset.viewLink==='coach-health'){link.hidden=true;return;}
        if(['community','messages'].includes(link.dataset.viewLink)){link.hidden=!socialAllowed;return;}
        if(link.dataset.viewLink==='nutrition'){link.hidden=previewRole==='coach'&&allowedViews.includes('coach-nutrition');return;}
        if(link.dataset.viewLink === 'owner-agenda'){link.hidden = !ownerAgendaAllowed;return;}
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
    syncSidebarIcons();
    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.id !== viewId;
    });
    document.querySelectorAll('[data-mobile-role]').forEach(link => {
        link.hidden = link.dataset.mobileRole !== previewRole;
    });
    if(window.capReal && previewRole==='coach')document.querySelectorAll('.mobile-bottom-nav [data-view-link]').forEach(link=>{link.hidden=link.dataset.mobileRole==='athlete'||!allowedViews.includes(link.dataset.viewLink);});
    document.querySelectorAll('.mobile-bottom-nav [data-admin-bottom]').forEach(link=>{link.hidden=!adminBottom;});
    if(adminBottom)document.querySelector('.mobile-bottom-nav [data-view-link="athletes"]').hidden=true;
    const moreGrid=mobileMorePage.querySelector('nav');moreGrid.replaceChildren();
    if(adminBottom)for(const link of sidebarNavigation.querySelectorAll('a[data-view-link]')){
        if(link.hidden||['dashboard','planning','create-workout','finance'].includes(link.dataset.viewLink))continue;
        const tile=document.createElement('a');tile.className='home-tile';tile.href=link.hash;
        const heading=document.createElement('strong');heading.textContent=link.textContent.trim();tile.append(heading);moreGrid.append(tile);
    }

    document.querySelectorAll('[data-view-link]').forEach((link) => {
        const isActive = link.dataset.viewLink === routeId || (previewRole==='coach'&&link.closest('#sidebar-navigation')&&(
            (link.dataset.viewLink==='health'&&routeId==='coach-health') ||
            (link.dataset.viewLink==='coach-nutrition'&&routeId==='nutrition'&&allowedViews.includes('coach-nutrition')) ||
            (link.dataset.viewLink==='finance'&&routeId==='invoices')));
        link.classList.toggle('active', isActive);
        if (isActive) {
            link.setAttribute('aria-current', 'page');
        } else {
            link.removeAttribute('aria-current');
        }
    });

    if(limited){
        document.querySelectorAll('[data-view-link]').forEach(link=>{link.hidden=!allowedViews.includes(link.dataset.viewLink);});
        for(const id of ['header-settings','header-messages','header-notifications'])document.getElementById(id).hidden=true;
        document.querySelectorAll('[data-account=profile],[data-account=notifications]').forEach(button=>{button.hidden=true;});
    }
    document.querySelector('[data-membership-shop]').hidden=!limited;
    document.querySelector('.mobile-bottom-nav').classList.toggle('is-membership-limited',limited);
    setMenuOpen(false);
    if (moveFocus) {
        document.querySelector(`#${viewId}-title`)?.focus();
    }
    document.dispatchEvent(new CustomEvent('cap:viewchange'));
}

window.addEventListener('hashchange', () => showCurrentView());
showCurrentView(false);
