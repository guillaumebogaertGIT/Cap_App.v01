// Shared keyboard and native-form affordances; feature validation stays in its module.
(() => {
    const errors=new WeakMap();let sequence=0;
    document.addEventListener('click',event=>{
        if(!event.target.closest('.skip-link'))return;
        event.preventDefault();const main=document.getElementById('main-content');main.focus();main.scrollIntoView({block:'start'});
    });
    function validationText(input){const v=input.validity;if(v.customError)return input.validationMessage;
        if(v.valueMissing)return input.tagName==='SELECT'?'Maak een keuze.':'Vul dit veld in.';
        if(v.typeMismatch)return input.type==='email'?'Vul een geldig e-mailadres in.':'Controleer de invoer.';
        if(v.rangeUnderflow)return 'Gebruik minstens '+input.min+'.';if(v.rangeOverflow)return 'Gebruik maximaal '+input.max+'.';
        if(v.tooLong)return 'Gebruik maximaal '+input.maxLength+' tekens.';if(v.badInput)return 'Vul een geldig getal in.';
        return 'Controleer de invoer en de aangegeven notatie.';
    }
    function clear(input){const error=errors.get(input);if(!error)return;
        input.removeAttribute('aria-invalid');const descriptions=(input.getAttribute('aria-describedby')||'').split(/\s+/).filter(id=>id&&id!==error.id);
        if(descriptions.length)input.setAttribute('aria-describedby',descriptions.join(' '));else input.removeAttribute('aria-describedby');error.remove();errors.delete(input);
    }
    document.addEventListener('invalid',event=>{
        const input=event.target;if(!input.matches('input,select,textarea')||!input.closest('main,dialog'))return;
        clear(input);const message=document.createElement('span');message.className='field-error';message.id='cap-field-error-'+(++sequence);message.textContent=validationText(input);
        input.setAttribute('aria-invalid','true');input.setAttribute('aria-describedby',((input.getAttribute('aria-describedby')||'')+' '+message.id).trim());input.after(message);errors.set(input,message);
    },true);
    document.addEventListener('input',event=>{if(errors.has(event.target)){if(event.target.validity.valid)clear(event.target);else errors.get(event.target).textContent=validationText(event.target);}});
    document.addEventListener('change',event=>{if(errors.has(event.target)&&event.target.validity.valid)clear(event.target);});
    document.addEventListener('reset',event=>{for(const input of event.target.querySelectorAll('[aria-invalid]'))clear(input);});
})();
