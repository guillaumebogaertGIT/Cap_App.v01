// Read-only personal training review, kept inside the existing Progress page.
(() => {
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
    const own=()=>window.capAccess?.signedIn&&previewRole==='athlete';
    const root=node('section',null,'personal-progress');root.setAttribute('aria-label','Trainingsprogress');
    const heading=node('div',null,'progress-heading');heading.append(node('h3','Jouw trainingsprogress'));
    const reload=node('button','Vernieuwen','secondary-button');reload.type='button';heading.append(reload);
    const status=node('p');status.setAttribute('role','status');
    const recordsSection=node('section');recordsSection.append(node('h3',"Persoonlijke records"),node('p','Vergelijk dezelfde oefening en hetzelfde aantal herhalingen. Alleen afgeronde trainingen tellen mee.','sample-notice'));
    const label=node('label','Zoek een oefening');const search=node('input');search.type='search';search.placeholder='Bijv. squat of bench press';label.append(search);
    const recordList=node('div',null,'progress-records');recordsSection.append(label,recordList);
    const pastSection=node('section');pastSection.append(node('h3','Vorige workouts'));const count=node('p',null,'sample-notice'),past=node('div',null,'progress-history');
    pastSection.append(count,past);
    root.append(heading,status,recordsSection,pastSection);document.querySelector('#health > header').after(root);
    let sessions=[],records=[],generation=0,controller;
    const fmt=value=>new Intl.NumberFormat('nl-BE',{maximumFractionDigits:2}).format(value);
    const day=value=>window.capDateTime.date(value);
    const normalized=value=>String(value).trim().toLocaleLowerCase('nl-BE').replace(/\s+/g,' ');
    function folder(title,description){const detail=node('details',null,'progress-folder'),summary=node('summary');summary.append(node('strong',title),node('span',description));detail.append(summary);return detail;}
    function category(name){const catalog=window.capExerciseCatalog?.entries?.length?window.capExerciseCatalog.entries:(window.capExerciseDatabase?.exercises||[]);return catalog.find(e=>normalized(e.name)===normalized(name))?.category?.trim()||'Overige oefeningen';}
    function observations(name,reps){const values=[];
        for(const session of sessions)for(const result of session.exercises||[]){const exercise=session.planned?.exercises?.[result.exerciseIndex];
            if(!exercise||normalized(exercise.name)!==normalized(name))continue;
            const sets=(result.sets||[]).filter(s=>s.reps===reps&&Number.isFinite(s.kg));
            if(sets.length)values.push({date:session.finishedAt,kg:Math.max(...sets.map(s=>s.kg)),workout:session.planned.name});
        }return values;
    }
    function renderRecords(){recordList.replaceChildren();const grouped=new Map();
        for(const record of records){if(!normalized(record.exercise).includes(normalized(search.value)))continue;
            const key=normalized(record.exercise);if(!grouped.has(key))grouped.set(key,{name:record.exercise,rows:[]});grouped.get(key).rows.push(record);
        }
        const categories=new Map();
        for(const group of [...grouped.values()].sort((a,b)=>a.name.localeCompare(b.name,'nl-BE'))){const key=category(group.name);if(!categories.has(key))categories.set(key,[]);categories.get(key).push(group);}
        for(const [name,groups]of [...categories].sort((a,b)=>a[0].localeCompare(b[0],'nl-BE'))){
            const categoryFolder=folder(name,groups.length+' oefening'+(groups.length===1?'':'en'));categoryFolder.open=Boolean(search.value.trim());recordList.append(categoryFolder);
            for(const group of groups){
            const detail=node('details',null,'progress-entry');const summary=node('summary');summary.append(node('strong',group.name),node('span',group.rows.length+' rep-record'+(group.rows.length===1?'':'s')));detail.append(summary);
            const body=node('div',null,'progress-entry-body');detail.append(body);let rendered=false;
            detail.addEventListener('toggle',()=>{if(!detail.open||rendered)return;rendered=true;
                for(const record of group.rows.sort((a,b)=>a.reps-b.reps)){
                    const history=observations(record.exercise,record.reps),best=history.find(row=>row.kg===record.kg);
                    const item=node('details',null,'progress-rep');item.append(node('summary',`${record.reps} reps · ${fmt(record.kg)} kg${best?' · '+day(best.date):''}`));
                    item.append(node('p',best?'Laatst geëvenaard of behaald op '+day(best.date):'Datum niet beschikbaar in de geladen geschiedenis.','sample-notice'));
                    const list=node('ul');for(const row of history)list.append(node('li',`${day(row.date)} · ${fmt(row.kg)} kg × ${record.reps} · ${row.workout}`));
                    if(!history.length)list.append(node('li','Geen bijbehorende sets beschikbaar.'));item.append(list);body.append(item);
                }
            });categoryFolder.append(detail);
            }
        }
        if(!grouped.size)recordList.append(node('p',records.length?'Geen oefening gevonden.':'Je PR’s verschijnen na je eerste afgeronde training.','club-empty'));
    }
    function sessionBody(session){const body=node('div',null,'progress-entry-body');
        body.append(node('p',window.capDateTime.dateTime(session.finishedAt)+' · '+Math.round((session.durationSeconds||0)/60)+' minuten','sample-notice'));
        for(const result of session.exercises||[]){const exercise=session.planned?.exercises?.[result.exerciseIndex];if(!exercise)continue;
            body.append(node('h4',exercise.name));const list=node('ul');
            (result.sets||[]).forEach((set,index)=>list.append(node('li',`Set ${index+1}: ${set.reps} reps${set.kg==null?' · gewicht niet ingevuld':' × '+fmt(set.kg)+' kg'}${set.rir==null?'':' · RIR '+set.rir}`)));
            if(!result.sets?.length)list.append(node('li','Overgeslagen'));body.append(list);
        }return body;
    }
    function renderPast(){past.replaceChildren();count.textContent=sessions.length+' afgeronde training'+(sessions.length===1?'':'en');
        const months=new Map();const monthKey=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Brussels',year:'numeric',month:'2-digit'}),monthLabel=new Intl.DateTimeFormat('nl-BE',{timeZone:'Europe/Brussels',month:'long',year:'numeric'});
        for(const session of sessions){const key=monthKey.format(new Date(session.finishedAt));if(!months.has(key))months.set(key,[]);months.get(key).push(session);}
        for(const rows of months.values()){
            const month=folder(monthLabel.format(new Date(rows[0].finishedAt)),rows.length+' training'+(rows.length===1?'':'en'));past.append(month);
            const list=node('div');month.append(list);let shown=0;
            const more=node('button','Meer trainingen in deze maand','secondary-button');more.type='button';month.append(more);
            function reveal(){for(const session of rows.slice(shown,shown+10)){const detail=node('details',null,'progress-entry'),summary=node('summary');
            summary.append(node('strong',session.planned.name),node('span',day(session.finishedAt)+' · '+Math.round((session.durationSeconds||0)/60)+' min'));detail.append(summary);
            detail.addEventListener('toggle',()=>{if(detail.open&&!detail.querySelector('.progress-entry-body'))detail.append(sessionBody(session));});list.append(detail);
            }shown+=10;more.hidden=shown>=rows.length;}
            more.onclick=reveal;month.addEventListener('toggle',()=>{if(month.open&&shown===0)reveal();});
        }
        if(!sessions.length)past.append(node('p','Nog geen afgeronde trainingen. Na het afronden van een workout vind je hier je uitgevoerde sets.','club-empty'));
    }
    async function load(){const current=++generation;controller?.abort();controller=new AbortController();const signal=AbortSignal.any([controller.signal,AbortSignal.timeout(10000)]);
        status.textContent='Je resultaten laden…';reload.disabled=true;sessions=[];records=[];recordList.replaceChildren();past.replaceChildren();count.textContent='';
        try{const get=async path=>{const response=await fetch('http://localhost:8080'+path,{cache:'no-store',signal});if(!response.ok)throw Error();return response.json();};
            const [history,prs]=await Promise.all([get('/api/training'),get('/api/records')]);
            if(current!==generation||!own())return;if(!Array.isArray(history)||!Array.isArray(prs))throw Error();
            sessions=history.filter(s=>s.finishedAt&&s.planned&&Number.isFinite(Date.parse(s.finishedAt))).sort((a,b)=>Date.parse(b.finishedAt)-Date.parse(a.finishedAt));records=prs;renderRecords();renderPast();status.textContent='';
            window.capExerciseCatalog?.load().catch(()=>{});
        }catch(error){if(current===generation&&own())status.textContent='Je resultaten konden niet laden. Kies Vernieuwen om opnieuw te proberen.';}
        finally{if(current===generation)reload.disabled=false;}
    }
    search.addEventListener('input',renderRecords);reload.onclick=load;
    document.addEventListener('cap:exercise-catalog-changed',()=>{if(own()&&location.hash==='#health')renderRecords();});
    document.addEventListener('cap:viewchange',()=>{root.hidden=!own();if(location.hash==='#health'&&own())load();else{generation++;controller?.abort();sessions=[];records=[];recordList.replaceChildren();past.replaceChildren();}});
    document.addEventListener('cap:training-changed',()=>{if(location.hash==='#health'&&own())load();});root.hidden=!own();
})();
