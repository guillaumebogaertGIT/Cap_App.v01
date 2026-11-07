// Opt-in AI test; conversations and workout proposals stay ephemeral.
(() => {
    const examples=[
        ['sets','Wat betekenen sets en herhalingen?','Een herhaling is één uitvoering van een oefening. Een set is een reeks herhalingen. Voorbeeld: 3 × 10 betekent drie sets van tien herhalingen. Volg de aantallen en rust die je coach heeft ingevuld.'],
        ['superset','Hoe werkt een superset?','Bij een superset horen meerdere oefeningen bij hetzelfde blok. Je voert ze in de aangegeven volgorde uit. Rust en het aantal rondes hangen af van de opdracht van je coach. Vraag het aan je coach als de opdracht onduidelijk is.'],
        ['weight','Welk gewicht moet ik gebruiken?','Volg het voorgeschreven gewicht. Een percentage verwijst naar de aangegeven referentie of passende rep-PR. Ontbreekt het gewicht of voelt de opdracht niet passend? Laat je coach het controleren. Deze voorbeeldchat kiest of verhoogt geen gewichten.'],
        ['pain','Wat als ik pijn voel?','Stop met een oefening die pijn veroorzaakt en vertel je coach wat je voelt. Deze voorbeeldchat kan geen blessure beoordelen of veilige vervangende oefening kiezen. Vraag bij aanhoudende of zorgwekkende klachten advies aan een zorgverlener.']
    ];
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text)el.textContent=text;if(cls)el.className=cls;return el;};
    const eligible=()=>Boolean(window.capAccess?.signedIn)&&(window.capReal?['ATHLETE','COACH'].includes(window.capReal.account.role):['athlete','coach'].includes(previewRole));
    let launcher,panel,log,input,context,identity='',notice,mode,taskMode,modeLabel,consentLabel,consent,send,available=false,loading=false,busy=false,history=[],chatGeneration=0;
    function close(focus=true){if(!panel)return;panel.hidden=true;launcher.setAttribute('aria-expanded','false');if(focus&&!launcher.hidden)launcher.focus();}
    function open(name){if(!eligible()||!panel)return;context.textContent=name?'Bij deze workout: '+name:'Voor cliënten en coaches. Probeer een voorbeeldvraag of typ je vraag.';panel.hidden=false;launcher.setAttribute('aria-expanded','true');input.focus();}
    function reset(){chatGeneration++;history=[];log.replaceChildren(node('p',available?'Welkom! Je chat met AI, niet met de menselijke coach Glenn.':'Welkom! Dit is een voorbeeldchat. Probeer een van de vragen hieronder.','sample-notice'));input.value='';}
    async function answer(question,key){
        if(!eligible()||busy||loading)return;
        if(available){
            if(!consent.checked){consent.reportValidity();consent.focus();return;}
            busy=true;send.disabled=true;const userKey=identity,generation=chatGeneration;
            const asked=node('p',question,'coach-glenn-question'),reply=node('div',null,'coach-glenn-answer');reply.setAttribute('role','status');reply.textContent='AI antwoordt…';log.append(asked,reply);log.scrollTop=log.scrollHeight;
            try{
                let recent=history.slice(-6);while(recent.length&&new TextEncoder().encode(recent.map(t=>t.content).join('')+question).length>8000)recent=recent.slice(2);
                const result=await window.capReal.request('/api/ai-coach','POST',{id:crypto.randomUUID(),message:question,history:recent,consent:true,mode:taskMode.value});
                if(!eligible()||identity!==userKey||generation!==chatGeneration)return;
                reply.replaceChildren(node('strong','AI Coach Glenn'),node('p',result.answer));history=[...recent,{role:'user',content:question},{role:'assistant',content:result.answer}];
                if(result.workout){
                    const draft=result.workout;reply.append(node('strong',draft.name),node('small','AI-concept · nog niet bewaard'));
                    const list=node('ul');for(const exercise of draft.exercises)list.append(node('li',`${exercise.name} · ${exercise.sets} × ${exercise.reps} · ${exercise.restSeconds}s rust`));reply.append(list);
                    const edit=node('button','Open in workouteditor','secondary-button');edit.type='button';edit.onclick=()=>{
                        const event=new CustomEvent('cap:ai-workout-draft',{detail:draft,cancelable:true});
                        if(!document.dispatchEvent(event))close(false);
                    };reply.append(edit);
                }
            }catch(error){if(eligible()&&identity===userKey&&generation===chatGeneration)reply.textContent=error.message||'AI kon niet antwoorden. Probeer later opnieuw.';}
            finally{busy=false;send.disabled=false;log.scrollTop=log.scrollHeight;}
            return;
        }
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
    async function loadAvailability(){
        if(!window.capReal||!eligible())return;
        loading=true;send.disabled=true;input.disabled=true;
        try{
            const status=await window.capReal.request('/api/ai-coach');available=status.available;modeLabel.hidden=!status.canDraft;
            mode.textContent=available?'AI-test via OpenAI':'Voorbeeldchat';
            consentLabel.hidden=!available;
            notice.textContent=available?'AI-test via OpenAI. Je vragen en recente chattekst worden naar OpenAI gestuurd. Bij Workoutconcept ook gedeelde oefennamen. CAP slaat de chat niet op. Geen automatische toegang tot persoonlijke workouts, voeding of gezondheid. Deel hier geen gevoelige gegevens.': 'Voorbeeldchat · De echte AI-test is voor dit account nog niet beschikbaar. Geen vragen naar een AI-dienst.';
            reset();
        }catch{notice.textContent='AI-status kon niet laden. De voorbeeldchat blijft beschikbaar; er worden geen AI-vragen verstuurd.';}
        finally{loading=false;send.disabled=false;input.disabled=false;}
    }
    document.addEventListener('DOMContentLoaded',()=>{
        launcher=node('button',null,'coach-glenn-launcher');launcher.id='coach-glenn-launcher';launcher.type='button';launcher.hidden=true;launcher.setAttribute('aria-label','Open AI Coach Glenn (voorbeeldchat)');launcher.title='AI Coach Glenn';launcher.setAttribute('aria-expanded','false');launcher.setAttribute('aria-controls','coach-glenn-chat');
        const logo=document.createElement('img');logo.src='img/coach-glenn-robot.png';logo.alt='';launcher.append(logo,node('span','AI','coach-glenn-launcher-label'));
        panel=node('aside',null,'coach-glenn-chat');panel.id='coach-glenn-chat';panel.hidden=true;panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','false');panel.setAttribute('aria-labelledby','coach-glenn-title');
        const header=node('header'),title=node('h2','AI Coach Glenn');title.id='coach-glenn-title';const dismiss=node('button',null,'secondary-button');dismiss.type='button';dismiss.setAttribute('aria-label','Chat sluiten');dismiss.innerHTML='<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>';dismiss.onclick=()=>close();header.append(title,dismiss);
        mode=node('small','Voorbeeldchat','coach-glenn-mode');title.append(mode);
        notice=node('p','Voorbeeldchat · Nog geen AI gekoppeld. Vaste voorbeeldantwoorden, niet geschreven door Glenn. Geen gegevens naar een AI-dienst.','coach-glenn-notice');
        const disclosure=node('details',null,'coach-glenn-disclosure');disclosure.append(node('summary','Over deze AI-test'),notice);
        modeLabel=node('label','Modus','coach-glenn-task-mode');modeLabel.hidden=true;taskMode=node('select');taskMode.append(new Option('Chat','chat'),new Option('Workoutconcept','workout'));modeLabel.append(taskMode);
        taskMode.onchange=()=>{reset();input.placeholder=taskMode.value==='workout'?'Bijv. full body voor beginners, halters en machines, 4 oefeningen':'Typ je vraag';};
        context=node('p',null,'coach-glenn-context');log=node('div',null,'coach-glenn-chat-log');log.setAttribute('role','log');log.setAttribute('aria-label','Voorbeeldgesprek');log.setAttribute('aria-live','polite');log.setAttribute('aria-relevant','additions');log.tabIndex=0;
        const choices=node('div',null,'coach-glenn-quick');choices.setAttribute('aria-label','Voorbeeldvragen');
        for(const [key,question]of examples){const button=node('button',question,'secondary-button');button.type='button';button.dataset.example=key;button.onclick=()=>answer(question,key);choices.append(button);}
        const suggestions=node('details',null,'coach-glenn-suggestions');suggestions.append(node('summary','Voorbeeldvragen'),choices);
        const form=node('form',null,'coach-glenn-chat-form'),label=node('label','Jouw vraag');input=node('textarea');input.name='question';input.rows=2;input.maxLength=500;input.required=true;input.placeholder='Typ je vraag';label.append(input);send=node('button','Versturen','primary-button');send.type='submit';form.append(label,send);form.onsubmit=async event=>{event.preventDefault();const question=input.value.trim();if(!question||!eligible()||busy||loading)return;await answer(question);if(!available||consent.checked)input.value='';if(!panel.hidden)input.focus();};
        consentLabel=node('label',null,'coach-glenn-consent');consentLabel.hidden=true;consent=node('input');consent.type='checkbox';consent.required=true;consentLabel.append(consent,node('span','Ik wil deze AI-test gebruiken en begrijp dat OpenAI mijn chattekst verwerkt.'));
        const footer=node('footer'),coach=node('a','Open Berichten');coach.href='#messages';coach.onclick=()=>close(false);const clear=node('button','Gesprek wissen','secondary-button');clear.type='button';clear.onclick=()=>{reset();input.focus();};footer.append(coach,clear);
        panel.append(header,disclosure,modeLabel,context,log,suggestions,consentLabel,form,footer);document.body.append(launcher,panel);
        launcher.onclick=()=>panel.hidden?open():close();panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
        document.addEventListener('cap:viewchange',sync);sync();loadAvailability();
    });
    window.capCoachGlenn={mount(container,workout){if(!eligible()||!workout?.exercises?.length)return;const button=node('button','Help me mijn workout te begrijpen','secondary-button');button.type='button';button.onclick=()=>{const dialog=container.closest('dialog');if(dialog?.open)dialog.close();open(workout.name);};container.append(button);}};
})();
