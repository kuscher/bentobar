package local.discobar.probe;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.TextView;

/** Hides the status bar to test how DiscoBar notices. */
public class FsActivity extends Activity {
  static FsActivity I;
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    I = this;
    android.util.Log.i("BarProbe", "FsActivity opened from=" + getIntent().getStringExtra("from"));
    TextView t = new TextView(this);
    t.setText("Fullscreen test (DiscoBar probe)");
    setContentView(t);
  }
  @Override public void onWindowFocusChanged(boolean f) {
    if (f && getIntent().getStringExtra("from") == null) getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
  }
  @Override protected void onDestroy() { I = null; super.onDestroy(); }
}
