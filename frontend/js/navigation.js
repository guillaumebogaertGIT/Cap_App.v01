const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');
const sidebar = document.querySelector('.sidebar');
const sidebarToggle = document.querySelector('.sidebar-toggle');
const mobileLayout = window.matchMedia('(max-width: 800px)');
let previewRole = 'athlete';

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
    const requestedView = window.location.hash.slice(1);
    const allowedViews = previewRole === 'coach' ? ['dashboard', 'settings', 'create-workout', 'athletes', 'group-classes', 'planning', 'bookings', 'shop', 'training'] : ['dashboard', 'workouts', 'settings', 'planning', 'bookings', 'shop', 'training'];
    const routeId = allowedViews.includes(requestedView)
        ? requestedView : 'dashboard';
    const viewId = previewRole === 'coach' && routeId === 'dashboard' ? 'coach' : routeId;

    sidebarNavigation.querySelectorAll('a').forEach((link) => {
        link.hidden = previewRole === 'coach'
            ? !['dashboard', 'settings', 'athletes', 'group-classes', 'planning', 'bookings', 'shop', 'training'].includes(link.dataset.viewLink)
            : link.hasAttribute('data-coach-only');
    });

    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.id !== viewId;
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
