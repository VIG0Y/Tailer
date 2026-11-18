package ru.tailer.app;

import android.app.Notification;
import android.app.Person;
import android.os.*;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

final class Conversation {
    final String id=UUID.randomUUID().toString(),title,replyId;
    final JSONArray lines;
    private Conversation(String title,String replyId,JSONArray lines){
        this.title=title;this.replyId=replyId;this.lines=lines;
    }
    JSONObject preview(){return Data.obj("id",id,"title",title,"replyId",replyId,"lines",lines);}
    static final class Recent {
        final ArrayDeque<JSONObject> lines=new ArrayDeque<>();
        void add(String who,String text){
            if(text==null||text.trim().isEmpty())return;
            String body=text.trim();
            if(body.length()>1600)body=body.substring(0,1599)+"…";
            String author=who==null?"":who.trim();
            lines.addLast(Data.obj("author",author.substring(0,Math.min(100,author.length())),"text",body));
            if(lines.size()>40)lines.removeFirst();
        }
        Conversation finish(String title,String replyId)throws IOException{
            if(lines.isEmpty())throw new IOException("В источнике нет доступных текстовых сообщений.");
            return new Conversation(title,replyId,new JSONArray(lines));
        }
    }
    static Conversation notification(TelegramService.Chat chat)throws IOException{
        Bundle extras=chat.sbn.getNotification().extras;Recent recent=new Recent();
        Parcelable[] messages=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(messages!=null)for(Parcelable part:messages){
            if(!(part instanceof Bundle))continue;
            Bundle m=(Bundle)part;CharSequence text=m.getCharSequence("text"),author=m.getCharSequence("sender");
            Object person=m.getParcelable("sender_person");
            if(person instanceof Person)author=((Person)person).getName();
            if(text!=null)recent.add(author==null?"Вы":author.toString(),text.toString());
        }
        if(recent.lines.isEmpty()){
            CharSequence[] rows=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if(rows!=null)for(CharSequence line:rows)if(line!=null)recent.add(chat.title,line.toString());
            if(recent.lines.isEmpty()){
                CharSequence value=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if(value==null)value=extras.getCharSequence(Notification.EXTRA_TEXT);
                if(value!=null)recent.add(chat.title,value.toString());
            }
        }
        return recent.finish(chat.title,chat.id);
    }
    static Conversation pasted(String text)throws IOException{
        Recent recent=new Recent();
        for(String line:text.split("\\r?\\n"))recent.add("",line);
        return recent.finish("Вставленная переписка","");
    }
    static Conversation exported(InputStream source)throws IOException{
        if(source==null)throw new IOException("Не удалось открыть файл.");
        Recent recent=new Recent();String title="Экспорт Telegram";boolean found=false;
        try(JsonReader reader=new JsonReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            reader.beginObject();
            while(reader.hasNext()){
                String field=reader.nextName();
                if(field.equals("name")&&reader.peek()==JsonToken.STRING)title=reader.nextString();
                else if(field.equals("messages")&&reader.peek()==JsonToken.BEGIN_ARRAY){
                    found=true;reader.beginArray();
                    while(reader.hasNext())readMessage(reader,recent);
                    reader.endArray();
                }else reader.skipValue();
            }
            reader.endObject();
        }catch(IllegalStateException e){throw new IOException("Ожидается JSON-экспорт одного чата Telegram Desktop.");}
        if(!found)throw new IOException("Выберите result.json из экспорта одного чата Telegram Desktop.");
        return recent.finish(title,"");
    }
    static void readMessage(JsonReader reader,Recent recent)throws IOException{
        if(reader.peek()!=JsonToken.BEGIN_OBJECT){reader.skipValue();return;}
        reader.beginObject();String who="",body="",type="";
        while(reader.hasNext()){
            String key=reader.nextName();
            if(key.equals("text"))body=richText(reader);
            else if(key.equals("from")&&reader.peek()==JsonToken.STRING)who=reader.nextString();
            else if(key.equals("type")&&reader.peek()==JsonToken.STRING)type=reader.nextString();
            else reader.skipValue();
        }
        reader.endObject();if(type.equals("message"))recent.add(who,body);
    }
    static String richText(JsonReader r)throws IOException{
        if(r.peek()==JsonToken.STRING)return r.nextString();
        if(r.peek()!=JsonToken.BEGIN_ARRAY){r.skipValue();return "";}
        StringBuilder b=new StringBuilder();r.beginArray();
        while(r.hasNext()){
            String text="";
            if(r.peek()==JsonToken.STRING)text=r.nextString();
            else if(r.peek()==JsonToken.BEGIN_OBJECT){
                r.beginObject();
                while(r.hasNext()){
                    if(r.nextName().equals("text")&&r.peek()==JsonToken.STRING)text=r.nextString();else r.skipValue();
                }
                r.endObject();
            }else r.skipValue();
            if(b.length()<1600)b.append(text,0,Math.min(1600-b.length(),text.length()));
        }
        r.endArray();return b.toString();
    }
}
