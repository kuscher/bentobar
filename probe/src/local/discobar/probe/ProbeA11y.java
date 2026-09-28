package local.discobar.probe;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.agenticon.AgentTaskState;
import android.agenticon.AgentTaskUpdate;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import java.util.List;

/** Throwaway feasibility probe for DiscoBar. */
public class ProbeA11y extends AccessibilityService {
  static final String T = "BarProbe";
  static volatile ProbeA11y I;
  TextView ov;
  String lastSb = "";

  @Override protected void onServiceConnected() { I = this; Log.i(T, "a11y connected"); }
  @Override public boolean onUnbind(Intent i) { I = null; overlay(false); return super.onUnbind(i); }
  @Override public void onInterrupt() {}

  @Override public void onAccessibilityEvent(AccessibilityEvent e) {
    if (e.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
    String sb = statusBarSummary();
    if (!sb.equals(lastSb)) { lastSb = sb; Log.i(T, "WINDOWS_CHANGED statusbar=" + sb); }
  }

  String statusBarSummary() {
    StringBuilder b = new StringBuilder();
    for (AccessibilityWindowInfo w : getWindows()) {
      Rect r = new Rect(); w.getBoundsInScreen(r);
      if (w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM && r.top == 0 && r.height() < 120)
        b.append(w.getTitle()).append(' ').append(r.toShortString()).append(';');
    }
    return b.length() == 0 ? "NONE" : b.toString();
  }

  void windows() {
    for (AccessibilityWindowInfo w : getWindows()) {
      Rect r = new Rect(); w.getBoundsInScreen(r);
      Log.i(T, String.format("WIN id=%d type=%d layer=%d title=%s bounds=%s active=%b focused=%b",
          w.getId(), w.getType(), w.getLayer(), w.getTitle(), r.toShortString(), w.isActive(), w.isFocused()));
      if (w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM && r.top == 0 && r.height() < 120) {
        AccessibilityNodeInfo root = w.getRoot();
        if (root != null) dump(root, 0);
        else Log.i(T, "  (no root)");
      }
    }
    WindowMetrics m = getSystemService(WindowManager.class).getCurrentWindowMetrics();
    WindowInsets in = m.getWindowInsets();
    Log.i(T, "METRICS bounds=" + m.getBounds().toShortString() + " statusBars=" + in.getInsets(WindowInsets.Type.statusBars())
        + " visible=" + in.isVisible(WindowInsets.Type.statusBars()) + " density=" + getResources().getDisplayMetrics().density);
  }

  void dump(AccessibilityNodeInfo n, int depth) {
    Rect r = new Rect(); n.getBoundsInScreen(r);
    Log.i(T, "  ".repeat(depth + 1) + n.getClassName() + " id=" + n.getViewIdResourceName() + " text=" + n.getText()
        + " desc=" + n.getContentDescription() + " state=" + n.getStateDescription() + " b=" + r.toShortString()
        + (n.isClickable() ? " CLICK" : "") + (n.isVisibleToUser() ? "" : " INVISIBLE"));
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo c = n.getChild(i);
      if (c != null) dump(c, depth + 1);
    }
  }

