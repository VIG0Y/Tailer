package ru.tailer.app;

import android.accessibilityservice.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import org.json.*;

public class GameService extends AccessibilityService {
    static GameService live;
    final Handler timer=new Handler(Looper.getMainLooper());
    String target="",mode="";
    boolean started;
    View capture,bar;
    JSONArray points;
    int index,repeats,interval,width,height;
    TextView counter;
    final BroadcastReceiver screenOff=new BroadcastReceiver(){public void onReceive(Context c,Intent i){end("Экран выключен");}};
    public void onServiceConnected(){live=this;registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));}
    static boolean isGame(ApplicationInfo a){return a.category==ApplicationInfo.CATEGORY_GAME||(a.flags&ApplicationInfo.FLAG_IS_GAME)!=0;}
    static JSONArray choices(Context c){
        PackageManager pm=c.getPackageManager();JSONArray a=new JSONArray();
        Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(ResolveInfo r:pm.queryIntentActivities(q,0)){
            ApplicationInfo info=r.activityInfo.applicationInfo;
            if(isGame(info)&&seen.add(info.packageName))a.put(Data.obj("id",info.packageName,"title",info.loadLabel(pm).toString()));
        }
        return a;
    }
    String launch(String pkg,boolean record,int count,int ms){
        if(!target.isEmpty())end("");
        try{
            PackageManager pm=getPackageManager();
            if(!isGame(pm.getApplicationInfo(pkg,0)))return "Выберите установленную игру.";
            Intent i=pm.getLaunchIntentForPackage(pkg);if(i==null)return "Игра недоступна.";
            points=record?new JSONArray():Data.array(Data.prefs(this).getString("taps."+pkg,"[]"));
            if(!record&&points.length()==0)return "Сначала отметьте точки в этой игре.";
            target=pkg;mode=record?"record":"play";repeats=Math.max(1,Math.min(20,count));interval=Math.max(400,Math.min(ms,5000));index=0;
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            timer.postDelayed(this::watchGame,250);
            timer.postDelayed(()->{if(!started)end("Игра не открылась");},8000);
            return "Открываю игру.";
        }catch(RuntimeException|PackageManager.NameNotFoundException e){end("");return "Не удалось открыть игру.";}
    }
    boolean inGame(){
        android.view.accessibility.AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null && root.getPackageName()!=null && target.contentEquals(root.getPackageName());
    }
    void watchGame(){
        if(target.isEmpty())return;
        if(inGame()){
            if(!started){started=true;begin();}
        }else if(started){end("Игра ушла с экрана");return;}
        if(!target.isEmpty())timer.postDelayed(this::watchGame,350);
    }
    public void onAccessibilityEvent(AccessibilityEvent e){
        if(target.isEmpty()||e.getPackageName()==null)return;
        String pkg=e.getPackageName().toString();
        if(target.equals(pkg)){
            if(!started && inGame()){started=true;begin();}
        }else if(started){
            if(pkg.equals(getPackageName()) && e.getClassName()!=null && !e.getClassName().toString().endsWith("Activity"))return;
            end("Игра ушла с экрана");
        }
    }
    WindowManager wm(){return getSystemService(WindowManager.class);}
    WindowManager.LayoutParams layout(int w,int h,int gravity){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=gravity;return p;
    }
    void begin(){
        if(target.isEmpty())return;
        Point size=new Point();wm().getDefaultDisplay().getRealSize(size);width=size.x;height=size.y;
        if(mode.equals("play")&&(Data.prefs(this).getInt("w."+target,0)!=width||Data.prefs(this).getInt("h."+target,0)!=height)){end("Поверните экран как при записи");return;}
        try{
            if(mode.equals("record")){
                capture=new View(this){
                    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                    protected void onDraw(Canvas c){int[] offset=new int[2];getLocationOnScreen(offset);paint.setColor(0xffa9f1df);for(int k=0;k<points.length();k++){JSONArray p=points.optJSONArray(k);c.drawCircle(p.optInt(0)-offset[0],p.optInt(1)-offset[1],16,paint);}}
                };
                capture.setBackgroundColor(0x22152228);
                capture.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP && points.length()<12){points.put(new JSONArray().put((int)e.getRawX()).put((int)e.getRawY()));counter.setText("Точки: "+points.length()+"  ");v.invalidate();}return true;});
                wm().addView(capture,layout(-1,-1,Gravity.TOP));
            }
            LinearLayout controls=new LinearLayout(this);bar=controls;controls.setBackgroundColor(0xff152227);controls.setGravity(Gravity.CENTER);controls.setPadding(10,0,4,0);
            counter=new TextView(this);counter.setTextColor(0xffa9f1df);counter.setText(mode.equals("record")?"Точки: 0  ":"Tailer   ");controls.addView(counter);
            if(mode.equals("record"))btn(controls,"Сохранить",()->{
                if(points.length()==0){Toast.makeText(this,"Отметьте хотя бы одну точку",0).show();return;}
                Data.prefs(this).edit().putString("taps."+target,points.toString()).putInt("w."+target,width).putInt("h."+target,height).apply();end("Точки сохранены");
            });
            btn(controls,"Стоп",()->end("Остановлено"));
            wm().addView(bar,layout(-2,-2,Gravity.TOP|Gravity.END));
            if(mode.equals("play"))timer.postDelayed(this::tap,750);
        }catch(RuntimeException e){end("Игровая панель недоступна");}
    }
    void btn(LinearLayout l,String title,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setOnClickListener(v->r.run());l.addView(b);}
    void tap(){
        if(!started||!"play".equals(mode))return;
        if(!inGame()){end("Игра ушла с экрана");return;}
        if(index>=points.length()*repeats){end("Повтор завершён");return;}
        JSONArray p=points.optJSONArray(index%points.length());
        Path path=new Path();path.moveTo(p.optInt(0),p.optInt(1));
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,55)).build();
        index++;counter.setText(index+" / "+points.length()*repeats+"   ");
        if(!dispatchGesture(g,new GestureResultCallback(){
            public void onCompleted(GestureDescription d){if(started&&"play".equals(mode))timer.postDelayed(GameService.this::tap,interval);}
            public void onCancelled(GestureDescription d){if(started)end("Касание прервано");}
        },null))end("Android отклонил касание");
    }
    void end(String reason){
        boolean active=!target.isEmpty();timer.removeCallbacksAndMessages(null);started=false;target="";mode="";
        for(View v:new View[]{capture,bar})if(v!=null)try{wm().removeViewImmediate(v);}catch(RuntimeException ignored){}
        capture=null;bar=null;
        if(active&&!reason.isEmpty()){Data.log(this,"Игры",reason);Toast.makeText(this,reason,0).show();}
    }
    public void onConfigurationChanged(Configuration newConfig){super.onConfigurationChanged(newConfig);if(started&&bar!=null)end("Изменилась ориентация экрана");}
    public void onInterrupt(){end("Служба приостановлена");}
    public void onDestroy(){end("");if(live==this){unregisterReceiver(screenOff);live=null;}super.onDestroy();}
}
