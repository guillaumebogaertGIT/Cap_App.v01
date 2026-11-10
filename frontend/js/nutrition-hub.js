// Small task launcher; panels stay mounted so switching never discards a draft.
document.addEventListener('DOMContentLoaded',()=>{
    const page=document.getElementById('coach-nutrition');
    const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;};
    const menu=node('nav',null,'nutrition-option-grid');menu.setAttribute('aria-label','Voedingsopties');
    const bar=node('div',null,'nutrition-option-bar'),back=node('button','Alle voedingsopties','secondary-button'),title=node('h3');back.type='button';title.tabIndex=-1;bar.append(back,title);bar.hidden=true;
    const options=[
        ['plan','Voedingsplan maken','Maaltijden en porties per week','M4 3h16v18H4zM8 7h8M8 12h3M8 17h8'],
        ['calculator','Doelen berekenen','Bereken en controleer dagdoelen','M5 3h14v18H5zM8 7h8M8 12h1m6 0h1M8 17h1m6 0h1'],
        ['targets','Dagdoelen bekijken','Bekijk of pas doelen handmatig aan','M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10M12 11v2'],
        ['diary','Dagboek bekijken','Bekijk wat je klant heeft gegeten','M4 5h16v16H4zM8 3v4m8-4v4M4 11h16M8 15h3'],
        ['templates','Sjablonen gebruiken','Herbruikbare voedingsplannen','M8 3h12v14H8zM4 7v14h12M11 7h6m-6 4h6'],
        ['own','Mijn voeding','Open je eigen doelen en dagboek','M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8M4 21v-2a8 8 0 0 1 16 0v2']
    ];
    const buttons=new Map();let lastPanel='menu',lastRoute=location.hash;
    function show(panel,focus=false){
        page.dataset.nutritionPanel=panel;menu.hidden=panel!=='menu';bar.hidden=panel==='menu';
        title.textContent=options.find(row=>row[0]===panel)?.[1]||'';
        if(focus){if(panel==='menu')buttons.get(lastPanel)?.focus();else title.focus();}
        if(panel!=='menu')lastPanel=panel;
        if(panel==='templates')document.querySelector('#coach-meal-plans .meal-plan-toolbar')?.scrollIntoView({block:'nearest'});
    }
    for(const [id,label,description,path]of options){
        const el=node(id==='own'?'a':'button',null,'nutrition-option');
        if(id==='own')el.href='#nutrition';else{el.type='button';el.onclick=()=>show(id,true);}
        const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');const shape=document.createElementNS(svg.namespaceURI,'path');shape.setAttribute('d',path);svg.append(shape);
        el.append(svg,node('strong',label),node('span',description));menu.append(el);buttons.set(id,el);
    }
    page.querySelector('header').after(menu,bar);
    page.querySelector('.dashboard-intro').textContent='Kies wat je wilt doen voor je klant of jezelf.';
    back.onclick=()=>show('menu',true);
    document.addEventListener('cap:viewchange',()=>{if(location.hash!==lastRoute){if(location.hash==='#coach-nutrition')show('plan');lastRoute=location.hash;}});
    show('plan');
});
