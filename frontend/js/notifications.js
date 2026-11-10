(() => {
    const $=id=>document.getElementById(id),real=window.capReal,social=window.capSocial;
    const bell=$('header-notifications'),panel=$('header-notification-panel'),messages=$('header-messages');
    const owner=()=>real?real.staffRole==='OWNER':previewRole==='coach';
    let generation=0,loading=false;
    function close(focus=false){panel.hidden=true;bell.setAttribute('aria-expanded','false');if(focus)bell.focus();}
    function badge(id,count,label,control){const el=$(id);el.hidden=!count;el.textContent=count>99?'99+':String(count);control.setAttribute('aria-label',count?`${label}, ${count} ongelezen`:label);}
    function render(items){const list=$('header-notification-list');list.replaceChildren();
        for(const item of items){const row=document.createElement('li'),button=document.createElement('button');button.type='button';button.className='header-notification-item';
            const title=document.createElement('strong');title.textContent=item.kind==='workout'?'Workout voltooid':item.kind==='community'?'Community ? '+(item.channelName||({general:'Announcements','chat-room':'Chat room',hyrox:'CAP HYROX',hybrid:'CAP Hybrid',move:'CAP Move','lift-hit':'CAP Lift & HIT','open-gym':'Open gym'}[item.channel]||'Announcements')):item.kind==='group'?'Bericht in lesgroep':'Nieuw privébericht';
            const text=document.createElement('span');text.textContent=item.kind==='workout'?`${item.athlete} · ${item.workout}`:`${item.name}: ${item.excerpt}`;
            const time=document.createElement('small');time.textContent=window.capDateTime.dateTime(item.created||item.completed);button.append(title,text,time);
            button.onclick=async()=>{button.disabled=true;try{if(item.kind==='workout'){await window.capOwnerAgenda.read(item.id);location.hash='owner-agenda';}else{await social.markRead([item.id]);await social.openNotification(item);}close();await refresh();}catch(error){$('header-notification-status').textContent=error.message;button.disabled=false;}};
            row.append(button);list.append(row);
        }
        if(!items.length){const row=document.createElement('li');row.textContent='Je bent helemaal bij. Geen nieuwe meldingen.';list.append(row);}
    }
    async function refresh(){
        if(real?.fullAccess===false){messages.hidden=true;bell.hidden=true;close();return;}
        messages.hidden=!social.allowed();if(!window.capAccess.signedIn){close();return;}
        const current=++generation;loading=true;
        const results=await Promise.allSettled([social.notificationData(),owner()&&real?real.request('/api/owner-agenda/notifications'):Promise.resolve({items:[],unread:0})]);
        if(current!==generation)return;loading=false;
        const [updates,workouts]=results;let items=[],messageCount=0;
        if(updates.status==='fulfilled'){items=updates.value.items;messageCount=updates.value.messageUnread;}
        if(workouts.status==='fulfilled')items=[...items,...workouts.value.items.filter(item=>!item.read).map(item=>({...item,kind:'workout'}))];
        items.sort((a,b)=>String(b.created||b.completed).localeCompare(String(a.created||a.completed)));
        badge('header-message-count',messageCount,'Berichten',messages);badge('header-notification-count',items.length,'Meldingen',bell);render(items);
        const failed=updates.status==='rejected'||workouts.status==='rejected';$('header-notification-status').textContent=failed?'Niet alle meldingen konden worden geladen. Probeer opnieuw.':'Recente ongelezen berichten en updates.';$('header-notification-retry').hidden=!failed;
    }
    bell.onclick=()=>{const opening=panel.hidden;panel.hidden=!opening;bell.setAttribute('aria-expanded',String(opening));if(opening){$('account-links').hidden=true;$('account-toggle').setAttribute('aria-expanded','false');refresh();}};
    $('header-notification-close').onclick=()=>close(true);$('header-notification-retry').onclick=refresh;
    document.addEventListener('click',event=>{if(!panel.hidden&&!panel.contains(event.target)&&!bell.contains(event.target))close();});
    document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!panel.hidden){close(true);event.stopPropagation();}});
    document.addEventListener('cap:viewchange',()=>{close();refresh();});document.addEventListener('cap:notifications-changed',refresh);document.addEventListener('cap:owner-agenda-changed',refresh);
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});setInterval(()=>{if(!document.hidden&&!loading)refresh();},15000);
    const oldNotificationMenu=document.querySelector('[data-account="notifications"]');oldNotificationMenu.addEventListener('click',event=>{event.stopImmediatePropagation();if(panel.hidden)bell.click();else close(true);},true);
    refresh();
})();
