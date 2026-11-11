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
    const eligible=()=>window.capReal?.fullAccess!==false&&Boolean(window.capAccess?.signedIn)&&(window.capReal?['ATHLETE','COACH'].includes(window.capReal.account.role):['athlete','coach'].includes(previewRole));
    const isCoach=()=>window.capReal?window.capReal.account.role==='COACH':previewRole==='coach';
    let launcher,panel,log,input,context,identity='',notice,mode,disclosure,consentLabel,consent,send,ownWorkouts,ownNutrition,ownProfile,ownBookings,ownMealPlan,dataOptions,dataSummary,coachActionsInput,settingsForm,settingsState,budget,settingsVersion=0,settingsDirty=false,available=false,loading=false,busy=false,history=[],chatGeneration=0;
    let nutritionConversation=null,nutritionRevision=0;
    const nutritionKey=()=>`cap-nutrition-chat-${identity}`;
    function forgetNutrition(){nutritionConversation=null;nutritionRevision=0;try{sessionStorage.removeItem(nutritionKey());}catch{}}
    function rememberNutrition(result){if(!result.conversationId)return;nutritionConversation=result.conversationId;nutritionRevision=result.revision;try{sessionStorage.setItem(nutritionKey(),nutritionConversation);}catch{}}
    function obsoleteNutritionProposals(){for(const review of log.querySelectorAll('[data-nutrition-proposal]')){for(const button of review.querySelectorAll('button'))button.disabled=true;review.dataset.superseded='true';}}
    async function restoreNutrition(){if(!isCoach()||!coachActionsInput.checked)return;let id;try{id=sessionStorage.getItem(nutritionKey());}catch{}if(!id)return;const userKey=identity,generation=chatGeneration;
        try{const result=await window.capReal.request('/api/ai-coach/nutrition-chat/'+id);if(identity!==userKey||generation!==chatGeneration)return;rememberNutrition(result);const reply=node('div',null,'coach-glenn-answer');reply.append(formattedAnswer(result.answer));if(result.proposal)renderProposal(reply,result.proposal,userKey,generation);log.append(reply);}
        catch{if(identity===userKey)forgetNutrition();}
    }
    function applySettings(saved){settingsVersion=saved.version||0;const v=saved.values||{};
        for(const [key,el]of Object.entries({consent,ownProfile,ownWorkouts,ownNutrition,ownBookings,ownMealPlan,coachActions:coachActionsInput}))el.checked=Boolean(v[key]);
        settingsDirty=false;dataSummary.textContent=!v.consent?'Coach Glenn is uit · open instellingen':v.coachActions?'Coachacties met controle vóór opslaan':'Coach Glenn · voorkeuren bewaard';
    }
    function renderProposal(reply,proposal,userKey,generation){
        const review=node('section',null,'coach-glenn-proposal');review.append(node('h3','Controleer vóór opslaan'));
        if(proposal.actions.some(a=>a.type==='save_nutrition_draft'))review.dataset.nutritionProposal=proposal.id;
        for(const action of proposal.actions){const detail=node('details');detail.open=true;detail.append(node('summary',action.summary.replace(/\d{4}-\d{2}-\d{2}/g,value=>window.capDateTime.date(value))));const d=action.data;
            if(action.type==='create_workout'){detail.append(window.capWorkoutView.render(d.workout.exercises));}
            if(['create_meal_plan','save_nutrition_draft'].includes(action.type))for(const day of (d.content?.days||d.days)){const section=node('details');const names=['Maandag','Dinsdag','Woensdag','Donderdag','Vrijdag','Zaterdag','Zondag'];const total={calories:0,proteinG:0,carbsG:0,fatG:0};for(const meal of day.meals)for(const food of meal.foods){const factor=food.amount/(food.unit==='stuk'?1:100);for(const key of Object.keys(total))total[key]+=food[key]*factor;}
                section.append(node('summary',names[day.day]+' · '+Math.round(total.calories)+' kcal · E '+Math.round(total.proteinG)+' g · K '+Math.round(total.carbsG)+' g · V '+Math.round(total.fatG)+' g'));
                for(const meal of day.meals){section.append(node('h4',meal.name));const list=node('ul');for(const f of meal.foods)list.append(node('li',`${f.amount} ${f.unit} ${f.name} · ${f.preparation}`));section.append(list);}detail.append(section);
            }
            if(action.type==='create_lesson')detail.append(node('p','Type: '+d.type+' · groep: '+(d.category||'geen')+' · programma: '+(d.program||'geen')));
            if(action.type==='create_shift'&&d.note)detail.append(node('p',d.note));review.append(detail);
        }
        const status=node('p');status.setAttribute('role','status');const confirm=node('button','Bevestigen en opslaan','primary-button'),cancel=node('button','Annuleren','secondary-button');confirm.type=cancel.type='button';
        confirm.onclick=async()=>{if(!eligible()||identity!==userKey||generation!==chatGeneration)return;confirm.disabled=true;cancel.disabled=true;status.textContent='Opslaan in CAP…';
            try{const result=await window.capReal.request('/api/ai-coach/actions/'+proposal.id+'/confirm','POST',{});if(identity!==userKey||generation!==chatGeneration)return;status.textContent=result.answer;confirm.hidden=true;cancel.hidden=true;history.push({role:'assistant',content:'Bevestigd: '+result.completed.join('; ')});document.dispatchEvent(new CustomEvent('cap:viewchange'));}
            catch(error){status.textContent=error.message||'Niet opgeslagen. Je kunt hetzelfde voorstel opnieuw bevestigen.';confirm.disabled=false;cancel.disabled=false;}
        };
        cancel.onclick=async()=>{cancel.disabled=true;confirm.disabled=true;try{await window.capReal.request('/api/ai-coach/actions/'+proposal.id,'DELETE');status.textContent='Geannuleerd. Er is niets opgeslagen.';cancel.hidden=true;confirm.hidden=true;}catch(error){status.textContent=error.message;cancel.disabled=false;confirm.disabled=false;}};
        review.append(node('p','Alleen de acties hierboven worden uitgevoerd. Het voorstel verloopt na 24 uur.','sample-notice'),confirm,cancel,status);reply.append(review);
    }
    function close(focus=true){if(!panel)return;panel.hidden=true;launcher.setAttribute('aria-expanded','false');if(focus&&!launcher.hidden)launcher.focus();}
    function open(name){if(!eligible()||!panel)return;context.hidden=!name;context.textContent=name?'Bij deze workout: '+name:'';panel.hidden=false;launcher.setAttribute('aria-expanded','true');input.focus();}
    function reset(){chatGeneration++;history=[];log.replaceChildren(node('p',available?'Welkom! Je chat met AI, niet met de menselijke coach Glenn.':'Welkom! Dit is een voorbeeldchat. Probeer een van de vragen hieronder.','sample-notice'));input.value='';}
    async function answer(question,key){
        if(!eligible()||busy||loading)return;
        if(available){
            if(!consent.checked||settingsDirty){close(false);location.hash='coach-glenn';settingsState.textContent='Bewaar je toestemming en voorkeuren voordat je chat.';consent.focus();return;}
            busy=true;send.disabled=true;const userKey=identity,generation=chatGeneration;
            const asked=node('p',question,'coach-glenn-question'),reply=node('div',null,'coach-glenn-answer');reply.setAttribute('role','status');reply.textContent='Glenn antwoordt…';log.append(asked,reply);log.scrollTop=log.scrollHeight;
            try{
                let recent=history.slice(-6);while(recent.length&&new TextEncoder().encode(recent.map(t=>t.content).join('')+question).length>8000)recent=recent.slice(2);
                const nutritionRequest=isCoach()&&coachActionsInput.checked&&window.capCan('NUTRITION_MANAGE')&&((nutritionConversation&&!/(workout|trainingsschema|maak.*les|plan.*les|werkuren)/i.test(question))||/(voedings?(?:schema|plan)|voedingsschema|maaltijdplan|meal\s?plan)/i.test(question));
                if(nutritionRequest)obsoleteNutritionProposals();
                const result=await window.capReal.request(nutritionRequest?'/api/ai-coach/nutrition-chat':'/api/ai-coach','POST',nutritionRequest?{id:crypto.randomUUID(),conversationId:nutritionConversation,revision:nutritionRevision,message:question,history:nutritionConversation?[]:recent.filter(t=>t.role==='user')}:{id:crypto.randomUUID(),message:question,history:recent,consent:true,mode:'auto',ownWorkouts:ownWorkouts.checked,ownNutrition:ownNutrition.checked,ownProfile:ownProfile.checked,ownBookings:ownBookings.checked,ownMealPlan:ownMealPlan.checked});
                if(!eligible()||identity!==userKey||generation!==chatGeneration)return;
                rememberNutrition(result);
                reply.replaceChildren(node('strong','Glenn · AI-assistent','coach-glenn-sender'),formattedAnswer(result.answer));history=[...recent,{role:'user',content:question},{role:'assistant',content:result.answer}];
                if(result.proposal){const contextText=JSON.stringify(result.proposal.actions.map(a=>({type:a.type,data:a.data,summary:a.summary})));const combined=history[history.length-1].content+'\nOnbevestigd voorstel: '+contextText;if(new TextEncoder().encode(combined).length<7800)history[history.length-1].content=combined;renderProposal(reply,result.proposal,userKey,generation);}
                if(result.workout){
                    const draft=result.workout;reply.append(node('strong',draft.name),node('small','AI-concept · nog niet bewaard'));
                    const list=node('ul');for(const exercise of draft.exercises)list.append(node('li',`${exercise.name} · ${exercise.sets} × ${exercise.reps} · ${exercise.restSeconds}s rust`));reply.append(list);
                    const edit=node('button','Open in workouteditor','secondary-button');edit.type='button';edit.onclick=()=>{
                        const event=new CustomEvent('cap:ai-workout-draft',{detail:draft,cancelable:true});
                        if(!document.dispatchEvent(event))close(false);
                    };reply.append(edit);
                }
            }catch(error){if(eligible()&&identity===userKey&&generation===chatGeneration){reply.textContent=error.message||'AI kon niet antwoorden. Probeer later opnieuw.';if(nutritionConversation)await restoreNutrition();}}
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
        if(identity!==key){nutritionConversation=null;nutritionRevision=0;identity=key;close(false);reset();if(coachActionsInput){coachActionsInput.closest('label').hidden=!isCoach();applySettings({version:0,values:{}});if(window.capReal)loadAvailability();else applySettings(window.capStorage.read('glenn-preferences-'+previewRole,{version:0,values:{}}));}}
        launcher.hidden=!eligible();if(launcher.hidden){close(false);reset();if(ownWorkouts){ownWorkouts.checked=false;ownNutrition.checked=false;ownProfile.checked=false;ownBookings.checked=false;ownMealPlan.checked=false;coachActionsInput.checked=false;consent.checked=false;dataSummary.textContent='Eigen gegevens: uit';}}
    }
    async function loadAvailability(){
        if(!window.capReal||!eligible())return;
        loading=true;send.disabled=true;input.disabled=true;
        try{
            const status=await window.capReal.request('/api/ai-coach');available=status.available;if(status.settings)applySettings(status.settings);if(!isCoach()&&!status.clientDataApproved)dataSummary.textContent='Persoonlijke gegevens gebruiken? Geef toestemming bij Voeding.';budget.textContent='Gedeeld testbudget: nog '+status.remainingRequests+' conservatief gereserveerde aanvragen beschikbaar (geen exact OpenAI-saldo).';
            mode.textContent=available?'AI-test via OpenAI':'Voorbeeldchat';
            consentLabel.hidden=!available;
            dataOptions.hidden=!available;
            disclosure.open=false;
            notice.textContent=available?'AI-test via OpenAI. Je vragen en recente chattekst worden naar OpenAI gestuurd. Bij toestemming kunnen relevante profiel-, trainings-, voedings- en gezondheidsgegevens worden gedeeld. Betaalgegevens, wachtwoorden en privéberichten worden niet automatisch gedeeld. CAP bewaart voedingstaakgegevens tot 24 uur na het laatste bericht; OpenAI kan gegevens verwerken volgens zijn bewaarbeleid.': 'Voorbeeldchat · De echte AI-test is voor dit account nog niet beschikbaar. Geen vragen naar een AI-dienst.';
            reset();await restoreNutrition();
        }catch{notice.textContent='AI-status kon niet laden. De voorbeeldchat blijft beschikbaar; er worden geen AI-vragen verstuurd.';}
        finally{loading=false;send.disabled=false;input.disabled=false;}
    }
    document.addEventListener('DOMContentLoaded',()=>{
        launcher=node('button',null,'coach-glenn-launcher');launcher.id='coach-glenn-launcher';launcher.type='button';launcher.hidden=true;launcher.setAttribute('aria-label','Open Coach Glenn (voorbeeldchat)');launcher.title='Coach Glenn';launcher.setAttribute('aria-expanded','false');launcher.setAttribute('aria-controls','coach-glenn-chat');
        const logo=document.createElement('img');logo.src='img/coach-glenn-robot.png';logo.alt='';launcher.append(logo,node('span','AI','coach-glenn-launcher-label'));
        panel=node('aside',null,'coach-glenn-chat');panel.id='coach-glenn-chat';panel.hidden=true;panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','false');panel.setAttribute('aria-labelledby','coach-glenn-title');
        const header=node('header'),title=node('h2','Coach Glenn');title.id='coach-glenn-title';const dismiss=node('button',null,'secondary-button');dismiss.type='button';dismiss.setAttribute('aria-label','Chat sluiten');dismiss.innerHTML='<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>';dismiss.onclick=()=>close();header.append(title,dismiss);
        mode=node('small','Voorbeeldchat','coach-glenn-mode');title.append(mode);
        notice=node('p','Voorbeeldchat · Nog geen AI gekoppeld. Vaste voorbeeldantwoorden, niet geschreven door Glenn. Geen gegevens naar een AI-dienst.','coach-glenn-notice');
        disclosure=node('details',null,'coach-glenn-disclosure');disclosure.append(node('summary','Over Glenn · AI & toestemming'),notice);
        context=node('p',null,'coach-glenn-context');log=node('div',null,'coach-glenn-chat-log');log.setAttribute('role','log');log.setAttribute('aria-label','Voorbeeldgesprek');log.setAttribute('aria-live','polite');log.setAttribute('aria-relevant','additions');log.tabIndex=0;
        const choices=node('div',null,'coach-glenn-quick');choices.setAttribute('aria-label','Voorbeeldvragen');
        for(const [key,question]of examples){const button=node('button',question,'secondary-button');button.type='button';button.dataset.example=key;button.onclick=()=>answer(question,key);choices.append(button);}
        const suggestions=node('details',null,'coach-glenn-suggestions');suggestions.append(node('summary','Voorbeeldvragen'),choices);
        const form=node('form',null,'coach-glenn-chat-form'),label=node('label');input=node('textarea');input.setAttribute('aria-label','Jouw vraag');input.name='question';input.rows=1;input.maxLength=4000;input.required=true;input.placeholder='Vraag over training, voeding of CAP…';label.append(input);send=node('button','Versturen','primary-button');send.type='submit';form.append(label,send);form.onsubmit=async event=>{event.preventDefault();const question=input.value.trim();if(!question||!eligible()||busy||loading)return;await answer(question);if(!available||consent.checked)input.value='';if(!panel.hidden)input.focus();};
        consentLabel=node('label',null,'coach-glenn-consent');consentLabel.hidden=true;consent=node('input');consent.type='checkbox';consentLabel.append(consent,node('span','Ik wil deze AI-test gebruiken en begrijp dat OpenAI mijn chattekst verwerkt.'));
        consent.onchange=()=>{if(consent.checked)disclosure.open=false;};disclosure.append(consentLabel,suggestions);
        dataOptions=node('fieldset',null,'coach-glenn-data-options');dataOptions.hidden=true;dataOptions.append(node('legend','Eigen gegevens delen met OpenAI (optioneel)'));
        function dataChoice(title,description){const label=node('label');const checkbox=node('input');checkbox.type='checkbox';const copy=node('span');copy.append(node('strong',title),node('small',description));label.append(checkbox,copy);dataOptions.append(label);return checkbox;}
        ownWorkouts=dataChoice('Mijn workouts','Deze week: maximaal 3 eigen plannen. Laatste 14 dagen: maximaal 2 afgeronde trainingen met een selectie van oefeningen, herhalingen en kg. Geen cliëntgegevens.');
        ownNutrition=dataChoice('Mijn voeding','Opgeslagen dagdoelen, voedingstotalen van vandaag en maximaal 8 gelogde producten met porties. Geen gewicht of medische gegevens.');
        ownProfile=dataChoice('Mijn profiel','Je naam, opgeslagen doel en trainingservaring. Geen geboortedatum, lichaamsmetingen, foto of contactgegevens.');
        ownBookings=dataChoice('Mijn boekingen','Maximaal 8 eigen actieve boekingen in de komende 14 dagen. Geen deelnemerslijsten of sessienotities.');
        ownMealPlan=dataChoice('Mijn voedingsplan','Het gepubliceerde plan van deze week: maaltijdnamen en opgetelde algemene boodschappen met hoeveelheden. Alleen je eigen plan, geen concepten.');
        if(!isCoach()){for(const choice of [ownWorkouts,ownNutrition,ownProfile,ownBookings,ownMealPlan])choice.closest('label').hidden=true;const info=node('small','Je bepaalt bij Voeding → Voedingsvoorkeuren en toestemming voor AI of je gegevens mogen worden gebruikt.');dataOptions.append(info);}
        dataOptions.append(node('small','Deze voorkeuren blijven bij je CAP-account bewaard nadat je ze opslaat. Uitzetten wist dit gesprek en stopt nieuwe gegevensdeling; eerder verzonden gegevens worden daarmee niet bij OpenAI verwijderd.'));
        dataSummary=node('p','Eigen gegevens: uit','coach-glenn-data-summary');
        function scopeChanged(){settingsDirty=true;settingsState.textContent='Voorkeuren gewijzigd: bewaar om ze te gebruiken.';reset();const active=[ownWorkouts.checked?'workouts':'',ownNutrition.checked?'voeding':'',ownProfile.checked?'profiel':'',ownBookings.checked?'boekingen':'',ownMealPlan.checked?'voedingsplan':''].filter(Boolean);dataSummary.textContent=active.length?'Met eigen '+active.join(' en '):'Eigen gegevens: uit';}
        ownWorkouts.onchange=scopeChanged;ownNutrition.onchange=scopeChanged;ownProfile.onchange=scopeChanged;ownBookings.onchange=scopeChanged;ownMealPlan.onchange=scopeChanged;disclosure.insertBefore(dataOptions,suggestions);
        coachActionsInput=dataChoice('Coachacties voorbereiden','Deel klantnamen en, wanneer de klant dit heeft toegestaan, relevante profiel-, trainings-, boekings-, meet- en voedingsgegevens met OpenAI. Coach Glenn mag acties voorstellen; elke opslag vereist Bevestigen. Je CAP-rechten blijven gelden. Geen betaalgegevens, wachtwoorden of privéberichten.');
        coachActionsInput.onchange=scopeChanged;coachActionsInput.closest('label').hidden=!isCoach();
        const settingsRoot=document.getElementById('coach-glenn-settings');settingsForm=node('form',null,'settings-card');
        const explanation=node('p','OpenAI verwerkt je vragen, recente chattekst en de gegevens waarvoor je hieronder kiest. Je instellingen blijven bewaard bij je CAP-account. CAP bewaart voedingstaakgegevens tot 24 uur na het laatste bericht; actievoorstellen verlopen na 24 uur. Eerder naar OpenAI verzonden gegevens worden door uitschakelen niet teruggehaald.','sample-notice');
        budget=node('p','Het gedeelde testbudget blijft begrensd.','sample-notice');settingsState=node('p');settingsState.setAttribute('role','status');const saveSettings=node('button','Voorkeuren bewaren','primary-button');saveSettings.type='submit';
        settingsForm.append(node('h3','Instellingen & toestemming'),explanation,consentLabel,dataOptions,saveSettings,settingsState,budget);settingsRoot.append(settingsForm);
        const openChat=node('button','Open Coach Glenn','secondary-button');openChat.type='button';openChat.onclick=()=>open();settingsRoot.prepend(openChat);
        if(window.capReal&&window.capCan('STAFF_MANAGE')){const test=node('button','Test AI-verbinding','secondary-button'),result=node('p');test.type='button';result.setAttribute('role','status');test.onclick=async()=>{test.disabled=true;result.textContent='Verbinding testen…';try{const response=await window.capReal.request('/api/ai-coach/diagnostic','POST',{id:crypto.randomUUID()});result.textContent=response.result;}catch(error){result.textContent=error.message||'De verbindingstest is mislukt.';}finally{test.disabled=false;}};settingsRoot.append(test,node('p','Verstuurt één vaste testvraag zonder klantgegevens en gebruikt het gedeelde testbudget.','sample-notice'),result);}
        const settingsLink=node('a','Instellingen op Coach Glenn');settingsLink.href='#coach-glenn';settingsLink.onclick=()=>close(false);disclosure.append(settingsLink);
        settingsForm.onsubmit=async event=>{event.preventDefault();saveSettings.disabled=true;try{
            const values={consent:consent.checked,ownProfile:ownProfile.checked,ownWorkouts:ownWorkouts.checked,ownNutrition:ownNutrition.checked,ownBookings:ownBookings.checked,ownMealPlan:ownMealPlan.checked,coachActions:coachActionsInput.checked&&isCoach()};
            const saved=window.capReal?await window.capReal.request('/api/ai-coach/settings','PUT',{version:settingsVersion,values}):{version:settingsVersion+1,values};
            if(!window.capReal)window.capStorage.write('glenn-preferences-'+previewRole,saved);applySettings(saved);settingsState.textContent='Voorkeuren bewaard. Je kunt Coach Glenn openen.';reset();
        }catch(error){settingsState.textContent=error.message||'Voorkeuren niet bewaard.';}finally{saveSettings.disabled=false;}};
        consent.onchange=scopeChanged;
        if(!window.capReal){consentLabel.hidden=false;dataOptions.hidden=false;applySettings(window.capStorage.read('glenn-preferences-'+previewRole,{version:0,values:{}}));settingsState.textContent='Voorbeeldinstellingen: alleen in deze browser. Geen echte AI-acties.';}
        const footer=node('footer'),coach=node('a','Open Berichten');coach.href='#messages';coach.onclick=()=>close(false);const clear=node('button','Gesprek wissen','secondary-button');clear.type='button';clear.onclick=async()=>{if(nutritionConversation){clear.disabled=true;try{await window.capReal.request('/api/ai-coach/nutrition-chat/'+nutritionConversation,'DELETE');forgetNutrition();}catch(error){input.value=error.message;return;}finally{clear.disabled=false;}}reset();input.focus();};footer.append(coach,clear);
        panel.append(header,disclosure,dataSummary,context,log,form,footer);document.body.append(launcher,panel);
        // On iOS the keyboard shrinks the visual viewport, not always the layout viewport.
        // Resize just the chat so the composer stays above the keyboard.
        function fitVisibleViewport(){const viewport=window.visualViewport;
            if(!viewport)return;
            panel.style.setProperty('--coach-visible-height',viewport.height+'px');
            panel.style.setProperty('--coach-visible-top',viewport.offsetTop+'px');
        }
        window.visualViewport?.addEventListener('resize',fitVisibleViewport);
        window.visualViewport?.addEventListener('scroll',fitVisibleViewport);
        fitVisibleViewport();
        launcher.onclick=()=>panel.hidden?open():close();panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
        document.addEventListener('cap:viewchange',sync);sync();
    });
    window.capCoachGlenn={mount(container,workout){if(!eligible()||!workout?.exercises?.length)return;const button=node('button','Help me mijn workout te begrijpen','secondary-button');button.type='button';button.onclick=()=>{const dialog=container.closest('dialog');if(dialog?.open)dialog.close();open(workout.name);};container.append(button);}};
})();
