const menuToggle = document.querySelector('.menu-toggle');
const sidebarNavigation = document.querySelector('#sidebar-navigation');

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