  void overlay(boolean on) {
    WindowManager wm = getSystemService(WindowManager.class);
    if (ov != null) { wm.removeView(ov); ov = null; }
    if (!on) return;
    int h = wm.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.statusBars()).top;
    if (h <= 0) h = 41;
    ov = new TextView(this);
    ov.setText("DiscoBar ▾ 12.3 MB/s");
    ov.setTextColor(Color.WHITE);
    ov.setTextSize(14);
    ov.setGravity(Gravity.CENTER);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(h / 2f); bg.setColor(0x33FFFFFF);
    ov.setBackground(bg);
    ov.setPadding(24, 0, 24, 0);
    ov.setOnClickListener(v -> Log.i(T, "OV click"));
    ov.setOnContextClickListener(v -> { Log.i(T, "OV context click (right button)"); return true; });
    ov.setOnHoverListener((v, e) -> {
      if (e.getAction() != MotionEvent.ACTION_HOVER_MOVE) Log.i(T, "OV hover " + MotionEvent.actionToString(e.getAction()));
      return false;
    });
    ov.setOnGenericMotionListener((v, e) -> {
      if (e.getAction() == MotionEvent.ACTION_SCROLL) Log.i(T, "OV scroll v=" + e.getAxisValue(MotionEvent.AXIS_VSCROLL));
      return false;
    });
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.LEFT;
    lp.x = 900; lp.y = 0;
    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    lp.setTitle("DiscoBarProbe");
    wm.addView(ov, lp);
    Log.i(T, "overlay added h=" + h);
  }

  void chip(String kind, int id) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("probe", "Probe", NotificationManager.IMPORTANCE_DEFAULT));
    Notification.Builder b = new Notification.Builder(this, "probe")
        .setSmallIcon(Icon.createWithResource(this, R.drawable.dot))
        .setContentTitle("DiscoBar probe " + id)
        .setContentIntent(PendingIntent.getActivity(this, id, new Intent(this, FsActivity.class).putExtra("from", "chip" + id), PendingIntent.FLAG_IMMUTABLE))
        .setContentText("Live Update test")
        .setOngoing(true)
        .setRequestPromotedOngoing(true)
        .setOnlyAlertOnce(true);
    if (kind.equals("text")) b.setShortCriticalText(id + "2.3M");
    if (kind.equals("long")) b.setShortCriticalText("Standup in 12 min");
    if (kind.equals("none")) {}
    if (kind.equals("chrono")) b.setWhen(System.currentTimeMillis() + 5 * 60_000).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true);
    if (kind.equals("metric")) {
      b.setStyle(new Notification.MetricStyle()
          .addMetric(new Notification.Metric(new Notification.Metric.FixedText("12.3", "MB/s"), "Down"))
          .addMetric(new Notification.Metric(new Notification.Metric.FixedText("0.4", "MB/s"), "Up"))
          .setCriticalMetric(0));
    }
    if (kind.equals("mtimer")) {
      b.setStyle(new Notification.MetricStyle()
          .addMetric(new Notification.Metric(Notification.Metric.TimeDifference.forTimer(
              SystemClock.elapsedRealtime() + 300_000, Notification.Metric.TimeDifference.FORMAT_CHRONOMETER), "Timer"))
          .setCriticalMetric(0));
    }
    Notification n = b.build();
    Log.i(T, "CHIP " + kind + " canPostPromoted=" + nm.canPostPromotedNotifications()
        + " promotable=" + n.hasPromotableCharacteristics() + " areEnabled=" + nm.areNotificationsEnabled());
    nm.notify(id, n);
  }

  void agent() {
    StatusBarManager sbm = getSystemService(StatusBarManager.class);
    try {
      Log.i(T, "AGENT featureSupported=" + sbm.isAgentTaskFeatureSupported() + " canSet=" + sbm.canSetAgentTask()
          + " launchSupported=" + sbm.isAgentTaskLaunchSupported());
    } catch (Throwable t) { Log.w(T, "AGENT query failed " + t); }
    try {
      PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, FsActivity.class), PendingIntent.FLAG_IMMUTABLE);
      AgentTaskUpdate u = new AgentTaskUpdate.Builder().setAgentTaskState(
          new AgentTaskState.Builder(Icon.createWithResource(this, R.drawable.dot), "DiscoBar probe").setClickAction(pi).build()).build();
      sbm.setAgentTask(u, getMainExecutor(), new android.os.OutcomeReceiver<android.agenticon.AgentTaskOutcome, Throwable>() {
        @Override public void onResult(android.agenticon.AgentTaskOutcome o) { Log.i(T, "AGENT set ok stateChanged=" + o.isStateChanged() + " shown=" + o.isEventShown()); }
        @Override public void onError(Throwable t) { Log.w(T, "AGENT set error " + t); }
      });
    } catch (Throwable t) { Log.w(T, "AGENT set threw " + t); }
  }
}
