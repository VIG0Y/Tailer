'use strict';
const reviewForm={source:null,sourceMode:'notification',pick:'',pasted:'',question:'',target:'',username:'',result:null,edited:'',busy:false};
let chosenProvider='',chosenModel=null;

function aiPanel(){
 const list=S.providers||[];
 const selected=list.find(p=>p.id===(chosenProvider||S.provider));
 if(!selected)return '';
 const current=list.find(p=>p.id===S.provider);
 const summary=S.ai?esc(current.title)+' · '+esc(current.model):'Ключ ещё не подключён';
 return box('Нейросеть',summary,
   '<label class="field">Сервис<select id="aiProvider">'+list.map(p=>'<option value="'+esc(p.id)+'" '+(p===selected?'selected':'')+'>'+esc(p.title)+'</option>').join('')+'</select></label>'+
   field('Идентификатор модели','aiModel',chosenModel??selected.model)+
   '<p class="smallprint">Для каждого сервиса хранится отдельный ключ. Выбранная модель используется в чате и при анализе Telegram.</p>'+
   btn('aiConfig',selected.saved?'Использовать или изменить ключ':'Подключить API-ключ','outline'));
}
function reviewPage(){
 const f=reviewForm,src=f.source;
 const names={notification:'Уведомление',file:'Файл JSON',paste:'Вставить текст'};
 let picker='<div class="chips">'+Object.entries(names).map(([k,v])=>btn('src_'+k,v,k===f.sourceMode?'button':'chip')).join('')+'</div>';
 if(f.sourceMode==='notification')picker+='<label class="field">Чат<select id="reviewPick">'+options(S.chats,f.pick,'Выберите чат')+'</select></label><div class="actions">'+btn('loadThread','Загрузить сообщения','outline')+btn('refresh','Обновить','plain')+'</div>'+(!S.tgAccess?btn('tgAccess','Подключить уведомления','plain'):'');
 if(f.sourceMode==='file')picker+='<p class="smallprint">В Telegram Desktop откройте меню нужного чата, экспортируйте историю в формате JSON и выберите result.json.</p>'+btn('reviewFile','Выбрать файл','outline');
 if(f.sourceMode==='paste')picker+='<label class="field">Фрагмент переписки<textarea id="reviewPaste" maxlength="64000" placeholder="Вставьте реплики с именами участников">'+esc(f.pasted)+'</textarea></label>'+btn('loadPasted','Загрузить текст','outline');
 let page=intro('Анализ переписки','Загрузите сообщения одного чата, затем задайте вопрос или поручите Tailer написать ответ.','telegram')+box('Источник сообщений','',picker);
 if(!src)return page;
 const current=(S.providers||[]).find(p=>p.id===S.provider);
 page+=box(esc(src.title),'Загружено сообщений: '+src.lines.length,
   '<details class="review-excerpts"><summary>Посмотреть текст для анализа</summary>'+src.lines.map(m=>'<p>'+(m.author?'<b>'+esc(m.author)+'</b> · ':'')+esc(m.text)+'</p>').join('')+'</details>');
 page+=box('Вопрос или поручение','Например, «На какое время мы договорились?» или «Спроси, удобно ли встретиться в пятницу».',
   area('Ваш текст','reviewQuestion',f.question)+
   (S.ai?'<p class="smallprint">При запросе '+esc(current?.title||'сервис ИИ')+' получит показанные сообщения и ваш текст.</p>':btn('settings','Подключить нейросеть','plain'))+
   (f.busy?'<p role="status">Модель готовит ответ…</p>':'<div class="actions">'+btn('analyzeThread','Ответить мне')+btn('writeThread','Составить реплику','outline')+'</div>'));
 if(f.result){
  page+=box(f.result.draft?'Реплика для собеседника':'Ответ на ваш вопрос',esc(f.result.question),
    '<label class="field">Текст можно изменить<textarea id="reviewEdited" maxlength="4096">'+esc(f.edited)+'</textarea></label>');
 }
 let target;
 if(src.replyId)target='<p>Получатель · <b>'+esc(src.title)+'</b></p>';
 else target='<label class="field">Получатель в Telegram<select id="reviewTarget">'+options(S.chats,f.target,'По @username')+'</select></label>'+
   (!f.target?field('Имя получателя','reviewUser',f.username,'@username'):'');
 return page+box('Отправить в Telegram','',target+'<div class="actions">'+btn('postQuestion','Отправить мой вопрос','outline')+(f.result?btn('postResult','Отправить готовый текст'):'')+'</div>');
}
function reviewAction(a){
 const f=reviewForm;
 if(a.startsWith('src_')){
   if(f.sourceMode!==a.slice(4)){f.sourceMode=a.slice(4);f.source=null;f.result=null;f.target='';f.username='';act('reviewReset');render();}
   return true;
 }
 switch(a){
 case 'aiConfig':act(a,{provider:$('aiProvider').value,model:$('aiModel').value.trim()});return true;
 case 'loadThread':act('reviewLoad',{id:f.pick});return true;
 case 'loadPasted':act('reviewLoad',{pasted:f.pasted});return true;
 case 'analyzeThread':case 'writeThread':
   if(f.busy)return true;
   act('reviewAsk',{id:f.source.id,question:f.question,draft:a==='writeThread'});return true;
 case 'postQuestion':case 'postResult':{
   const text=(a==='postQuestion'?f.question:f.edited).trim();
   if(!text){notice('Введите текст для отправки.');return true;}
   if(text.length>4096){notice('Сократите сообщение до 4096 знаков.');return true;}
   const id=f.source.replyId||f.target;
   if(id)act('tgReply',{id,text});else if(f.username.trim())act('tgSend',{name:f.username,text});else notice('Выберите чат или введите @username.');
   return true;
 }
 default:return false;
 }
}
function reviewEvent(type,payload){
 const f=reviewForm;
 if(type==='reviewSource'){
   f.source=JSON.parse(payload);f.result=null;f.edited='';f.target='';f.username='';
   if(view==='review')render();return;
 }
 if(type==='reviewBusy'){f.busy=payload==='true';if(view==='review')render();return;}
 if(type==='reviewResult'){
   const answer=JSON.parse(payload);if(f.source?.id!==answer.id)return;
   if(answer.error){notice(answer.error);return;}
   f.result=answer;f.edited=answer.text;if(view==='review')render();else notice('Ответ готов в разделе «Анализ переписки».');
 }
}
document.addEventListener('input',e=>{
 const map={reviewPaste:'pasted',reviewQuestion:'question',reviewEdited:'edited',reviewUser:'username'};
 if(map[e.target.id])reviewForm[map[e.target.id]]=e.target.value;
 if(e.target.id==='aiModel')chosenModel=e.target.value;
});
document.addEventListener('change',e=>{
 const el=e.target;
 if(el.id==='aiProvider'){chosenProvider=el.value;chosenModel=null;render();}
 if(el.id==='reviewTarget'){reviewForm.target=el.value;render();}
 if(el.id==='reviewPick'){
   reviewForm.pick=el.value;reviewForm.source=null;reviewForm.result=null;act('reviewReset');render();
 }
});
