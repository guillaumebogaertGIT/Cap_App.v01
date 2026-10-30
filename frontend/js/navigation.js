const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');
const sidebar = document.querySelector('.sidebar');
const sidebarToggle = document.querySelector('.sidebar-toggle');
const mobileLayout = window.matchMedia('(max-width: 800px)');
const originalSidebarLinks = Array.from(sidebarNavigation.children);
const coachSidebarOrder = ['dashboard', 'planning', 'bookings', 'create-workout', 'exercises', 'saved-workouts', 'athletes', 'coach-nutrition'];
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
function showCurrentView(moveFocus = true) {
    // Coach tasks follow the agreed order; sporters retain their original navigation.
    const coachLinks = coachSidebarOrder.map(view => originalSidebarLinks.find(link => link.dataset.viewLink === view));
    const orderedLinks = previewRole === 'coach'
        ? [...coachLinks, ...originalSidebarLinks.filter(link => !coachLinks.includes(link))]
        : originalSidebarLinks;
    if (orderedLinks.some((link, index) => sidebarNavigation.children[index] !== link)) {
        sidebarNavigation.append(...orderedLinks);
    }
    const requestedView = window.location.hash.slice(1);
    const allowedViews = previewRole === 'coach' ? ['dashboard', 'settings', 'create-workout', 'saved-workouts', 'exercises', 'athletes', 'coach-health', 'coach-nutrition', 'planning', 'bookings', 'training'] : ['dashboard', 'workouts', 'health', 'nutrition', 'settings', 'planning', 'bookings', 'shop', 'training'];
    document.querySelector('[data-account="payment"]').hidden = previewRole === 'coach';
    if (previewRole === 'coach' && window.capReal?.permissions?.reviewFinance) allowedViews.push('finance');
    const routeId = allowedViews.includes(requestedView)
        ? requestedView : 'dashboard';
    const viewId = previewRole === 'coach' && routeId === 'dashboard' ? 'coach' : routeId;
    document.body.classList.toggle('is-workout-editor', viewId === 'create-workout');

    sidebarNavigation.querySelectorAll('a').forEach((link) => {
        if (link.dataset.viewLink === 'finance') {
            link.hidden = !allowedViews.includes('finance');
            return;
        }
        link.hidden = previewRole === 'coach'
            ? !['dashboard', 'settings', 'create-workout', 'saved-workouts', 'exercises', 'athletes', 'coach-health', 'coach-nutrition', 'planning', 'bookings', 'training'].includes(link.dataset.viewLink)
            : link.hasAttribute('data-coach-only');
    });

    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.id !== viewId;
    });
    document.querySelectorAll('[data-mobile-role]').forEach(link => {
        link.hidden = link.dataset.mobileRole !== previewRole;
    });

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
