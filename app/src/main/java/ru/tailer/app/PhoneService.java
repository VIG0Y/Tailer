package ru.tailer.app;

import android.app.*;
import android.os.*;
import android.telecom.*;
import android.content.*;
import java.util.*;

public class PhoneService extends InCallService {
    static PhoneService live;
    final List<Call> calls=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    final Call.Callback callback=new Call.Callback(){
        public void onStateChanged(Call call,int state){refresh();}
        public void onDetailsChanged(Call call,Call.Details details){refresh();}
    };
    static String number(Call c){return c.getDetails().getHandle()==null?"":c.getDetails().getHandle().getSchemeSpecificPart();}
    Call selected(){
        for(Call c:calls)if(c.getState()==Call.STATE_RINGING)return c;
        for(Call c:calls)if(c.getState()==Call.STATE_ACTIVE)return c;
        return calls.isEmpty()?null:calls.get(calls.size()-1);
    }
    public void onCallAdded(Call c){
        super.onCallAdded(c); live=this;calls.add(c);c.registerCallback(callback);refresh();
        if(c.getState()==Call.STATE_RINGING){
            Data.log(this,"Звонки","Входящий вызов");
            handler.postDelayed(()->{
                if(calls.contains(c)&&c.getState()==Call.STATE_RINGING&&allowed(c)){
                    c.answer(VideoProfile.STATE_AUDIO_ONLY);Data.log(this,"Звонки","Принят вызов с выбранного номера");
                }
            },3000);
        }else if(c.getState()==Call.STATE_DIALING||c.getState()==Call.STATE_CONNECTING){
            startActivity(new Intent(this,PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }
    boolean allowed(Call c){SharedPreferences p=Data.prefs(this);return p.getBoolean("phoneAuto",false)&&Data.matchesNumber(number(c),p.getString("phoneNumber",""));}
    public void onCallRemoved(Call c){c.unregisterCallback(callback);calls.remove(c);refresh();super.onCallRemoved(c);}
    public void onCallAudioStateChanged(CallAudioState s){sendBroadcast(new Intent("ru.tailer.app.CALL").setPackage(getPackageName()));}
    void refresh(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        Call c=selected();
        if(c==null)nm.cancel(701);
        else{
            boolean ring=c.getState()==Call.STATE_RINGING;
            String channel=ring?"incoming":"ongoing";
            NotificationChannel ch=new NotificationChannel(channel,ring?"Входящие звонки":"Текущий разговор",ring?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_LOW);
            if(ring)ch.setSound(null,null);
            nm.createNotificationChannel(ch);
            Intent open=new Intent(this,PhoneActivity.class).setAction("ru.tailer.app.CALL_UI").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(this,25,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b=new Notification.Builder(this,channel).setSmallIcon(ru.tailer.app.R.drawable.tailer).setContentTitle(ring?"Входящий звонок":"Телефон").setContentText(number(c)).setContentIntent(pi).setCategory(Notification.CATEGORY_CALL).setOngoing(true);
            if(ring)b.setFullScreenIntent(pi,true);
            nm.notify(701,b.build());
        }
        sendBroadcast(new Intent("ru.tailer.app.CALL").setPackage(getPackageName()));
    }
    public void onDestroy(){handler.removeCallbacksAndMessages(null);for(Call c:calls)c.unregisterCallback(callback);calls.clear();getSystemService(NotificationManager.class).cancel(701);live=null;super.onDestroy();}
}
