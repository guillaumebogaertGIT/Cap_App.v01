// Opt-in AI test; conversations and workout proposals stay ephemeral.
(() => {
    const examples=[
        ['sets','Wat betekenen sets en herhalingen?','Een herhaling is één uitvoering van een oefening. Een set is een reeks herhalingen. Voorbeeld: 3 × 10 betekent drie sets van tien herhalingen. Volg de aantallen en rust die je coach heeft ingevuld.'],
        ['superset','Hoe werkt een superset?','Bij een superset horen meerdere oefeningen bij hetzelfde blok. Je voert ze in de aangegeven volgorde uit. Rust en het aantal rondes hangen af van de opdracht van je coach. Vraag het aan je coach als de opdracht onduidelijk is.'],
        ['weight','Welk gewicht moet ik gebruiken?','Volg het voorgeschreven gewicht. Een percentage verwijst naar de aangegeven referentie of passende rep-PR. Ontbreekt het gewicht of voelt de opdracht niet passend? Laat je coach het controleren. Deze voorbeeldchat kiest of verhoogt geen gewichten.'],
        ['pain','Wat als ik pijn voel?','Stop met een oefening die pijn veroorzaakt en vertel je coach wat je voelt. Deze voorbeeldchat kan geen blessure beoordelen of veilige vervangende oefening kiezen. Vraag bij aanhoudende of zorgwekkende klachten advies aan een zorgverlener.']
    ];
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text)el.textContent=text;if(cls)el.className=cls;return el;};
    // Render a small, safe text format. Provider HTML is always plain text.
    function formattedAnswer(text){
        const content=node('div',null,'coach-glenn-response');let paragraph=null,list=null;
        function inline(el,value){for(const part of value.split(/(\*\*[^*]+\*\*)/g))el.append(part.startsWith('**')&&part.endsWith('**')?node('strong',part.slice(2,-2)):document.createTextNode(part));}
        for(const raw of String(text).split(/\r?\n/)){
            const line=raw.trim();if(!line){paragraph=null;list=null;continue;}
            const item=line.match(/^(?:([-*•])|\d+[.)])\s+(.+)$/);
            if(item){const tag=item[1]?'ul':'ol';if(!list||list.tagName.toLowerCase()!==tag){list=node(tag);content.append(list);}const li=node('li');inline(li,item[2]);list.append(li);paragraph=null;}
            else{list=null;if(!paragraph){paragraph=node('p');content.append(paragraph);}else paragraph.append(document.createElement('br'));inline(paragraph,line.replace(/^#{1,4}\s+/,''));}
        }return content;
    }
    const eligible=()=>Boolean(window.capAccess?.signedIn)&&(window.capReal?['ATHLETE','COACH'].includes(window.capReal.account.role):['athlete','coach'].includes(previewRole));
    let launcher,panel,log,input,context,identity='',notice,mode,disclosure,consentLabel,consent,send,ownWorkouts,ownNutrition,ownProfile,ownBookings,dataOptions,dataSummary,available=false,loading=false,busy=false,history=[],chatGeneration=0;
    function close(focus=true){if(!panel)return;panel.hidden=true;launcher.setAttribute('aria-expanded','false');if(focus&&!launcher.hidden)launcher.focus();}
    function open(name){if(!eligible()||!panel)return;context.hidden=!name;context.textContent=name?'Bij deze workout: '+name:'';panel.hidden=false;launcher.setAttribute('aria-expanded','true');input.focus();}
    function reset(){chatGeneration++;history=[];log.replaceChildren(node('p',available?'Welkom! Je chat met AI, niet met de menselijke coach Glenn.':'Welkom! Dit is een voorbeeldchat. Probeer een van de vragen hieronder.','sample-notice'));input.value='';}
    async function answer(question,key){
        if(!eligible()||busy||loading)return;
        if(available){
            if(!consent.checked){consent.reportValidity();consent.focus();return;}
            busy=true;send.disabled=true;const userKey=identity,generation=chatGeneration;
            const asked=node('p',question,'coach-glenn-question'),reply=node('div',null,'coach-glenn-answer');reply.setAttribute('role','status');reply.textContent='Glenn antwoordt…';log.append(asked,reply);log.scrollTop=log.scrollHeight;
            try{
                let recent=history.slice(-6);while(recent.length&&new TextEncoder().encode(recent.map(t=>t.content).join('')+question).length>8000)recent=recent.slice(2);
                const result=await window.capReal.request('/api/ai-coach','POST',{id:crypto.randomUUID(),message:question,history:recent,consent:true,mode:'auto',ownWorkouts:ownWorkouts.checked,ownNutrition:ownNutrition.checked,ownProfile:ownProfile.checked,ownBookings:ownBookings.checked});
                if(!eligible()||identity!==userKey||generation!==chatGeneration)return;
                reply.replaceChildren(node('strong','Glenn · AI-assistent','coach-glenn-sender'),formattedAnswer(result.answer));history=[...recent,{role:'user',content:question},{role:'assistant',content:result.answer}];
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
        if(identity!==key){identity=key;close(false);reset();if(ownWorkouts){ownWorkouts.checked=false;ownNutrition.checked=false;ownProfile.checked=false;ownBookings.checked=false;dataSummary.textContent='Eigen gegevens: uit';}}
        launcher.hidden=!eligible();if(launcher.hidden){close(false);reset();if(ownWorkouts){ownWorkouts.checked=false;ownNutrition.checked=false;ownProfile.checked=false;ownBookings.checked=false;consent.checked=false;dataSummary.textContent='Eigen gegevens: uit';}}
    }
    async function loadAvailability(){
        if(!window.capReal||!eligible())return;
        loading=true;send.disabled=true;input.disabled=true;
        try{
            const status=await window.capReal.request('/api/ai-coach');available=status.available;
            mode.textContent=available?'AI-test via OpenAI':'Voorbeeldchat';
            consentLabel.hidden=!available;
            dataOptions.hidden=!available;
            disclosure.open=available&&!consent.checked;
            notice.textContent=available?'AI-test via OpenAI. Je vragen en recente chattekst worden naar OpenAI gestuurd, voor coaches ook gedeelde oefennamen. Eigen workouts en voeding worden alleen meegestuurd als je hieronder toestemming geeft. CAP bewaart de chat niet; OpenAI kan gegevens verwerken volgens zijn bewaarbeleid. Geen betalingen, wachtwoorden, privéberichten, coachnotities of medische gegevens.': 'Voorbeeldchat · De echte AI-test is voor dit account nog niet beschikbaar. Geen vragen naar een AI-dienst.';
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
        disclosure=node('details',null,'coach-glenn-disclosure');disclosure.append(node('summary','Over Glenn · AI & toestemming'),notice);
        context=node('p',null,'coach-glenn-context');log=node('div',null,'coach-glenn-chat-log');log.setAttribute('role','log');log.setAttribute('aria-label','Voorbeeldgesprek');log.setAttribute('aria-live','polite');log.setAttribute('aria-relevant','additions');log.tabIndex=0;
        const choices=node('div',null,'coach-glenn-quick');choices.setAttribute('aria-label','Voorbeeldvragen');
        for(const [key,question]of examples){const button=node('button',question,'secondary-button');button.type='button';button.dataset.example=key;button.onclick=()=>answer(question,key);choices.append(button);}
        const suggestions=node('details',null,'coach-glenn-suggestions');suggestions.append(node('summary','Voorbeeldvragen'),choices);
        const form=node('form',null,'coach-glenn-chat-form'),label=node('label');input=node('textarea');input.setAttribute('aria-label','Jouw vraag');input.name='question';input.rows=1;input.maxLength=500;input.required=true;input.placeholder='Vraag over training, voeding of CAP…';label.append(input);send=node('button','Versturen','primary-button');send.type='submit';form.append(label,send);form.onsubmit=async event=>{event.preventDefault();const question=input.value.trim();if(!question||!eligible()||busy||loading)return;await answer(question);if(!available||consent.checked)input.value='';if(!panel.hidden)input.focus();};
        consentLabel=node('label',null,'coach-glenn-consent');consentLabel.hidden=true;consent=node('input');consent.type='checkbox';consent.required=true;consentLabel.append(consent,node('span','Ik wil deze AI-test gebruiken en begrijp dat OpenAI mijn chattekst verwerkt.'));
        consent.onchange=()=>{if(consent.checked)disclosure.open=false;};disclosure.append(consentLabel,suggestions);
        dataOptions=node('fieldset',null,'coach-glenn-data-options');dataOptions.hidden=true;dataOptions.append(node('legend','Eigen gegevens delen met OpenAI (optioneel)'));
        function dataChoice(title,description){const label=node('label');const checkbox=node('input');checkbox.type='checkbox';const copy=node('span');copy.append(node('strong',title),node('small',description));label.append(checkbox,copy);dataOptions.append(label);return checkbox;}
        ownWorkouts=dataChoice('Mijn workouts','Deze week: maximaal 3 eigen plannen. Laatste 14 dagen: maximaal 2 afgeronde trainingen met een selectie van oefeningen, herhalingen en kg. Geen cliëntgegevens.');
        ownNutrition=dataChoice('Mijn voeding','Opgeslagen dagdoelen, voedingstotalen van vandaag en maximaal 8 gelogde producten met porties. Geen gewicht of medische gegevens.');
        ownProfile=dataChoice('Mijn profiel','Je naam, opgeslagen doel en trainingservaring. Geen geboortedatum, lichaamsmetingen, foto of contactgegevens.');
        ownBookings=dataChoice('Mijn boekingen','Maximaal 8 eigen actieve boekingen in de komende 14 dagen. Geen deelnemerslijsten of sessienotities.');
        dataOptions.append(node('small','Geldt voor je volgende vragen in dit gesprek. Uitzetten wist dit gesprek en stopt nieuwe gegevensdeling; eerder verzonden gegevens worden daarmee niet bij OpenAI verwijderd.'));
        dataSummary=node('p','Eigen gegevens: uit','coach-glenn-data-summary');
        function scopeChanged(){reset();const active=[ownWorkouts.checked?'workouts':'',ownNutrition.checked?'voeding':'',ownProfile.checked?'profiel':'',ownBookings.checked?'boekingen':''].filter(Boolean);dataSummary.textContent=active.length?'Met eigen '+active.join(' en '):'Eigen gegevens: uit';}
        ownWorkouts.onchange=scopeChanged;ownNutrition.onchange=scopeChanged;ownProfile.onchange=scopeChanged;ownBookings.onchange=scopeChanged;disclosure.insertBefore(dataOptions,suggestions);
        const footer=node('footer'),coach=node('a','Open Berichten');coach.href='#messages';coach.onclick=()=>close(false);const clear=node('button','Gesprek wissen','secondary-button');clear.type='button';clear.onclick=()=>{reset();input.focus();};footer.append(coach,clear);
        panel.append(header,disclosure,dataSummary,context,log,form,footer);document.body.append(launcher,panel);
        launcher.onclick=()=>panel.hidden?open():close();panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
        document.addEventListener('cap:viewchange',sync);sync();loadAvailability();
    });
    window.capCoachGlenn={mount(container,workout){if(!eligible()||!workout?.exercises?.length)return;const button=node('button','Help me mijn workout te begrijpen','secondary-button');button.type='button';button.onclick=()=>{const dialog=container.closest('dialog');if(dialog?.open)dialog.close();open(workout.name);};container.append(button);}};
})();
