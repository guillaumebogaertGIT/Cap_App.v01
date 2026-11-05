// Give static and dynamically created dialogs the same accessible close control.
(() => {
    function enhance(dialog) {
        if (!dialog.matches('dialog')) return;
        if (dialog.querySelector('.session-dialog-close')) return;

        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'secondary-button session-dialog-close';
        close.setAttribute('aria-label', 'Sluiten');
        close.title = 'Sluiten';
        close.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>';
        close.addEventListener('click', () => {
            // Reuse existing cancellation logic (including disabled saving controls).
            const existing = [...dialog.querySelectorAll('button')].find(button =>
                button !== close && /^(sluiten|annuleren|verder bewerken)$/i.test(button.textContent.trim()));
            if (existing) {
                existing.click();
                return;
            }
            if (dialog.dispatchEvent(new Event('cancel', { cancelable: true }))) dialog.close();
        });

        let header = dialog.querySelector('.session-dialog-header');
        if (!header) {
            header = document.createElement('div');
            header.className = 'session-dialog-header';
            const heading = dialog.querySelector('h2');
            if (heading) header.append(heading);
            dialog.prepend(header);
        }
        header.append(close);
    }

    document.querySelectorAll('dialog').forEach(enhance);
    new MutationObserver(records => {
        for (const record of records) {
            if (record.type === 'attributes') enhance(record.target);
            for (const node of record.addedNodes) {
                if (node.nodeType !== Node.ELEMENT_NODE) continue;
                if (node.matches('dialog')) enhance(node);
                node.querySelectorAll('dialog').forEach(enhance);
            }
        }
    }).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['open'] });
})();
