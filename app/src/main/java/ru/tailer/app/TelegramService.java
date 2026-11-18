package ru.tailer.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import org.json.*;
import java.util.*;

public class TelegramService extends NotificationListenerService {
    static volatile TelegramService connected;
    static class Chat {
        final StatusBarNotification sbn;
        final String title,id,fingerprint;
        final Notification.Action action;
        final RemoteInput input;
        Chat(StatusBarNotification s,String name,Notification.Action a,RemoteInput ri,String stamp) {
            sbn=s; title=name; action=a; input=ri; fingerprint=stamp;
            String shortcut=s.getNotification().getShortcutId();
            id=s.getPackageName()+"|"+s.getUser().hashCode()+"|"+(shortcut==null?s.getKey():shortcut)+"|"+name;
        }
    }
    public void onListenerConnected(){connected=this;}
    public void onListenerDisconnected(){connected=null;}
    public void onDestroy(){if(connected==this)connected=null;super.onDestroy();}
    Chat parse(StatusBarNotification s) {
        if(!Data.telegram(s.getPackageName()))return null;
        Notification n=s.getNotification();
        if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0 || n.actions==null)return null;
        Bundle x=n.extras;
        CharSequence title=x.getCharSequence(Notification.EXTRA_TITLE);
        if(title==null||title.toString().trim().isEmpty())return null;
        String stamp=String.valueOf(n.when)+"|"+x.getCharSequence(Notification.EXTRA_TEXT,"");
        Parcelable[] messages=x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(messages!=null && messages.length>0 && messages[messages.length-1] instanceof Bundle){
            Bundle last=(Bundle)messages[messages.length-1];
            if(last.getCharSequence("sender")==null && last.getParcelable("sender_person")==null)return null;
            stamp=last.getLong("time")+"|"+last.getCharSequence("text","");
        }
        for(Notification.Action a:n.actions){
            if(a.actionIntent==null || a.getRemoteInputs()==null)continue;
            for(RemoteInput input:a.getRemoteInputs())if(input.getAllowFreeFormInput())
                return new Chat(s,title.toString(),a,input,Integer.toHexString(stamp.hashCode()));
        }
        return null;
    }
    List<Chat> current(){
        List<Chat> chats=new ArrayList<>();
        try{
            StatusBarNotification[] active=getActiveNotifications();
            if(active!=null)for(StatusBarNotification s:active){Chat c=parse(s);if(c!=null)chats.add(c);}
        }catch(RuntimeException ignored){}
        return chats;
    }
    JSONArray choices(){JSONArray out=new JSONArray();for(Chat c:current())out.put(Data.obj("id",c.id,"title",c.title));return out;}
    String manual(String id,String msg){
        for(Chat c:current())if(c.id.equals(id))return send(c,msg);
        return "Уведомление уже закрыто. Дождитесь нового сообщения.";
    }
    private String send(Chat c,String msg){
        if(msg.trim().isEmpty())return "Введите сообщение.";
        Intent reply=new Intent();Bundle result=new Bundle();result.putCharSequence(c.input.getResultKey(),msg.trim());
        RemoteInput.addResultsToIntent(new RemoteInput[]{c.input},reply,result);
        RemoteInput.setResultsSource(reply,RemoteInput.SOURCE_FREE_FORM_INPUT);
        try{c.action.actionIntent.send(this,0,reply);Data.log(this,"Telegram","Ответ передан в чат «"+c.title+"»");return "Ответ передан Telegram.";}
        catch(PendingIntent.CanceledException e){return "Telegram отозвал действие ответа. Нужное уведомление устарело.";}
    }
    public void onNotificationPosted(StatusBarNotification s){
        SharedPreferences p=Data.prefs(this);
        if(!p.getBoolean("tgAuto",false))return;
        Chat c=parse(s);
        if(c==null||!c.id.equals(p.getString("tgId","")))return;
        String token=c.id+"|"+c.fingerprint;
        if(token.equals(p.getString("tgLast","")) || System.currentTimeMillis()-p.getLong("tgTime",0)<600000L)return;
        String msg=p.getString("tgText","");if(msg.trim().isEmpty())return;
        if(send(c,msg).equals("Ответ передан Telegram.")){
            p.edit().putString("tgLast",token).putLong("tgTime",System.currentTimeMillis()).apply();
        }
    }
}
