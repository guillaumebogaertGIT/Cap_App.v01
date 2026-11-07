// Fixed-answer prototype: no AI provider, network requests or stored conversations.
(() => {
    const examples=[
        ['sets','Wat betekenen sets en herhalingen?','Een herhaling is één uitvoering van een oefening. Een set is een reeks herhalingen. Voorbeeld: 3 × 10 betekent drie sets van tien herhalingen. Volg de aantallen en rust die je coach heeft ingevuld.'],
        ['superset','Hoe werkt een superset?','Bij een superset horen meerdere oefeningen bij hetzelfde blok. Je voert ze in de aangegeven volgorde uit. Rust en het aantal rondes hangen af van de opdracht van je coach. Vraag het aan je coach als de opdracht onduidelijk is.'],
        ['weight','Welk gewicht moet ik gebruiken?','Volg het voorgeschreven gewicht. Een percentage verwijst naar de aangegeven referentie of passende rep-PR. Ontbreekt het gewicht of voelt de opdracht niet passend? Laat je coach het controleren. Deze voorbeeldchat kiest of verhoogt geen gewichten.'],
        ['pain','Wat als ik pijn voel?','Stop met een oefening die pijn veroorzaakt en vertel je coach wat je voelt. Deze voorbeeldchat kan geen blessure beoordelen of veilige vervangende oefening kiezen. Vraag bij aanhoudende of zorgwekkende klachten advies aan een zorgverlener.']
    ];
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text)el.textContent=text;if(cls)el.className=cls;return el;};
    const eligible=()=>Boolean(window.capAccess?.signedIn)&&(window.capReal?['ATHLETE','COACH'].includes(window.capReal.account.role):['athlete','coach'].includes(previewRole));
    let launcher,panel,log,input,context,identity='';
    function close(focus=true){if(!panel)return;panel.hidden=true;launcher.setAttribute('aria-expanded','false');if(focus&&!launcher.hidden)launcher.focus();}
    function open(name){if(!eligible()||!panel)return;context.textContent=name?'Bij deze workout: '+name:'Voor cliënten en coaches. Probeer een voorbeeldvraag of typ je vraag.';panel.hidden=false;launcher.setAttribute('aria-expanded','true');input.focus();}
    function reset(){log.replaceChildren(node('p','Welkom! Dit is een voorbeeldchat. Probeer een van de vragen hieronder.','sample-notice'));input.value='';}
    function answer(question,key){
        if(!eligible())return;
        const q=question.toLocaleLowerCase('nl-BE');
        const kind=key||(/pijn|blessure|injury|pain/.test(q)?'pain':/superset/.test(q)?'superset':/gewicht|weight|procent/.test(q)?'weight':/sets?|herhaling|reps/.test(q)?'sets':'');
        const example=examples.find(row=>row[0]===kind);
        const asked=node('p',question,'coach-glenn-question'),reply=node('div',null,'coach-glenn-answer');
        reply.append(node('strong','Voorbeeldantwoord'),node('p',example?example[2]:'Er is nog geen AI verbonden die vrij op jouw vraag kan antwoorden. Probeer een voorbeeldvraag, of open Berichten om je menselijke coach iets te vragen.'));
        log.append(asked,reply);while(log.children.length>21){log.children[1]?.remove();log.children[1]?.remove();}log.scrollTop=log.scrollHeight;
    }
    function sync(){
        const key=window.capReal?.account.id||String(window.capAccess?.signedIn)+':'+previewRole;
        if(identity!==key){identity=key;close(false);reset();}
        launcher.hidden=!eligible();if(launcher.hidden){close(false);reset();}
    }
    document.addEventListener('DOMContentLoaded',()=>{
        launcher=node('button',null,'coach-glenn-launcher');launcher.id='coach-glenn-launcher';launcher.type='button';launcher.hidden=true;launcher.setAttribute('aria-label','Open AI Coach Glenn (voorbeeldchat)');launcher.title='AI Coach Glenn';launcher.setAttribute('aria-expanded','false');launcher.setAttribute('aria-controls','coach-glenn-chat');
        const logo=document.createElement('img');logo.src='img/coach-glenn-robot.png';logo.alt='';launcher.append(logo,node('span','AI','coach-glenn-launcher-label'));
        panel=node('aside',null,'coach-glenn-chat');panel.id='coach-glenn-chat';panel.hidden=true;panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','false');panel.setAttribute('aria-labelledby','coach-glenn-title');
        const header=node('header'),title=node('h2','AI Coach Glenn');title.id='coach-glenn-title';const dismiss=node('button',null,'secondary-button');dismiss.type='button';dismiss.setAttribute('aria-label','Chat sluiten');dismiss.innerHTML='<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>';dismiss.onclick=()=>close();header.append(title,dismiss);
        const notice=node('p','Voorbeeldchat · Nog geen AI gekoppeld. Vaste voorbeeldantwoorden, niet geschreven door Glenn. Geen gegevens naar een AI-dienst.','coach-glenn-notice');
        context=node('p',null,'coach-glenn-context');log=node('div',null,'coach-glenn-chat-log');log.setAttribute('role','log');log.setAttribute('aria-label','Voorbeeldgesprek');log.setAttribute('aria-live','polite');log.setAttribute('aria-relevant','additions');log.tabIndex=0;
        const choices=node('div',null,'coach-glenn-quick');choices.setAttribute('aria-label','Voorbeeldvragen');
        for(const [key,question]of examples){const button=node('button',question,'secondary-button');button.type='button';button.dataset.example=key;button.onclick=()=>answer(question,key);choices.append(button);}
        const form=node('form',null,'coach-glenn-chat-form'),label=node('label','Jouw vraag');input=node('textarea');input.name='question';input.rows=2;input.maxLength=500;input.required=true;input.placeholder='Typ een vraag om de voorbeeldchat te proberen';label.append(input);const send=node('button','Versturen','primary-button');send.type='submit';form.append(label,send);form.onsubmit=event=>{event.preventDefault();const question=input.value.trim();if(!question||!eligible())return;answer(question);input.value='';input.focus();};
        const footer=node('footer'),coach=node('a','Open Berichten');coach.href='#messages';coach.onclick=()=>close(false);const clear=node('button','Gesprek wissen','secondary-button');clear.type='button';clear.onclick=()=>{reset();input.focus();};footer.append(coach,clear);
        panel.append(header,notice,context,log,choices,form,footer);document.body.append(launcher,panel);
        launcher.onclick=()=>panel.hidden?open():close();panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
        document.addEventListener('cap:viewchange',sync);sync();
    });
    window.capCoachGlenn={mount(container,workout){if(!eligible()||!workout?.exercises?.length)return;const button=node('button','Help me mijn workout te begrijpen','secondary-button');button.type='button';button.onclick=()=>{const dialog=container.closest('dialog');if(dialog?.open)dialog.close();open(workout.name);};container.append(button);}};
})();
