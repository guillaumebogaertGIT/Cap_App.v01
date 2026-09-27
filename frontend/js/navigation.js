const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');
const sidebar = document.querySelector('.sidebar');
const sidebarToggle = document.querySelector('.sidebar-toggle');
const mobileLayout = window.matchMedia('(max-width: 800px)');

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
    const viewId = ['dashboard', 'workouts', 'settings'].includes(requestedView)
        ? requestedView : 'dashboard';

    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.id !== viewId;
    });

    document.querySelectorAll('[data-view-link]').forEach((link) => {
        const isActive = link.dataset.viewLink === viewId;
        link.classList.toggle('active', isActive);
        if (isActive) {
            link.setAttribute('aria-current', 'page');
        } else {
            link.removeAttribute('aria-current');
        }
    });

    setMenuOpen(false);
    if (moveFocus) {
        document.querySelector(`#${viewId}-title`).focus();
    }
}

document.querySelectorAll('[data-workout]').forEach((button) => {
    button.addEventListener('click', () => {
        document.querySelector('#selected-workout-title').textContent = button.dataset.workout;
        document.querySelector('#selected-workout-description').textContent =
            `Voorbeeldtraining geselecteerd: ${button.dataset.summary}.`;
        document.querySelector('.workout-card .primary-button').textContent = 'Wijzig training';
        window.location.hash = 'dashboard';
    });
});

window.addEventListener('hashchange', () => showCurrentView());
showCurrentView(false);
