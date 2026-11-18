package ru.tailer.app;

import android.accessibilityservice.AccessibilityService;
import android.os.*;
import android.view.accessibility.*;
import android.widget.Toast;
import java.util.*;

public class TelegramSendService extends AccessibilityService {
    static TelegramSendService live;
    final Handler timer=new Handler(Looper.getMainLooper());
    String pkg="",user="",text="",marker="";
    int chatWindow=-1;
    boolean placed;
    public void onServiceConnected(){live=this;}
    String prepare(String target,String recipient,String message){
        reset();pkg=target;user=recipient;text=message;
        marker="Tailer · черновик "+UUID.randomUUID().toString();
        timer.postDelayed(()->finish("Время ожидания истекло. Проверьте черновик в Telegram."),20000);
        return marker;
    }
    void reset(){timer.removeCallbacksAndMessages(null);pkg="";user="";text="";marker="";chatWindow=-1;placed=false;}
    void finish(String result){if(pkg.isEmpty())return;Data.log(this,"Telegram",result);Toast.makeText(this,result,Toast.LENGTH_LONG).show();reset();}
    public void onAccessibilityEvent(AccessibilityEvent e){
        if(!pkg.isEmpty()&&e.getPackageName()!=null&&pkg.contentEquals(e.getPackageName())&&!timer.hasCallbacks(probe))timer.postDelayed(probe,100);
    }
    final Runnable probe=this::inspect;
    void inspect(){
        if(pkg.isEmpty())return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||!pkg.contentEquals(root.getPackageName()))return;
        if(placed&&root.getWindowId()!=chatWindow){finish("Окно Telegram сменилось. Проверьте черновик.");return;}
        AccessibilityNodeInfo editor=null,send=null;
        ArrayDeque<AccessibilityNodeInfo> todo=new ArrayDeque<>();todo.add(root);
        int remaining=700;
        while(!todo.isEmpty()&&remaining-->0){
            AccessibilityNodeInfo n=todo.removeFirst();
            if(n.isVisibleToUser()&&n.isEnabled()){
                CharSequence t=n.getText();
                if(n.isEditable()&&t!=null&&(placed?text:marker).contentEquals(t))editor=n;
                String desc=String.valueOf(n.getContentDescription()).trim().toLowerCase(Locale.ROOT);
                if(n.isClickable()&&(desc.equals("send")||desc.equals("send message")||desc.equals("отправить")||desc.equals("отправить сообщение")))send=n;
            }
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)todo.add(child);}
        }
        if(editor==null)return;
        if(!placed){
            Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
            chatWindow=root.getWindowId();placed=true;
            if(!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){finish("Telegram не разрешил заполнить поле сообщения.");return;}
            timer.postDelayed(probe,200);return;
        }
        if(send!=null){
            String target=user;boolean ok=send.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            finish(ok?"Нажата отправка в Telegram для @"+target:"Проверьте черновик для @"+target);
        }
    }
    public void onInterrupt(){reset();}
    public void onDestroy(){reset();if(live==this)live=null;super.onDestroy();}
}
