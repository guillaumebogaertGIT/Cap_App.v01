// Financial data is fetched only for the separately authorized real coach account.
document.addEventListener('DOMContentLoaded', () => {
    const real = window.capReal;
    if (real?.account.role !== 'COACH' || !real.permissions?.reviewFinance) return;
    const page = document.getElementById('finance');
    const form = page.querySelector('form');
    const month = page.querySelector('#finance-month');
    const year = page.querySelector('#finance-year');
    const status = page.querySelector('[role="status"]');
    const results = page.querySelector('#finance-results');
    const list = page.querySelector('ul');
    const button = form.querySelector('button');
    const parts = Object.fromEntries(new Intl.DateTimeFormat('nl-BE', {
        timeZone: 'Europe/Brussels', year: 'numeric', month: '2-digit'
    }).formatToParts(new Date()).map(part => [part.type, part.value]));
    ['Januari', 'Februari', 'Maart', 'April', 'Mei', 'Juni', 'Juli', 'Augustus', 'September', 'Oktober', 'November', 'December']
        .forEach((label, index) => month.add(new Option(label, String(index + 1).padStart(2, '0'))));
    month.value = parts.month; year.value = parts.year;
    const money = cents => new Intl.NumberFormat('nl-BE', { style: 'currency', currency: 'EUR' }).format(cents / 100);
    const home=document.getElementById('finance-home'),homeSummary=document.getElementById('finance-home-summary'),homeStatus=document.getElementById('finance-home-status'),homeRefresh=document.getElementById('finance-home-refresh');
    const chart=document.createElement('div');chart.className='finance-chart';results.insertBefore(chart,list.previousElementSibling);
    const filters=document.createElement('div');filters.className='club-toolbar';
    filters.innerHTML='<label>Zoek sporter<input type="search" placeholder="Naam" autocomplete="off"></label><label>Betalingen<select><option value="all">Alle ontvangen betalingen</option><option value="refund">Met terugbetaling</option><option value="disputed">Betwist</option></select></label><p role="status"></p>';
    list.before(filters);const search=filters.querySelector('input'),kind=filters.querySelector('select'),filterStatus=filters.querySelector('p');
    let reportData=null;
    function graph(report){
        const section=document.createElement('section');section.className='finance-daily';
        const heading=document.createElement('h3');heading.textContent='Ontvangen per dag';
        const caption=document.createElement('p');caption.textContent='Bruto ontvangen in '+report.month.split('-').reverse().join('/')+' · tik op een balk voor het bedrag. Terugbetalingen staan in de totalen.';
        const bars=document.createElement('div');bars.className='finance-bars';
        const [y,m]=report.month.split('-').map(Number),days=new Date(Date.UTC(y,m,0)).getUTCDate(),values=Array(days).fill(0);
        for(const payment of report.payments){const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Brussels',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(payment.date));const day=Number(date.slice(-2));if(date.startsWith(report.month)&&day>=1&&day<=days)values[day-1]+=payment.receivedCents;}
        const max=Math.max(1,...values),readout=document.createElement('p');readout.className='finance-chart-readout';readout.setAttribute('aria-live','polite');readout.textContent=report.payments.length?'Kies een dag om het bedrag te bekijken.':'Geen ontvangen betalingen in deze maand.';
        values.forEach((amount,i)=>{const bar=document.createElement('button');bar.type='button';bar.className='finance-bar';const label=String(i+1).padStart(2,'0')+'/'+String(m).padStart(2,'0')+'/'+y+' · '+money(amount);bar.title=label;bar.setAttribute('aria-label',label);const fill=document.createElement('span');fill.style.height=(amount/max*100)+'%';fill.setAttribute('aria-hidden','true');bar.append(fill);bar.addEventListener('click',()=>{readout.textContent=label;});bars.append(bar);});
        const scale=document.createElement('p');scale.className='finance-chart-scale';scale.textContent='1/'+String(m).padStart(2,'0')+' · '+days+'/'+String(m).padStart(2,'0')+' · hoogste dag: '+money(Math.max(...values));section.append(heading,caption,bars,scale,readout);return section;
    }
    function renderPayments(){list.replaceChildren();if(!reportData)return;const term=search.value.trim().toLocaleLowerCase('nl-BE'),payments=reportData.payments.filter(p=>p.athlete.toLocaleLowerCase('nl-BE').includes(term)&&(kind.value==='all'||kind.value==='refund'&&p.refundedCents>0||kind.value==='disputed'&&p.disputed));
        for(const payment of payments){const item=document.createElement('li');for(const text of [payment.athlete,window.capDateTime.dateTime(payment.date),money(payment.receivedCents),payment.refundedCents?money(payment.refundedCents)+' terugbetaald':'Geen terugbetaling',payment.disputed?'Betwist: controleer Stripe':'']){if(!text)continue;const span=document.createElement('span');span.textContent=text;item.append(span);}list.append(item);}
        filterStatus.textContent=payments.length+' van '+reportData.payments.length+' betalingen getoond. De totalen en grafiek blijven voor de volledige maand.';
    }
    search.addEventListener('input',renderPayments);kind.addEventListener('change',renderPayments);
    let homeGeneration=0;
    async function loadHome(){const version=++homeGeneration;homeSummary.hidden=true;homeSummary.replaceChildren();homeStatus.textContent='Testbetalingen laden…';homeRefresh.disabled=true;
        try{const selected=parts.year+'-'+parts.month,report=await real.request('/api/finance?month='+selected);if(version!==homeGeneration||home.hidden)return;if(!report.testMode||report.month!==selected)throw new Error('Het overzicht past niet bij deze maand.');
            const totals=document.createElement('dl');totals.className='finance-totals';for(const [label,value] of [['Ontvangen',report.receivedCents],['Terugbetaald',report.refundedCents],['Over na terugbetalingen',report.remainingCents]]){const group=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=money(value);group.append(dt,dd);totals.append(group);}homeSummary.append(totals,graph(report));homeSummary.hidden=false;homeStatus.textContent=report.payments.length+' ontvangen testbetalingen · gecontroleerd '+window.capDateTime.dateTime(report.checkedAt)+(report.excludedCurrencyCount?' · '+report.excludedCurrencyCount+' betalingen in andere valuta uitgesloten.':'.');
        }catch(error){if(version===homeGeneration)homeStatus.textContent=error.message+' Kies Opnieuw laden.';}finally{if(version===homeGeneration)homeRefresh.disabled=false;}
    }
    homeRefresh.addEventListener('click',loadHome);
    let generation = 0;
    function clear() { ++generation; results.hidden = true; reportData=null; chart.replaceChildren(); list.replaceChildren(); button.disabled = false; }
    async function load() {
        clear();
        if (!form.reportValidity()) return;
        const version = generation, selected = year.value + '-' + month.value;
        status.textContent = 'Testbetalingen controleren bij Stripe…'; button.disabled = true;
        try {
            const report = await real.request('/api/finance?month=' + encodeURIComponent(selected));
            if (version !== generation || page.hidden) return;
            if (!report.testMode || report.month !== selected) throw new Error('Het overzicht past niet bij de gekozen maand. Probeer opnieuw.');
            for (const key of ['received', 'refunded', 'remaining']) page.querySelector('#finance-' + key).textContent = money(report[key + 'Cents']);
            reportData=report;chart.replaceChildren(graph(report));renderPayments();
            results.hidden = false;
            status.textContent = (report.payments.length ? report.payments.length + ' ontvangen testbetalingen.' : 'Geen ontvangen testbetalingen in deze maand.')
                + ' Gecontroleerd: ' + window.capDateTime.dateTime(report.checkedAt) + '.'
                + (report.excludedCurrencyCount ? ' ' + report.excludedCurrencyCount + ' betalingen in andere valuta niet meegerekend.' : '');
        } catch (error) {
            if (version === generation) status.textContent = error.message + ' Kies Overzicht laden om opnieuw te proberen.';
        } finally { if (version === generation) button.disabled = false; }
    }
    form.addEventListener('submit', event => { event.preventDefault(); load(); });
    form.addEventListener('input', () => { clear(); status.textContent = 'Kies Overzicht laden voor de geselecteerde maand.'; });
    document.addEventListener('cap:viewchange', () => {
        if (!page.hidden) load(); else clear();
        if(real.staffRole==='FINANCE'&&!home.hidden)loadHome();else{++homeGeneration;homeRefresh.disabled=false;homeSummary.hidden=true;homeSummary.replaceChildren();}
    });
    if (!page.hidden) load();
    if(real.staffRole==='FINANCE'&&!home.hidden)loadHome();
});
