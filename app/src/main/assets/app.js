'use strict';
const $=id=>document.getElementById(id);
function esc(s){return String(s??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));}
function read(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback;}catch(e){return fallback;}}
function write(k,v){localStorage.setItem(k,JSON.stringify(v));}
function act(action,data={}){Tailer.act(action,JSON.stringify(data));}
const paths={
 home:'<path d="m3 10 9-7 9 7v10H5V10"/><path d="M9 20v-7h6v7"/>',
 chat:'<path d="M20 14a4 4 0 0 1-4 4H9l-5 3V7a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4Z"/><path d="M8 9h8M8 13h5"/>',
 log:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/>',
 settings:'<path d="M4 7h6m5 0h5M4 17h10m5 0h1"/><circle cx="12" cy="7" r="2.5"/><circle cx="16" cy="17" r="2.5"/>',
 phone:'<path d="M6 3 4 5c-2 6 9 17 15 14l2-2-4-4-3 2c-3-1-5-3-6-6l2-2Z"/>',
 tg:'<path d="m3 10 17-7-3 17-5-5-3 2v-5l7-6-9 6Z"/>',
 game:'<path d="M8 6h8c3 0 4 3 5 8s-2 7-5 3l-1-1H9l-1 1c-3 4-6 2-5-3s2-8 5-8Z"/><path d="M8 9v5m-2.5-2.5h5m5-1h.1m2 2h.1"/>',
 mic:'<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0m-7 7v3m-3 0h6"/>',
 right:'<path d="m9 5 7 7-7 7"/>',back:'<path d="m14 5-7 7 7 7"/>',send:'<path d="M12 19V5m-7 7 7-7 7 7"/>',
 grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
 tap:'<path d="M8 13V6a2 2 0 0 1 4 0v6l2-1 5 2v4l-2 4h-6l-6-7a2 2 0 0 1 3-1Z"/>',
 note:'<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h7m-7 4h7m-7 4h4"/>'};
