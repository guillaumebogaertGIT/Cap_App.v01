document.addEventListener('DOMContentLoaded',()=>{
    const real=window.capReal,page=document.getElementById('finance');
    if(real&&!real.permissions?.reviewFinance)return;
    const host=document.createElement('section');host.className='finance-overview';host.setAttribute('aria-labelledby','finance-overview-title');
    host.innerHTML='<header class="finance-overview-heading"><div><h3 id="finance-overview-title">Deze maand in beeld</h3><p data-date></p></div><button type="button" class="secondary-button" data-refresh>Cijfers vernieuwen</button></header><p role="status" aria-live="polite"></p><div class="finance-metrics"></div><p class="sample-notice" data-source></p>';
    page.querySelector('header').after(host);if(!real){page.querySelector('#finance-filter').hidden=true;page.querySelector('#finance-status').textContent='Het gedetailleerde betalingsrapport is beschikbaar na aanmelden met een bevoegd echt account.';}
    const definitions=[['active','Actieve klanten','Actieve klantaccounts met een geldig lidmaatschap of beschikbare lescredits.'],['new','Nieuwe klanten','Nieuw aangemaakte klantaccounts, ook als ze nu niet meer actief zijn.'],['participants','Deelnemers','Unieke klanten die voor minstens één les als Aanwezig zijn geregistreerd.'],['income','Inkomsten','Bruto ontvangen EUR-betalingen van gekoppelde CAP-accounts in Stripe TEST. Vóór terugbetalingen en kosten; externe betalingen ontbreken.']];
    const elements={};for(const [key,title,meaning] of definitions){const article=document.createElement('article');article.className='finance-metric';const heading=document.createElement('h4');heading.textContent=title;const value=document.createElement('strong');value.textContent='—';const period=document.createElement('p');period.textContent=key==='active'?'Vandaag':'Deze maand tot nu toe';const change=document.createElement('p');change.className='finance-metric-change';const detail=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Wat telt mee?';const text=document.createElement('p');text.textContent=meaning;detail.append(summary,text);article.append(heading,value,period,change,detail);elements[key]={value,period,change};host.querySelector('.finance-metrics').append(article);}
    const money=cents=>new Intl.NumberFormat('nl-BE',{style:'currency',currency:'EUR'}).format(cents/100);
    function delta(key,current,previous){const el=elements[key].change;el.classList.remove('is-up','is-down');if(previous==null){el.textContent=key==='active'?'Historische vergelijking niet beschikbaar.':'Vergelijking niet beschikbaar.';return;}const diff=current-previous;el.classList.toggle('is-up',diff>0);el.classList.toggle('is-down',diff<0);const amount=key==='income'?money(Math.abs(diff)):Math.abs(diff);el.textContent=diff===0?'Gelijk aan vorige periode.':`${diff>0?'+':'−'}${amount}${previous>0?' · '+(Math.abs(diff)/previous*100).toFixed(0)+'% '+(diff>0?'meer':'minder'):''} t.o.v. dezelfde periode vorige maand.`;}
    let loading=false,generation=0,loaded=false;
    const preview=()=>{const now=new Date(),day=capDateTime.date(now);return {today:day,activeClients:8,newClients:{value:3,previous:2},participants:{value:6,previous:5},billingEnabled:false};};
    async function load(){if(loading)return;loading=true;loaded=false;const current=++generation;host.querySelector('[data-refresh]').disabled=true;host.querySelector('[role=status]').textContent='Cijfers laden…';const finishLoading=capLoading.begin(host.querySelector('.finance-metrics'),{shape:'metrics',count:4,retain:Object.values(elements).some(item=>item.value.textContent!=='—')});for(const item of Object.values(elements)){item.value.textContent='—';item.change.textContent='';item.change.classList.remove('is-up','is-down');}
        try{const data=real?await real.request('/api/finance/overview'):preview();if(current!==generation)return;finishLoading();
            host.querySelector('[data-date]').textContent=real?capDateTime.dayLabel(data.today):data.today;
            elements.active.value.textContent=data.activeClients;delta('active',data.activeClients,null);
            for(const [key,counter] of [['new',data.newClients],['participants',data.participants]]){elements[key].value.textContent=counter.value;delta(key,counter.value,counter.previous);}
            host.querySelector('[data-source]').textContent=real?'Klanten en aanwezigheid uit CAP. Vergelijking: dezelfde dag en tijd vorige maand, in Belgische tijd. Prijsafspraken tellen niet als ontvangen betalingen.':'Voorbeeldcijfers om de indeling te bekijken. Dit zijn geen echte klanten- of omzetcijfers.';
            host.querySelector('[role=status]').textContent='';
            if(!real){elements.income.value.textContent=money(87500);elements.income.period.textContent='Voorbeeld · Stripe TEST';delta('income',87500,70000);}
            else if(!data.billingEnabled){elements.income.period.textContent='Stripe TEST staat uit';elements.income.change.textContent='Geen betalingscijfers geladen.';}
            else{
                elements.income.period.textContent='Bruto ontvangen · Stripe TEST';
                const month=value=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Brussels',year:'numeric',month:'2-digit'}).format(new Date(value));
                const reports=await Promise.allSettled([real.request('/api/finance?month='+month(data.currentStart)),real.request('/api/finance?month='+month(data.previousStart))]);if(current!==generation)return;
                function amount(result,start,end){if(result.status!=='fulfilled'||!result.value.testMode||result.value.month!==month(start))return null;return result.value.payments.filter(item=>new Date(item.date)>=new Date(start)&&new Date(item.date)<new Date(end)).reduce((total,item)=>total+item.receivedCents,0);}
                const received=amount(reports[0],data.currentStart,data.currentEnd),previous=amount(reports[1],data.previousStart,data.previousEnd);
                if(received==null){elements.income.change.textContent='Betalingscijfers niet beschikbaar. Probeer opnieuw.';}else{elements.income.value.textContent=money(received);delta('income',received,previous);}
            }
            loaded=true;
        }catch(error){host.querySelector('[role=status]').textContent='Cijfers laden mislukt. '+error.message+' Kies Cijfers vernieuwen om opnieuw te proberen.';}
        finally{finishLoading();loading=false;host.querySelector('[data-refresh]').disabled=false;}
    }
    host.querySelector('[data-refresh]').onclick=load;document.addEventListener('cap:viewchange',()=>{if(location.hash==='#finance'&&!loaded)load();});if(location.hash==='#finance')load();
});
