package local.barbook.probe;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** adb-only commands (the receiver requires android.permission.DUMP). */
public class Cmd extends BroadcastReceiver {
  @Override public void onReceive(Context ctx, Intent i) {
    String[] a = String.valueOf(i.getStringExtra("c")).split(" ");
    ProbeA11y s = ProbeA11y.I;
    Log.i(ProbeA11y.T, "CMD " + String.join(" ", a) + " a11y=" + (s != null));
    try {
      switch (a[0]) {
        case "sb": s.windows(); break;
        case "ov": s.overlay(a[1].equals("on")); break;
        case "chip": s.chip(a[1], a.length > 2 ? Integer.parseInt(a[2]) : 1); break;
        case "chipoff": ctx.getSystemService(NotificationManager.class).cancelAll(); break;
        case "agent": s.agent(); break;
        case "fs": ctx.startActivity(new Intent(ctx, FsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); break;
        case "fsoff": if (FsActivity.I != null) FsActivity.I.finish(); break;
        default: Log.w(ProbeA11y.T, "unknown command");
      }
    } catch (Throwable x) { Log.w(ProbeA11y.T, "CMD failed", x); }
  }
}
