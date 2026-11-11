// Presentation only: the training controller owns authorization, saves and completion.
window.capTrainingPlayer = (() => {
    const el=(tag,text,cls)=>{const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;};
    const action=(label,fn,cls='secondary-button')=>{const b=el('button',label,cls);b.type='button';b.onclick=fn;return b;};
    function icon(path){const s=document.createElementNS('http://www.w3.org/2000/svg','svg');s.setAttribute('viewBox','0 0 24 24');s.setAttribute('aria-hidden','true');const p=document.createElementNS(s.namespaceURI,'path');p.setAttribute('d',path);s.append(p);return s;}
    function timeline(exercises,{completed=[],selected=-1,onSelect}={}) {
        const list=el('ol',null,'workout-timeline');list.setAttribute('aria-label','Oefeningen en trainingsblokken');
        const groups=[...new Set(exercises.map(e=>e.supersetGroup).filter(Boolean))];
        exercises.forEach((e,i)=>{const flags=completed[i]||[],done=flags.filter(Boolean).length,full=flags.length>0&&done===flags.length;
            const li=el('li');li.dataset.tone=String(e.supersetGroup?groups.indexOf(e.supersetGroup)%4:0);li.dataset.grouped=String(Boolean(e.supersetGroup));
            const b=action('',()=>onSelect?.(i),'timeline-exercise');b.setAttribute('aria-current',selected===i?'step':'false');
            const mark=el('span',null,'timeline-icon');mark.append(icon(full?'m5 12 4 4L19 6':'M6 5v14M3 8v8m15-11v14m3-11v8M6 12h12'));
            const targets=capWorkoutView.setTargets(e),reps=targets.map(s=>s.reps),prescription=targets.length+' × '+(reps.every(r=>r===reps[0])?reps[0]:reps.join(', '));
            const copy=el('span',null,'timeline-copy');copy.append(el('strong',e.name),el('span',prescription));
            if(e.supersetGroup){const badge=el('span',e.supersetGroup,'timeline-block');badge.setAttribute('aria-label','Blok '+e.supersetGroup);mark.append(badge);}
            const status=full?'Voltooid':done?'Bezig · '+done+'/'+flags.length:'Niet gestart';
            if(full||done)copy.append(el('small',status,'timeline-state'));
            b.setAttribute('aria-label',e.name+', '+prescription+(e.supersetGroup?', blok '+e.supersetGroup:'')+', '+status);
            b.append(mark,copy);li.append(b);list.append(li);
        });return list;
    }
    function render(container,ctx){
        const {draft,onChange,onSave,onSelect,onOverview,onRest,previous,opened}=ctx,index=draft.ui.exerciseIndex,e=draft.planned.exercises[index],actual=draft.exercises[index],flags=draft.ui.completed[index];
        const layout=el('div',null,'workout-player-layout');layout.dataset.exerciseOpen=String(opened);
        const overview=el('section',null,'workout-player-overview');overview.append(el('h4','Jouw oefeningen'),timeline(draft.planned.exercises,{completed:draft.ui.completed,selected:opened?index:-1,onSelect}));
        const detail=el('section',null,'workout-player-detail');detail.hidden=!opened;
        const nav=el('div',null,'player-detail-nav');nav.append(action('Alle oefeningen',onOverview),el('span',(index+1)+' / '+draft.planned.exercises.length));detail.append(nav);
        const title=el('h3',e.name);title.id='active-exercise-title';title.tabIndex=-1;detail.append(title);
        if(e.supersetGroup)detail.append(el('p','Blok '+e.supersetGroup+' · '+draft.planned.exercises.filter(x=>x.supersetGroup===e.supersetGroup).map(x=>x.name).join(' / '),'training-group'));
        detail.append(el('p',capWorkoutView.compact(e)+(e.rir!=null?' · RIR '+e.rir:''),'training-prescribed'));capExerciseVideo.append(detail,e.name);
        const prior=el('p',previous?.text||'','training-previous');prior.id='training-previous';prior.hidden=!prior.textContent;detail.append(prior);
        const form=el('form',null,'set-log');form.noValidate=true;
        const table=el('table',null,'set-log-table');const thead=el('thead'),hr=el('tr');['Set / doel','kg','Reps','RIR','Klaar'].forEach(t=>hr.append(el('th',t)));thead.append(hr);const body=el('tbody');table.append(thead,body);
        actual.sets.forEach((set,j)=>{
            const row=el('tr');row.dataset.done=String(flags[j]);row.dataset.current=String(!flags[j]&&j===flags.findIndex(v=>!v));
            const target=capWorkoutView.setTargets(e)[j],label=el('th');label.scope='row';label.append(el('strong',String(j+1)),el('small',target?capWorkoutView.targetText(target):'Extra set'));row.append(label);
            const feedback=el('span',null,'set-feedback');feedback.id='set-error-'+j;feedback.setAttribute('role','status');label.append(feedback);
            for(const [field,name,max]of [['kg','Gewicht',2000],['reps','Herhalingen',1000],['rir','RIR',10]]){
                const cell=el('td'),input=el('input');input.type='number';input.min=field==='reps'?'1':'0';input.max=String(max);input.step=field==='kg'?'0.01':'1';input.inputMode=field==='kg'?'decimal':'numeric';input.required=field!=='rir';input.value=field==='reps'&&set[field]===0?'':set[field]??'';
                const suggestion=previous?.sets[j]?.[field];input.placeholder=suggestion!=null?String(suggestion):field==='rir'?'—':'…';input.disabled=flags[j]||Boolean(draft.finishedAt);input.setAttribute('aria-label',name+', set '+(j+1));input.setAttribute('aria-describedby',feedback.id);input.oninput=()=>{set[field]=input.value;onChange();};cell.append(input);row.append(cell);
            }
            const cell=el('td');const done=action('',()=>{
                if(flags[j]){flags[j]=false;onChange();ctx.redraw();return;}
                if(!capTrainingState.valid(set)){feedback.textContent='Vul kg en reps in (0 kg mag).';row.querySelector('input:invalid')?.focus();return;}
                flags[j]=true;draft.ui.restUntil=e.restSeconds?Date.now()+Math.min(3600,e.restSeconds)*1000:null;draft.ui.restPausedSeconds=null;onChange();onSave();ctx.redraw();
            },'set-done');done.append(icon('m5 12 4 4L19 6'));done.setAttribute('aria-label',(flags[j]?'Set corrigeren':'Set voltooien')+', set '+(j+1));done.setAttribute('aria-pressed',String(flags[j]));done.disabled=Boolean(draft.finishedAt);cell.append(done);row.append(cell);body.append(row);
        });
        form.append(table);detail.append(form);
        if(previous?.sets.length){const use=action('Vorige waarden overnemen',()=>{actual.sets.forEach((s,j)=>{if(flags[j])return;for(const field of ['kg','reps','rir'])if((s[field]==null||s[field]===''||field==='reps'&&s[field]===0)&&previous.sets[j]?.[field]!=null)s[field]=previous.sets[j][field];});onChange();ctx.redraw();});use.disabled=Boolean(draft.finishedAt);detail.append(el('p','Grijze voorbeeldwaarden zijn suggesties; ze tellen pas mee na invullen en voltooien.','set-suggestion-note'),use);}
        const options=el('details',null,'set-options');options.append(el('summary','Sets aanpassen'));
        const add=action('Set toevoegen',()=>{actual.sets.push({kg:'',reps:''});flags.push(false);onChange();ctx.redraw();});add.disabled=Boolean(draft.finishedAt)||actual.sets.length>=100;options.append(add);
        actual.sets.forEach((s,j)=>{const remove=action('Set '+(j+1)+' verwijderen',()=>{if(flags[j]&&!confirm('Deze voltooide set verwijderen?'))return;actual.sets.splice(j,1);flags.splice(j,1);onChange();ctx.redraw();});remove.disabled=Boolean(draft.finishedAt);options.append(remove);});detail.append(options);
        if(draft.ui.restUntil||draft.ui.restPausedSeconds!=null){const rest=el('section',null,'player-rest');rest.setAttribute('aria-label','Rusttimer');const count=el('strong');count.id='training-rest-count';rest.append(el('span','Rust'),count,action(draft.ui.restPausedSeconds!=null?'Hervatten':'Pauzeren',()=>onRest('pause')),action('+15 sec',()=>onRest('add')),action('Overslaan',()=>onRest('skip')));detail.append(rest);}
        const moves=el('nav',null,'player-exercise-moves');moves.setAttribute('aria-label','Oefening wisselen');const prev=action('Vorige oefening',()=>onSelect(index-1)),next=action('Volgende oefening',()=>onSelect(index+1));prev.disabled=index===0;next.disabled=index===draft.exercises.length-1;moves.append(prev,next);detail.append(moves);
        layout.append(overview,detail);container.append(layout);return form;
    }
    return {timeline,render};
})();
