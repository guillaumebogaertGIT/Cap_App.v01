// Private chat attachments. Real files travel with their message; preview files stay in IndexedDB.
(() => {
    const limit=10*1024*1024,accept='.pdf,.png,.jpg,.jpeg,.webp,.txt,.csv,.docx,.xlsx,.pptx';
    const clip='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22" aria-hidden="true"><path d="m8 12 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l9-9M6 14l8-8"/></svg>';
    const size=bytes=>bytes<1048576?Math.ceil(bytes/1024)+' KB':(bytes/1048576).toFixed(1)+' MB';
    async function previewStore(mode,id,file){return new Promise((resolve,reject)=>{const open=indexedDB.open('cap-chat-files',1);open.onupgradeneeded=()=>open.result.createObjectStore('files');open.onerror=()=>reject(new Error('Bestand opslaan mislukt. Probeer opnieuw.'));open.onsuccess=()=>{const db=open.result,tx=db.transaction('files',mode==='get'?'readonly':'readwrite'),store=tx.objectStore('files');const req=mode==='get'?store.get(id):mode==='delete'?store.delete(id):store.put(file,id);let value;req.onsuccess=()=>{value=req.result;};tx.oncomplete=()=>{db.close();resolve(value);};tx.onerror=()=>{db.close();reject(new Error('Bestand opslaan mislukt. Controleer de browseropslag.'));};};});}
    window.capChatAttachments={
        size,previewStore,
        mount(chat,currentKey,status){
            const form=chat.querySelector('form'),textarea=form.querySelector('textarea'),files=new Map();
            textarea.required=false;
            const picker=document.createElement('input');picker.type='file';picker.accept=accept;picker.hidden=true;
            const button=document.createElement('button');button.type='button';button.className='secondary-button message-attach';button.title='Bestand toevoegen';button.setAttribute('aria-label','Bestand toevoegen');button.innerHTML=clip;button.disabled=true;button.onclick=()=>picker.click();
            const pending=document.createElement('div');pending.className='message-pending-file';pending.hidden=true;
            const help=document.createElement('small');help.className='message-file-help';help.textContent='Sleep een bestand hierheen of gebruik de paperclip · Max. 10 MB';
            if(form.classList.contains('community-composer')){form.prepend(pending);textarea.parentElement.prepend(button);form.querySelector(':scope > small')?.remove();help.textContent+=' ? Zichtbaar voor iedereen bij CAP.';}else form.prepend(pending,button);form.append(picker,help);
            function render(){const file=files.get(currentKey());button.disabled=!currentKey();pending.replaceChildren();pending.hidden=!file;if(file){const name=document.createElement('span');name.textContent=file.name+' · '+size(file.size);const remove=document.createElement('button');remove.type='button';remove.className='secondary-button';remove.setAttribute('aria-label','Bijlage verwijderen');remove.textContent='Verwijderen';remove.onclick=()=>{files.delete(currentKey());render();form.dispatchEvent(new Event('input'));};pending.append(name,remove);}}
            function choose(list){if(!currentKey())return;if(list.length!==1){status.textContent='Voeg één bestand per bericht toe.';return;}const file=list[0];if(!file.size||file.size>limit||! /\.(pdf|png|jpe?g|webp|txt|csv|docx|xlsx|pptx)$/i.test(file.name)){status.textContent='Kies een PDF, afbeelding of document van maximaal 10 MB.';return;}files.set(currentKey(),file);render();status.textContent='Bestand toegevoegd. Klik op Versturen om het te delen.';form.dispatchEvent(new Event('input'));}
            picker.onchange=()=>{if(picker.files.length)choose(picker.files);picker.value='';};
            const area=chat.querySelector('.message-conversation,.community-conversation');let depth=0;
            area.addEventListener('dragenter',event=>{if(!event.dataTransfer?.types.includes('Files'))return;event.preventDefault();depth++;if(currentKey())area.classList.add('is-file-drop');});
            area.addEventListener('dragover',event=>{if(event.dataTransfer?.types.includes('Files')){event.preventDefault();event.dataTransfer.dropEffect=currentKey()?'copy':'none';}});
            area.addEventListener('dragleave',()=>{if(--depth<=0){depth=0;area.classList.remove('is-file-drop');}});
            area.addEventListener('drop',event=>{if(!event.dataTransfer?.types.includes('Files'))return;event.preventDefault();depth=0;area.classList.remove('is-file-drop');choose(event.dataTransfer.files);});
            chat.addEventListener('cap:community-channel',render);render();
            return {render,get:key=>files.get(key),clear(key){files.delete(key);render();},async encode(file){return new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve({name:file.name,data:reader.result.split(',')[1]});reader.onerror=()=>reject(new Error('Bestand lezen mislukt. Selecteer het opnieuw.'));reader.readAsDataURL(file);});}};
        },
        link(item,real){
            const link=document.createElement('a');link.className='message-file-link';link.textContent=item.filename+' · '+size(item.size);link.download=item.filename;
            if(real)link.href='/api/social/attachments/'+encodeURIComponent(item.id);
            else{link.href='#';link.onclick=async event=>{event.preventDefault();try{const file=await previewStore('get',item.id);if(!file)throw new Error('Dit voorbeeldbestand is niet meer beschikbaar.');const url=URL.createObjectURL(file),a=document.createElement('a');a.href=url;a.download=item.filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}catch(error){link.closest('[data-view]').querySelector('[role=status]').textContent=error.message;}};}
            return link;
        }
    };
})();
