package com.mycompany.datatracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class TopUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(Alerts.TOP_UP_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
