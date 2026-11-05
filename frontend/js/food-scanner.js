// Decode on the device; only the resulting numeric product code goes to CAP.
document.addEventListener('DOMContentLoaded',()=>{
    const launch=document.getElementById('food-scan-start');
    const allowed=()=>window.capAccess.signedIn&&previewRole==='athlete'&&location.hash==='#nutrition';
    const dialog=document.createElement('dialog');dialog.className='club-dialog food-scanner-dialog';dialog.id='food-scanner-dialog';dialog.setAttribute('aria-labelledby','food-scanner-title');
    dialog.innerHTML=`<div class="session-dialog-header"><h2 id="food-scanner-title">Barcode scannen</h2><button type="button" class="secondary-button session-dialog-close" aria-label="Sluiten"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
      <p>Houd de streepjescode goed verlicht en recht voor de camera. Er wordt niets gelogd voordat je het product en de portie kiest.</p>
      <video muted playsinline autoplay aria-label="Camerabeeld voor barcode"></video>
      <p role="status" aria-live="polite"></p><div class="editor-actions"><button type="button" class="secondary-button" data-retry>Camera starten</button><button type="button" class="secondary-button" data-type>Barcode typen</button></div>
      <label class="club-form">Of kies een duidelijke barcodefoto<input type="file" accept="image/*" capture="environment"></label>`;
    document.body.append(dialog);
    const video=dialog.querySelector('video'),status=dialog.querySelector('[role=status]'),retry=dialog.querySelector('[data-retry]'),file=dialog.querySelector('[type=file]');
    let controls=null,generation=0,library=null;
    function stop(){
        ++generation;controls?.stop();controls=null;
        if(video.srcObject)video.srcObject.getTracks().forEach(track=>track.stop());
        video.srcObject=null;retry.disabled=false;
    }
    function load(){
        if(window.ZXingBrowser)return Promise.resolve(window.ZXingBrowser);
        if(library)return library;
        library=new Promise((resolve,reject)=>{
            const script=document.createElement('script');script.src='vendor/zxing-browser-0.2.1.min.js';
            script.onload=()=>window.ZXingBrowser?resolve(window.ZXingBrowser):reject(new Error('Scanner niet geladen.'));
            script.onerror=()=>{script.remove();library=null;reject(new Error('Scanner niet geladen.'));};document.head.append(script);
        });return library;
    }
    function found(code,scannerControls){
        if(!allowed()||!dialog.open)return;
        if(!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(code)){status.textContent='Dit is geen ondersteunde productbarcode. Gebruik de streepjescode op de verpakking.';return;}
        scannerControls?.stop();stop();dialog.close();
        document.getElementById('food-barcode-panel').open=true;
        document.dispatchEvent(new CustomEvent('cap:food-barcode-selected',{detail:{barcode:code}}));
    }
    async function start(){
        if(!allowed()||!dialog.open)return;
        stop();const version=generation;retry.disabled=true;status.textContent='Camera openen…';
        if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia){status.textContent='De camera heeft HTTPS of localhost nodig. Kies een barcodefoto of typ de code.';retry.disabled=false;return;}
        try{
            const zxing=await load();if(version!==generation||!dialog.open)return;
            const reader=new zxing.BrowserMultiFormatReader();
            const scanning=await reader.decodeFromConstraints({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}}},video,(result,error,current)=>{
                if(version===generation&&result)found(result.getText(),current);
            });
            if(version!==generation||!dialog.open){scanning.stop();return;}
            controls=scanning;status.textContent='Richt de camera op de streepjescode. De scanner stopt zodra een productcode is gevonden.';
        }catch(error){
            if(version!==generation||!dialog.open)return;stop();
            status.textContent=error.name==='NotAllowedError'?'Geen cameratoegang. Sta de camera toe, kies een foto of typ de barcode.':error.name==='NotFoundError'?'Geen camera gevonden. Kies een foto of typ de barcode.':'De camera kon niet starten. Probeer opnieuw, kies een foto of typ de barcode.';
        }
    }
    launch.addEventListener('click',()=>{if(!allowed())return;file.value='';dialog.showModal();start();});
    retry.addEventListener('click',start);
    dialog.querySelector('.session-dialog-close').addEventListener('click',()=>dialog.close());
    dialog.querySelector('[data-type]').addEventListener('click',()=>{dialog.close();document.getElementById('food-barcode-panel').open=true;document.getElementById('food-barcode').focus();});
    dialog.addEventListener('close',stop);dialog.addEventListener('cancel',stop);
    document.addEventListener('visibilitychange',()=>{if(document.hidden&&dialog.open){stop();status.textContent='Camera gepauzeerd. Kies Camera starten om verder te scannen.';}});
    document.addEventListener('cap:viewchange',()=>{if(!allowed()&&dialog.open)dialog.close();});
    file.addEventListener('change',async()=>{
        const image=file.files[0];if(!image||!allowed()||!dialog.open)return;
        stop();const version=generation;
        if(image.size>10*1024*1024){status.textContent='Kies een afbeelding kleiner dan 10 MB.';return;}
        status.textContent='Barcode in foto lezen…';const url=URL.createObjectURL(image);
        try{
            const zxing=await load();if(version!==generation||!dialog.open)return;
            const picture=new Image();picture.src=url;await picture.decode();
            if(picture.width*picture.height>20000000)throw new Error('Afbeelding te groot.');
            const result=await new zxing.BrowserMultiFormatReader().decodeFromImageElement(picture);
            if(version===generation)found(result.getText());
        }catch{if(version===generation&&dialog.open)status.textContent='Geen barcode herkend. Kies een scherpere foto of typ de code.';}
        finally{URL.revokeObjectURL(url);}
    });
});