function icon(k,cls=''){return '<svg aria-hidden="true" class="'+cls+'" viewBox="0 0 24 24">'+paths[k]+'</svg>';}
const row=(dest,i,color,name,info)=>'<button class="row" data-do="'+dest+'"><span class="ico '+color+'">'+icon(i)+'</span><span class="copy"><b>'+name+'</b><small>'+esc(info)+'</small></span>'+icon('right','chev')+'</button>';
const btn=(a,s,cls='button')=>'<button class="'+cls+'" data-do="'+a+'">'+s+'</button>';
const field=(name,id,val,placeholder='')=>'<label class="field">'+name+'<input id="'+id+'" value="'+esc(val)+'" placeholder="'+esc(placeholder)+'" autocomplete="off"></label>';
const area=(name,id,val)=>'<label class="field">'+name+'<textarea id="'+id+'" maxlength="2000">'+esc(val)+'</textarea></label>';
const sw=(id,val,label)=>'<label class="switch"><input id="'+id+'" type="checkbox" '+(val?'checked':'')+'>'+label+'</label>';
const box=(name,detail,content)=>'<section class="box"><h3>'+name+'</h3>'+(detail?'<p>'+detail+'</p>':'')+content+'</section>';
const status=(on,yes,no)=>'<span class="status '+(on?'':'off')+'">'+(on?yes:no)+'</span>';
const options=(items,selected,empty)=>'<option value="">'+empty+'</option>'+items.map(o=>'<option value="'+esc(o.id)+'" '+(o.id===selected?'selected':'')+'>'+esc(o.title)+'</option>').join('');
const back=(to='home')=>'<button class="back" data-do="'+to+'">'+icon('back')+'Назад</button>';
const intro=(name,sub,to)=>back(to)+'<h1>'+name+'</h1><p class="intro">'+sub+'</p>';
let S={chats:[],games:[],journal:[],reply:'Сейчас занят. Отвечу чуть позже.'},view='home',toneTimer,frame,autoTimer,session=read('puzzle',null)||Puzzle.fresh();
let messages=read('chat',[]),notes=read('notes',[]),localLog=read('log',[]),gamePkg=read('gamePkg','');
function audit(topic,text){localLog.push({time:Date.now(),topic,text});localLog=localLog.slice(-80);write('log',localLog);}
function notice(t){$('snack').textContent=t;$('snack').className='show';clearTimeout(toneTimer);toneTimer=setTimeout(()=>$('snack').className='',3500);}
function orb(){
  cancelAnimationFrame(frame);const canvas=$('wave');if(!canvas)return;
  const ctx=canvas.getContext('2d'),reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
  function draw(ms){
    if(view!=='home'||document.hidden)return;
    const w=canvas.clientWidth,h=canvas.clientHeight,scale=Math.min(devicePixelRatio,2);
    if(canvas.width!==Math.round(w*scale)||canvas.height!==Math.round(h*scale)){canvas.width=Math.round(w*scale);canvas.height=Math.round(h*scale);}
    ctx.setTransform(scale,0,0,scale,0,0);ctx.clearRect(0,0,w,h);
    const t=ms/5200,g=ctx.createLinearGradient(0,0,w,h*.6);g.addColorStop(0,'rgba(116,238,203,.15)');g.addColorStop(.37,'#8ce4dc');g.addColorStop(.65,'#abc7ef');g.addColorStop(1,'#b4a9f1');
    ctx.strokeStyle=g;
    for(let i=0;i<42;i++){
      const p=i/41;ctx.beginPath();ctx.lineWidth=.8;ctx.globalAlpha=.36+.46*Math.sin(Math.PI*p);
      for(let k=0;k<=92;k++){
        const u=k/92,phase=u*6.05+p*2.85+.16*Math.sin(t);
        const x=(.015+u*.94)*w,y=(.51+(p-.5)*.28+Math.sin(phase)*(.105+.19*Math.sin(Math.PI*u))+.073*Math.sin(u*8-p*3+t*.35))*h;
        if(k===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
      }ctx.stroke();
    }ctx.globalAlpha=1;if(!reduced)frame=requestAnimationFrame(draw);
  }frame=requestAnimationFrame(draw);
}
function home(){return '<section class="hero"><canvas id="wave" aria-hidden="true"></canvas><div class="herostate"><i class="dot"></i>Готов к команде</div><h1>Что<br>сделать?</h1><div class="heroask">'+btn('compose','Написать команду…','fieldlike')+'<button class="mic" data-do="voice" aria-label="Голосовая команда">'+icon('mic')+'</button></div></section><div class="section"><h2>Сценарии</h2></div><div class="cards">'+row('phone','phone','', 'Звонки',S.role&&S.phoneAuto?'Автоприём включён':'Автоприём с выбранного номера')+row('telegram','tg','blue','Telegram',S.tgAccess&&S.tgAuto?'Автоответ в чате «'+S.chatTitle+'»':'Сообщения и автоответы')+row('games','game','purple','Игры','Автоигрок и сценарии касаний')+'</div><div class="section"><h2>Быстрые команды</h2></div><div class="chips">'+btn('volume50','Громкость 50%','chip')+btn('notes','Мои заметки','chip')+btn('network','Интернет','chip')+'</div>';}
function phone(){return intro('Звонки','Tailer может поднять трубку через три секунды, если звонит выбранный номер. После соединения разговариваете вы.')+box('Телефон по умолчанию',status(S.role,'Tailer назначен','Требуется роль телефона'),btn('role',S.role?'Проверить уведомления':'Назначить Tailer','outline'))+box('Правило автоприёма','Укажите номер в том виде, в котором он определяется при звонке.',field('Номер телефона','num',S.number,'+7 999 123-45-67')+sw('phoneOn',S.phoneAuto,'Принимать автоматически')+btn('phoneSave','Сохранить правило'))+btn('dial','Набрать номер','outline');}
function telegram(){
 let chats=S.chats.slice();if(S.chatId&&!chats.some(c=>c.id===S.chatId))chats.push({id:S.chatId,title:S.chatTitle+' · сохранён'});
 return intro('Telegram','Отвечайте из своего аккаунта или откройте новый диалог по имени пользователя.')+'<div class="cards">'+row('review','chat','purple','Анализ переписки','Задать вопрос по сообщениям или составить ответ')+'</div>'+box('Уведомления',status(S.tgAccess,'Доступ подключён','Доступ не подключён'),'<p class="smallprint">Android предлагает общий доступ к уведомлениям. Tailer обрабатывает уведомления Telegram с кнопкой ответа.</p>'+btn('tgAccess','Настроить доступ','outline'))+
 box('Ответ в выбранный чат','Получите сообщение и оставьте его уведомление открытым. Для автоответа выберите чат и сохраните правило.','<label class="field">Чат<select id="chatId">'+options(chats,S.chatId,'Выберите уведомление')+'</select></label>'+btn('refresh','Обновить список','plain')+area('Текст ответа','reply',S.reply)+sw('tgOn',S.tgAuto,'Отвечать автоматически')+'<p class="smallprint">Один автоответ за 10 минут на новые входящие в выбранном чате.</p><div class="actions">'+btn('tgSave','Сохранить')+btn('tgReply','Ответить сейчас','outline')+'</div>')+
 box('Новый диалог','Укажите точный @username. Для отправки по команде Tailer открывает чат, заполняет поле и нажимает кнопку в Telegram.',field('Получатель','handle','','@username')+area('Сообщение','draft','')+'<div class="actions">'+btn('tgSend','Отправить через Tailer')+btn('tgDraft','Открыть черновик','outline')+'</div><p class="smallprint">'+status(S.tgSendAccess,'Служба отправки подключена','Для автоматической отправки включите службу «Tailer · Отправка в Telegram»')+'</p>'+btn('gameAccess','Настроить службу','plain'));
}
function games(){return intro('Игры','Выберите игру и способ управления.')+'<div class="cards">'+row('puzzle','grid','purple','2048','Tailer оценивает поле и выбирает ход')+row('external','tap','blue','Касания в игре','Повтор точек в установленной игре')+'</div>';}
function external(){return intro('Касания в игре','Отметьте на игровом экране до 12 точек в нужном порядке. При повторе Tailer нажмёт каждую из них.','games')+box('Игровая служба',status(S.gameAccess,'Подключена','Требуется доступ в спецвозможностях'),btn('gameAccess','Настроить службу','outline'))+box('Сценарий','В списке появляются приложения, которые Android распознаёт как игры.','<label class="field">Игра<select id="pkg">'+options(S.games,gamePkg,'Выберите игру')+'</select></label><div class="pair"><label class="field">Повторы<input id="count" type="number" value="3" min="1" max="20"></label><label class="field">Пауза, мс<input id="ms" type="number" value="800" min="400" max="5000"></label></div><div class="actions">'+btn('record','Отметить точки','outline')+btn('play','Запустить повтор')+'</div><p class="smallprint">Во время разметки касания отмечают точки. Нажмите «Сохранить», затем запустите повтор в той же ориентации. Кнопка «Стоп» остаётся поверх игры.</p>');}
function puzzle(){return intro('2048','Соединяйте одинаковые плитки свайпом или передайте ход Tailer.','games')+'<div class="score"><span>Счёт <b id="score"></b></span><span id="mode"></span></div><div id="board" class="board" aria-label="Игровое поле"></div><div class="pad">'+['←','↑','→','↓'].map((s,i)=>btn('move'+i,s,'outline')).join('')+'</div><div class="actions">'+btn('auto','Автоигра')+btn('step','Один ход','outline')+btn('newGame','Заново','outline')+'</div>';}
function paintBoard(){
 if(!$('board'))return;
 $('board').innerHTML=session.board.map(v=>{const n=Math.log2(v||1);return '<div class="tile" '+(v?'style="background:hsl('+(166+n*9)+' '+(24+n*2)+'% '+Math.min(76,42+n*3)+'%);color:#102529"':'')+'>'+(v||'')+'</div>';}).join('');
 $('score').textContent=session.score;$('mode').textContent=autoTimer?'Tailer играет':'Ручной режим';document.querySelector('[data-do="auto"]').textContent=autoTimer?'Остановить':'Автоигра';
}
function move(d){const m=Puzzle.slide(session.board,d);if(!m.changed)return;session={board:m.board,score:session.score+m.gain};Puzzle.spawn(session.board);write('puzzle',session);paintBoard();}
function stopAuto(){clearInterval(autoTimer);autoTimer=null;}
function step(){const d=Puzzle.choose(session.board);if(d<0){stopAuto();paintBoard();notice('Ходов больше нет. Итог: '+session.score);return;}move(d);}
function chat(){return '<h1>Чат</h1><div class="history" id="history">'+(!messages.length?'<p class="empty">Введите команду или выберите одну из подсказок.</p><div class="chips">'+btn('exampleTG','Открой Telegram','chip')+btn('exampleCalc','15% от 2400','chip')+btn('exampleGame','Сыграй в 2048','chip')+'</div>':'')+messages.map(m=>'<div class="bubble '+(m.me?'me':'')+'">'+esc(m.text)+'</div>').join('')+(S.askBusy?'<div class="stamp">Tailer отвечает…</div>':'')+'</div><form id="composer" class="composer"><button class="circle" type="button" data-do="voice" aria-label="Диктовать">'+icon('mic')+'</button><input id="command" maxlength="4000" autocomplete="off" placeholder="Сообщение" aria-label="Сообщение"><button class="mic" aria-label="Отправить">'+icon('send')+'</button></form>';}
function journal(){
 const items=[...S.journal,...localLog].sort((a,b)=>b.time-a.time);
 return '<h1>Журнал</h1><p class="intro">Последние действия Tailer на этом устройстве.</p>'+(items.length?items.map(i=>'<article class="logitem"><span class="stamp">'+esc(new Date(i.time).toLocaleString('ru',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}))+' · '+esc(i.topic)+'</span><p>'+esc(i.text)+'</p></article>').join(''):'<div class="box"><h3>Пока пусто</h3><p>Здесь появятся принятые звонки, отправленные ответы и игровые действия.</p></div>');
}
function notePage(){return intro('Заметки','Сохраняются на этом устройстве.')+box('Новая заметка','',area('Текст','note','')+btn('saveNote','Сохранить'))+notes.slice().reverse().map(n=>'<article class="logitem"><p>'+esc(n)+'</p></article>').join('');}
function settings(){return intro('Настройки','Подключения и возможности помощника.')+aiPanel()+box('Вызов из любого приложения','Добавьте Tailer в шторку быстрых настроек. Нажатие на плитку открывает чат.',btn('tile','Добавить плитку','outline'))+box('Локальные команды','«Включи фонарик», «Открой камеру», «Громкость 30%», «Сколько 15% от 2400», «Заметка: купить молоко», «Сыграй в 2048», «Напиши @username: привет».','')+'<div class="cards">'+row('phone','phone','','Телефон','Роль телефона и автоприём')+row('telegram','tg','blue','Telegram','Доступ и выбранный чат')+row('external','tap','purple','Игровая служба','Касания поверх игры')+'</div>';}
const pages={home,phone,telegram,review:reviewPage,games,external,puzzle,chat,journal,notes:notePage,settings};
function render(){
 cancelAnimationFrame(frame);$('page').className=view==='chat'?'chatpage':'';$('page').innerHTML=pages[view]();
 $('settingsBtn').innerHTML=icon('settings');$('nav').innerHTML=[['home','home','Главная'],['chat','chat','Чат'],['journal','log','Журнал']].map(([k,i,s])=>'<button data-do="'+k+'" class="'+(view===k?'on':'')+'"><span>'+icon(i)+'</span>'+s+'</button>').join('');
 if(view==='home')orb();if(view==='puzzle'){paintBoard();bindSwipe();}
 if(view==='chat'){$('composer').onsubmit=e=>{e.preventDefault();submit($('command').value);};$('history').scrollTop=$('history').scrollHeight;}
}
function go(v){if(v!==view)stopAuto();view=v;render();$('page').scrollTop=0;}
window.goBack=()=>{if(view==='home')return false;go(view==='review'?'telegram':view==='puzzle'||view==='external'?'games':'home');return true;};
function bindSwipe(){
 let start=null;const board=$('board');board.onpointerdown=e=>{start=[e.clientX,e.clientY];board.setPointerCapture(e.pointerId);};
 board.onpointerup=e=>{if(!start||autoTimer)return;const x=e.clientX-start[0],y=e.clientY-start[1];start=null;if(Math.max(Math.abs(x),Math.abs(y))>20)move(Math.abs(x)>Math.abs(y)?x<0?0:2:y<0?1:3);};
}
function say(text,me=false){messages.push({me,text});messages=messages.slice(-100);write('chat',messages);if(view==='chat')render();}
function submit(text){
 text=text.trim();if(!text)return;if(S.askBusy){notice('Дождитесь ответа Tailer.');return;}
 go('chat');say(text,true);const t=text.toLowerCase().replace(/ё/g,'е').trim();let m;
 if((m=text.match(/^заметка\s*[:：]\s*(.+)/is))){notes.push(m[1]);write('notes',notes);say('Заметка сохранена.');return;}
 if((m=text.match(/^отправь\s+(@[\w]+)\s*[:,]\s*(.+)/is))){
   if(!S.tgSendAccess){say('Для отправки включите службу «Tailer · Отправка в Telegram» в разделе Telegram.');return;}
   say('Запрашиваю отправку для '+m[1]+'. Результат появится в журнале.');act('tgSend',{name:m[1],text:m[2]});return;
 }
 if((m=text.match(/^(?:напиши|написать)\s+(@[\w]+)\s*[:,]\s*(.+)/is))){say('Открываю черновик для '+m[1]+'. Отправку подтвердите в Telegram.');act('tgDraft',{name:m[1],text:m[2]});return;}
 if((m=t.match(/(?:громкость|громкости)\s*(\d{1,3})\s*%?/))){act('volume',{value:+m[1]});return;}
 if((m=t.match(/(?:^|сколько\s+)(-?\d+(?:[.,]\d+)?)\s*%\s*от\s*(-?\d+(?:[.,]\d+)?)[?!. ]*$/))){say(String(Number(m[1].replace(',','.'))*Number(m[2].replace(',','.'))/100).replace('.',','));return;}
 if((m=t.match(/^(?:сколько\s+)?(-?\d+(?:[.,]\d+)?)\s*([+*×/÷−-])\s*(-?\d+(?:[.,]\d+)?)[?!. ]*$/))){const a=Number(m[1].replace(',','.')),b=Number(m[3].replace(',','.'));const op=m[2];const result=op==='+'?a+b:'-−'.includes(op)?a-b:'*×'.includes(op)?a*b:a/b;say(Number.isFinite(result)?Number(result.toPrecision(12)).toLocaleString('ru'):'Делить на ноль нельзя.');return;}
 if(/^(включи|выключи|отключи) фонарик[.!]?$/.test(t)){act('torch',{on:t.startsWith('включи')});return;}
 if(/не беспокоить|не беспокоил/.test(t)){say('Открываю настройки режима «Не беспокоить». Выберите режим в Android.');act('focus');return;}
 if(/2048|сыграй|поиграй/.test(t)){say('Запускаю автоигру в 2048.');go('puzzle');action('auto');return;}
 if(/автоответ|автоприем/.test(t)){say('Открываю настройки правила.');go(/звон|трубк|прием/.test(t)?'phone':'telegram');return;}
 if(/^(открой|открыть|запусти|покажи)\s/.test(t)){
  for(const [re,a,label] of [[/телеграм|telegram|\bтг\b/,'tgOpen','Telegram'],[/камер/,'camera','камеру'],[/ютуб|youtube/,'youtube','YouTube'],[/экран|темн|диспле/,'display','настройки экрана'],[/интернет|wi-fi|вайфай/,'network','панель интернета'],[/телефон|номер/,'dial','набор номера']]){
    if(re.test(t)||a==='tgOpen'&&t.endsWith(' тг')){say('Открываю '+label+'.');act(a);return;}
  }
  if(/замет/.test(t)){go('notes');return;}
 }
 if(t==='помощь'||t==='команды'){say('Попробуйте «Открой Telegram», «Сыграй в 2048», «Громкость 30%» или «Заметка: купить хлеб».');return;}
 if(!S.ai){say('Такой локальной команды пока нет. Напишите «команды», чтобы посмотреть примеры, или подключите ИИ в настройках для свободного диалога.');return;}
 act('ask',{messages:messages.slice(-10)});
}
function action(a){
 if(reviewAction(a))return;
 if(pages[a]){go(a);return;}
 if(a==='compose'){go('chat');$('command').focus();return;}
 if(a==='voice'){act('voice');return;}
 if(a==='volume50'){go('chat');say('Громкость 50%',true);act('volume',{value:50});return;}
 if(a==='exampleTG'||a==='exampleCalc'||a==='exampleGame'){submit({exampleTG:'Открой Telegram',exampleCalc:'15% от 2400',exampleGame:'Сыграй в 2048'}[a]);return;}
 if(a==='phoneSave'){act(a,{number:$('num').value,on:$('phoneOn').checked});return;}
 if(a==='tgSave'||a==='tgReply'){act(a,{id:$('chatId').value,text:$('reply').value,on:$('tgOn').checked});return;}
 if(a==='tgDraft'||a==='tgSend'){act(a,{name:$('handle').value,text:$('draft').value});return;}
 if(a==='saveNote'){const t=$('note').value.trim();if(t){notes.push(t);write('notes',notes);render();notice('Заметка сохранена.');}return;}
 if(a==='record'||a==='play'){gamePkg=$('pkg').value;write('gamePkg',gamePkg);act('game',{pkg:gamePkg,record:a==='record',count:+$('count').value,ms:+$('ms').value});return;}
 if(a==='step'){if(!autoTimer)step();return;}
 if(a.startsWith('move')){if(!autoTimer)move(+a.slice(-1));return;}
 if(a==='auto'){if(autoTimer){stopAuto();audit('Игры','Автоигра остановлена. Счёт: '+session.score);}else{audit('Игры','Запущена автоигра в 2048');autoTimer=setInterval(step,310);}paintBoard();return;}
 if(a==='newGame'){if(!window.confirm('Начать новую партию в 2048?'))return;stopAuto();session=Puzzle.fresh();write('puzzle',session);paintBoard();return;}
 act(a);
}
document.addEventListener('click',e=>{const button=e.target.closest('[data-do]');if(button)action(button.dataset.do);});
document.addEventListener('visibilitychange',()=>{if(document.hidden){stopAuto();cancelAnimationFrame(frame);}else if(view==='home')orb();else if(view==='puzzle')paintBoard();});
document.addEventListener('keydown',e=>{const k=['ArrowLeft','ArrowUp','ArrowRight','ArrowDown'].indexOf(e.key);if(view==='puzzle'&&k>=0&&!autoTimer){e.preventDefault();move(k);}});
window.nativeEvent=(type,payload)=>{
 if(type.startsWith('review')){reviewEvent(type,payload);return;}
 if(type==='state'){
   const fields=[...$('page').querySelectorAll('input,textarea,select')].filter(el=>el.dataset.edited==='yes').map(el=>[el.id,el.value,el.checked]);
   const top=$('page').scrollTop;S=JSON.parse(payload);render();
   for(const [id,value,checked] of fields){const el=$(id);if(el){el.value=value;if(el.type==='checkbox')el.checked=checked;el.dataset.edited='yes';}}
   $('page').scrollTop=top;
 }
 if(type==='notice')notice(payload);
 if(type==='compose')action('compose');
 if(type==='voice'){go('chat');submit(payload);}
 if(type==='answer'){say(payload);if(view!=='chat')notice('В чате появился ответ.');}
};
document.addEventListener('input',e=>{if(e.target.matches('input,textarea,select'))e.target.dataset.edited='yes';});
render();

