package ru.tailer.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.telecom.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class PhoneActivity extends Activity {
    LinearLayout body; EditText field; boolean hadCall;
    final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){draw();}};
    public void onCreate(Bundle state){super.onCreate(state);
        IntentFilter f=new IntentFilter("ru.tailer.app.CALL");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
        draw();
    }
    public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);draw();}
    public void onDestroy(){unregisterReceiver(receiver);super.onDestroy();}
    void label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,12,0,12);body.addView(v);}
    void button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->action.run());body.addView(b);}
    void dial(){
        String n=field.getText().toString().trim();if(n.isEmpty())return;
        if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CALL_PHONE},9);return;}
        try{getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel",n,null),new Bundle());}
        catch(RuntimeException e){Toast.makeText(this,"Не удалось начать вызов",Toast.LENGTH_LONG).show();}
    }
    public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==9&&g.length>0&&g[0]==0&&field!=null)dial();}
    void draw(){
        PhoneService srv=PhoneService.live;Call c=srv==null?null:srv.selected();
        if(c==null && (hadCall || "ru.tailer.app.CALL_UI".equals(getIntent().getAction()))){finish();return;}
        body=new LinearLayout(this);body.setOrientation(1);body.setGravity(Gravity.CENTER_VERTICAL);body.setPadding(52,32,52,32);body.setBackgroundColor(Color.rgb(17,25,32));setContentView(body);
        label("T A I L E R   /   ТЕЛЕФОН",14,0xffa2e8d8);
        if(c==null){
            label("Набрать номер",30,-1);field=new EditText(this);field.setSingleLine();field.setHint("+7…");field.setInputType(3);if(getIntent().getData()!=null && "tel".equals(getIntent().getData().getScheme()))field.setText(getIntent().getData().getSchemeSpecificPart());body.addView(field);button("Позвонить",this::dial);return;
        }
        hadCall=true;int state=c.getState();
        label(state==Call.STATE_RINGING?"Входящий звонок":state==Call.STATE_ACTIVE?"Идёт разговор":state==Call.STATE_HOLDING?"На удержании":"Соединение",26,-1);
        String n=PhoneService.number(c);label(n.isEmpty()?"Скрытый номер":n,24,0xffa2e8d8);
        if(state==Call.STATE_RINGING){button("Принять",()->c.answer(VideoProfile.STATE_AUDIO_ONLY));button("Отклонить",()->c.reject(false,null));}
        else {
            CallAudioState audio=srv.getCallAudioState();
            if(audio!=null){
                button(audio.isMuted()?"Включить микрофон":"Выключить микрофон",()->srv.setMuted(!audio.isMuted()));
                boolean speaker=audio.getRoute()==CallAudioState.ROUTE_SPEAKER;
                button(speaker?"Динамик телефона":"Громкая связь",()->srv.setAudioRoute(speaker?CallAudioState.ROUTE_WIRED_OR_EARPIECE:CallAudioState.ROUTE_SPEAKER));
            }
            button("Завершить",c::disconnect);
        }
    }
}
