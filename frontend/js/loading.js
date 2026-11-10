// Content-shaped loading states. Rendering and request ownership stay with each feature.
(() => {
    const pending=new WeakMap();
    function line(width){const el=document.createElement('span');el.className='cap-skeleton-line';el.style.width=width;return el;}
    function skeleton(target,shape,count){
        const table=target.tagName==='TBODY',list=['UL','OL'].includes(target.tagName);
        const root=document.createElement(table?'tr':list?'li':'div');root.className='cap-skeleton';root.setAttribute('aria-hidden','true');
        let body=root;if(table){body=document.createElement('td');body.colSpan=target.closest('table')?.querySelectorAll('thead th').length||7;root.append(body);}
        const content=document.createElement('div');content.className='cap-skeleton-content cap-skeleton-'+shape;body.append(content);
        const size=shape==='calendar'?42:count;
        for(let i=0;i<size;i++){const row=document.createElement('div');row.className='cap-skeleton-row';
            if(shape==='calendar')row.append(line('1rem'));
            else if(shape==='metrics'){row.append(line('65%'),line('40%'),line('80%'));}
            else {row.append(line(i%2?'62%':'78%'),line('46%'));if(shape==='cards')row.append(line('90%'));}
            content.append(row);
        }return root;
    }
    function begin(target,{shape='list',count=3,retain=Boolean(target?.children.length&&target.textContent.trim())}={}){
        if(!target)return ()=>{};pending.get(target)?.();
        const busy=target.getAttribute('aria-busy'),marker=skeleton(target,shape,count),nodes=[...target.childNodes];
        const reserve=target.tagName==='TBODY'?target.closest('table').parentElement:target;
        const previousMin=reserve.style.minBlockSize;
        if(!retain){const rem=parseFloat(getComputedStyle(document.documentElement).fontSize),rows=shape==='calendar'?6:shape==='metrics'?Math.ceil(count/(matchMedia('(min-width:1000px)').matches?4:2)):count;
            const height=(rows*(shape==='cards'||shape==='metrics'?8:4.5)+Math.max(0,rows-1)*(shape==='calendar'?.25:1)+1.5)*rem;
            const maximum=parseFloat(getComputedStyle(reserve).maxHeight);reserve.style.minBlockSize=Math.min(height,Number.isFinite(maximum)?maximum:height)+'px';}
        let finished=false,shown=false;target.setAttribute('aria-busy','true');if(retain)target.classList.add('cap-refreshing');
        // Delay the indicator, never the request or its response. Fast requests do not flash.
        const timer=setTimeout(()=>{if(finished||!target.isConnected||retain)return;shown=true;target.replaceChildren(marker);},140);
        const finish=()=>{if(finished)return;finished=true;clearTimeout(timer);
            if(shown&&marker.parentNode===target){marker.remove();if(!target.childNodes.length)target.append(...nodes);}
            if(pending.get(target)===finish){pending.delete(target);reserve.style.minBlockSize=previousMin;target.classList.remove('cap-refreshing');if(busy===null)target.removeAttribute('aria-busy');else target.setAttribute('aria-busy',busy);}
        };
        pending.set(target,finish);return finish;
    }
    window.capLoading={begin};
})();
