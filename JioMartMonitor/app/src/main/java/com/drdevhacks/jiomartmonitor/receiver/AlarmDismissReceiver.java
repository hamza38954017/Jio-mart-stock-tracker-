package com.drdevhacks.jiomartmonitor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.drdevhacks.jiomartmonitor.AlarmActivity;

public class AlarmDismissReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Tell AlarmActivity to dismiss if running
        Intent dismiss = new Intent(AlarmActivity.ACTION_DISMISS);
        context.sendBroadcast(dismiss);
    }
}
