package ru.tailer.app;

import android.content.*;
import org.json.*;

final class Data {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tailer", Context.MODE_PRIVATE); }
    static JSONObject obj(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) o.put((String)pairs[i],pairs[i+1]); } catch(JSONException e){ throw new IllegalArgumentException(e); }
        return o;
    }
    static JSONArray array(String json){ try{return new JSONArray(json);}catch(JSONException e){return new JSONArray();} }
    static synchronized void log(Context c,String topic,String message) {
        SharedPreferences p=prefs(c);
        JSONArray list=array(p.getString("journal","[]"));
        list.put(obj("time",System.currentTimeMillis(),"topic",topic,"text",message));
        while(list.length()>80)list.remove(0);
        p.edit().putString("journal",list.toString()).apply();
    }
    static final String[] TELEGRAM={"org.telegram.messenger","org.telegram.messenger.web","org.thunderdog.challegram"};
    static boolean telegram(String pkg){for(String s:TELEGRAM)if(s.equals(pkg))return true;return false;}
    static boolean matchesNumber(String a,String b){
        if(a==null||b==null)return false;
        String x=android.telephony.PhoneNumberUtils.normalizeNumber(a), y=android.telephony.PhoneNumberUtils.normalizeNumber(b);
        return !x.isEmpty() && x.equals(y);
    }
}
