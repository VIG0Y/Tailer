package ru.tailer.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.*;

public class TailerTile extends TileService {
    public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setState(Tile.STATE_ACTIVE);t.setLabel("Tailer");t.updateTile();}}
    public void onClick(){super.onClick();
        Intent i=new Intent(this,MainActivity.class).setAction("ru.tailer.app.COMPOSE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,75,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        else startActivityAndCollapse(i);
    }
}
