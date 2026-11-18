package ru.tailer.app;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    WebView web; SharedPreferences prefs; boolean ready,askingAI,requestedTorch;
    Conversation review;
    boolean reviewing;
    int sourceVersion;
    static final String ORIGIN="https://tailer.invalid/";
    RoleManager roles(){return getSystemService(RoleManager.class);}
    boolean phoneRole(){return roles().isRoleAvailable(RoleManager.ROLE_DIALER)&&roles().isRoleHeld(RoleManager.ROLE_DIALER);}
    public void onCreate(Bundle b){
        super.onCreate(b);prefs=Data.prefs(this);int bg=Color.rgb(12,18,23);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        web=new WebView(this);web.setBackgroundColor(bg);
        if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)WebView.setWebContentsDebuggingEnabled(true);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(bg);frame.addView(web);
        if(Build.VERSION.SDK_INT>=35)frame.setOnApplyWindowInsetsListener((v,i)->{
            android.graphics.Insets safe=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return WindowInsets.CONSUMED;
        });
        setContentView(frame);web.setWebChromeClient(new WebChromeClient());
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                String u=r.getUrl().toString();String filename=u.startsWith(ORIGIN)?u.substring(ORIGIN.length()):"";
                if(Arrays.asList("index.html","app.css","app.js","game.js","review.js").contains(filename)){
                    try{return new WebResourceResponse(filename.endsWith(".html")?"text/html":filename.endsWith(".css")?"text/css":"application/javascript","UTF-8",getAssets().open(filename));}
                    catch(IOException ignored){}
                }
                return new WebResourceResponse("text/plain","utf-8",new ByteArrayInputStream(new byte[0]));
            }
            public void onPageFinished(WebView v,String url){ready=true;refresh();tileIntent();}
        });
        web.addJavascriptInterface(new Bridge(),"Tailer");web.loadUrl(ORIGIN+"index.html");
    }
    protected void onResume(){super.onResume();if(ready)refresh();}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(ready)tileIntent();}
    void tileIntent(){if("ru.tailer.app.COMPOSE".equals(getIntent().getAction())){getIntent().setAction(null);event("compose","");}}
    public void onBackPressed(){if(ready)web.evaluateJavascript("window.goBack()",v->{if("false".equals(v))super.onBackPressed();});else super.onBackPressed();}
    public void onDestroy(){ready=false;web.removeJavascriptInterface("Tailer");web.destroy();super.onDestroy();}
    void event(String name,Object payload){
        if(ready&&!isFinishing())web.evaluateJavascript("window.nativeEvent("+JSONObject.quote(name)+","+JSONObject.quote(String.valueOf(payload))+")",null);
    }
    void toast(String s){event("notice",s);}
    void refresh(){
        TelegramService ts=TelegramService.connected;
        JSONObject state=Data.obj("role",phoneRole(),"phoneAuto",prefs.getBoolean("phoneAuto",false),"number",prefs.getString("phoneNumber",""),
            "tgAccess",ts!=null,"chatId",prefs.getString("tgId",""),"chatTitle",prefs.getString("tgTitle",""),"tgAuto",prefs.getBoolean("tgAuto",false),"reply",prefs.getString("tgText","Сейчас занят. Отвечу чуть позже."),
            "chats",ts==null?new JSONArray():ts.choices(),"gameAccess",GameService.live!=null,"games",GameService.choices(this),"journal",Data.array(prefs.getString("journal","[]")),
            "ai",AssistantAI.enabled(prefs),"provider",AssistantAI.active(prefs).name(),"providers",AssistantAI.providers(prefs),
            "askBusy",askingAI,"tgSendAccess",TelegramSendService.live!=null);
        event("state",state);
    }
    public class Bridge {
        @JavascriptInterface public void act(String action,String json){
            runOnUiThread(()->{
                try{handle(action,new JSONObject(json));}
                catch(ActivityNotFoundException e){toast("На устройстве нет приложения для этого действия.");}
                catch(Exception e){toast("Действие недоступно. Проверьте разрешения в настройках Android.");}
            });
        }
    }
    void handle(String a,JSONObject j)throws Exception{
        switch(a){
            case "refresh":refresh();break;
            case "role":
                if(!roles().isRoleAvailable(RoleManager.ROLE_DIALER)){toast("На этом устройстве нет роли телефона.");break;}
                if(!phoneRole())startActivityForResult(roles().createRequestRoleIntent(RoleManager.ROLE_DIALER),42);
                else notificationPermission();
                break;
            case "dial":startActivity(new Intent(this,PhoneActivity.class).setAction(Intent.ACTION_DIAL));break;
            case "phoneSave":{
                String num=j.optString("number").trim();boolean on=j.optBoolean("on");
                if(on&&(!phoneRole()||!num.matches("[+0-9() .-]{3,30}")||android.telephony.PhoneNumberUtils.normalizeNumber(num).length()<3)){toast("Назначьте Tailer телефоном и введите номер с кодом страны.");break;}
                prefs.edit().putString("phoneNumber",num).putBoolean("phoneAuto",on).apply();refresh();toast("Настройки звонков сохранены.");break;
            }
            case "tgAccess":startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));break;
            case "tgSave":{
                String id=j.optString("id"),msg=j.optString("text").trim(),title=prefs.getString("tgTitle","");boolean on=j.optBoolean("on");
                TelegramService ts=TelegramService.connected;boolean exists=!id.isEmpty()&&id.equals(prefs.getString("tgId",""));
                if(ts!=null)for(TelegramService.Chat c:ts.current())if(c.id.equals(id)){exists=true;title=c.title;}
                if(on&&(!exists||msg.isEmpty()||ts==null)){toast("Нужны доступ к уведомлениям, выбранный чат и текст ответа.");break;}
                prefs.edit().putString("tgId",id).putString("tgTitle",title).putString("tgText",msg).putBoolean("tgAuto",on).apply();
                refresh();toast("Правило Telegram сохранено.");break;
            }
            case "tgReply":toast(TelegramService.connected==null?"Подключите уведомления Telegram.":TelegramService.connected.manual(j.optString("id"),j.optString("text")));refresh();break;
            case "tgOpen":openTelegram(null);break;
            case "tgSend":{
                String name=j.optString("name").trim().replaceFirst("^@",""),msg=j.optString("text").trim();
                if(!name.matches("[A-Za-z][A-Za-z0-9_]{3,31}")||msg.isEmpty()||msg.length()>4096){toast("Проверьте @username и текст до 4096 знаков.");break;}
                TelegramSendService svc=TelegramSendService.live;
                if(svc==null){toast("Включите службу «Tailer · Отправка в Telegram».");break;}
                boolean launched=false;
                for(String pkg:Data.TELEGRAM){
                    Uri.Builder link=new Uri.Builder().scheme("tg").authority("resolve").appendQueryParameter("domain",name);
                    Intent i=new Intent(Intent.ACTION_VIEW,link.build()).setPackage(pkg);
                    if(i.resolveActivity(getPackageManager())==null)continue;
                    String marker=svc.prepare(pkg,name,msg);
                    try{startActivity(i.setData(link.appendQueryParameter("text",marker).build()));launched=true;}
                    catch(RuntimeException e){svc.reset();throw e;}
                    break;
                }
                if(!launched)toast("Установите Telegram.");break;
            }
            case "tgDraft":{
                String name=j.optString("name").trim().replaceFirst("^@",""),msg=j.optString("text").trim();
                if(!name.matches("[A-Za-z][A-Za-z0-9_]{3,31}")||msg.isEmpty()){toast("Введите @username получателя и текст.");break;}
                openTelegram(new Uri.Builder().scheme("tg").authority("resolve").appendQueryParameter("domain",name).appendQueryParameter("text",msg).build());break;
            }
            case "gameAccess":startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));break;
            case "game":{
                if(GameService.live==null){toast("Включите службу игровых касаний Tailer.");break;}
                toast(GameService.live.launch(j.optString("pkg"),j.optBoolean("record"),j.optInt("count",1),j.optInt("ms",800)));break;
            }
            case "voice":{
                Intent voice=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU").putExtra(RecognizerIntent.EXTRA_PROMPT,"Команда для Tailer");startActivityForResult(voice,41);break;
            }
            case "volume":{
                AudioManager audio=getSystemService(AudioManager.class);int v=Math.max(0,Math.min(100,j.optInt("value",50)));audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(v/100f*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),AudioManager.FLAG_SHOW_UI);event("answer","Громкость медиа установлена на "+Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))+"%.");break;
            }
            case "display":startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));break;
            case "network":startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));break;
            case "camera":startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));break;
            case "torch":requestedTorch=j.optBoolean("on",true);
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},44);
                else torch();break;
            case "focus":try{startActivity(new Intent("android.settings.ZEN_MODE_SETTINGS"));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}break;
            case "tile":
                if(Build.VERSION.SDK_INT>=33)getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this,TailerTile.class),"Tailer",android.graphics.drawable.Icon.createWithResource(this,R.drawable.tailer),getMainExecutor(),result->toast(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED||result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED?"Плитка Tailer доступна в шторке.":"Плитку можно добавить при редактировании шторки."));
                else toast("Откройте редактирование шторки Android и добавьте плитку Tailer.");
                break;
            case "youtube":startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/")));break;
            case "aiConfig":configure(AssistantAI.Provider.valueOf(j.optString("provider",AssistantAI.active(prefs).name())),j.optString("model"));break;
            case "reviewReset":sourceVersion++;review=null;break;
            case "reviewFile":
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),45);break;
            case "reviewLoad":{
                Conversation loaded=null;
                try{
                    if(j.has("pasted"))loaded=Conversation.pasted(j.getString("pasted"));
                    else if(TelegramService.connected!=null){
                        for(TelegramService.Chat c:TelegramService.connected.current())
                            if(c.id.equals(j.optString("id"))){loaded=Conversation.notification(c);break;}
                    }
                    if(loaded==null){toast("Обновите список и выберите открытое уведомление Telegram.");break;}
                    sourceVersion++;review=loaded;event("reviewSource",review.preview());
                }catch(IOException e){toast(e.getMessage());}break;
            }
            case "reviewAsk":{
                final Conversation source=review;
                final String question=j.optString("question").trim();
                if(source==null||!source.id.equals(j.optString("id"))){toast("Загрузите переписку ещё раз.");break;}
                if(question.isEmpty()||question.length()>2000){toast("Введите поручение до 2000 знаков.");break;}
                if(!AssistantAI.enabled(prefs)){toast("Подключите нейросеть в настройках.");break;}
                if(reviewing){toast("Tailer уже обрабатывает запрос.");break;}
                boolean draft=j.optBoolean("draft");reviewing=true;event("reviewBusy","true");
                new Thread(()->{
                    String result="",error="";
                    try{result=AssistantAI.telegram(this,question,source.lines,draft);}
                    catch(Exception e){error=AssistantAI.explain(e);}
                    JSONObject response=Data.obj("id",source.id,"question",question,"draft",draft,"text",result,"error",error);
                    runOnUiThread(()->{reviewing=false;event("reviewBusy","false");event("reviewResult",response);});
                }).start();break;
            }
            case "ask":{
                if(!AssistantAI.enabled(prefs)){event("answer","Для свободных вопросов подключите ИИ в настройках. Локальные команды доступны сразу.");break;}
                if(askingAI){toast("Дождитесь текущего ответа.");break;}
                askingAI=true;JSONArray messages=j.getJSONArray("messages");refresh();
                new Thread(()->{String answer;try{answer=AssistantAI.chat(this,messages);}catch(Exception e){answer=AssistantAI.explain(e);}
                    final String result=answer;runOnUiThread(()->{askingAI=false;if(ready){event("answer",result);refresh();}});}).start();break;
            }
        }
    }
    void openTelegram(Uri uri){
        for(String pkg:Data.TELEGRAM){
            Intent i=uri==null?getPackageManager().getLaunchIntentForPackage(pkg):new Intent(Intent.ACTION_VIEW,uri).setPackage(pkg);
            if(i!=null&&i.resolveActivity(getPackageManager())!=null){startActivity(i);return;}
        }toast("Установите официальный Telegram или Telegram X.");
    }
    void configure(AssistantAI.Provider service,String selectedModel){
        String model=selectedModel.isEmpty()?service.model(prefs):selectedModel.trim();
        EditText input=new EditText(this);input.setSingleLine();input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        boolean saved=prefs.contains(service.slot("secret"));
        input.setHint(saved?"Оставьте пустым для сохранённого ключа":"API-ключ");
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(service.title+" · "+model)
            .setMessage("Ключ шифруется на устройстве. Запросы оплачиваются по тарифу вашего API-аккаунта.").setView(input)
            .setPositiveButton("Подключить",null).setNegativeButton("Отмена",null);
        if(saved)builder.setNeutralButton("Удалить ключ",(d,w)->{
            prefs.edit().remove(service.slot("secret")).remove(service.slot("iv")).apply();refresh();toast("Ключ удалён.");
        });
        AlertDialog dialog=builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{AssistantAI.save(this,service,model,input.getText().toString().trim());dialog.dismiss();refresh();toast("Подключён "+service.title+".");}
            catch(IllegalArgumentException e){input.setError(e.getMessage());}
            catch(Exception e){input.setError("Не удалось сохранить ключ на устройстве.");}
        });
    }
    void notificationPermission(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);}
    void torch(){
        android.hardware.camera2.CameraManager camera=getSystemService(android.hardware.camera2.CameraManager.class);
        try{for(String id:camera.getCameraIdList()){
            Boolean flash=camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if(Boolean.TRUE.equals(flash)){camera.setTorchMode(id,requestedTorch);event("answer",requestedTorch?"Фонарик включён.":"Фонарик выключен.");return;}
        }event("answer","На устройстве нет доступной вспышки.");}catch(Exception e){event("answer","Вспышка занята или недоступна.");}
    }
    public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==44){if(results.length>0&&results[0]==0)torch();else event("answer","Для фонарика нужен доступ к камере.");}}
    protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==45&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            final Uri uri=data.getData();final int revision=++sourceVersion;
            new Thread(()->{
                try{
                    Conversation loaded=Conversation.exported(getContentResolver().openInputStream(uri));
                    runOnUiThread(()->{if(ready&&revision==sourceVersion){review=loaded;event("reviewSource",loaded.preview());}});
                }catch(Exception e){runOnUiThread(()->{if(revision==sourceVersion)toast("Не удалось прочитать историю. Выберите JSON-экспорт одного чата с текстовыми сообщениями.");});}
            }).start();
        }
        if(req==42&&result==RESULT_OK)notificationPermission();
        if(req==41&&result==RESULT_OK&&data!=null){ArrayList<String> hits=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(hits!=null&&!hits.isEmpty())event("voice",hits.get(0));}
        if(ready)refresh();
    }
}
