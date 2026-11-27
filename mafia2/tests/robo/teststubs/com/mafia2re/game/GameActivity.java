package com.mafia2re.game;

/** Test double of the port's game activity: adds the original overlay the way the real one does. */
public class GameActivity extends org.libsdl.app.SDLActivity {
    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        setContentView(new android.view.View(this));
        addContentView(new TouchControls(this), new android.view.ViewGroup.LayoutParams(-1, -1));
        android.widget.TextView credit = new android.widget.TextView(this);
        credit.setText("Mafia II Android port by VinTeRuS");
        addContentView(credit, new android.widget.FrameLayout.LayoutParams(-2, -2, 49));
    }
}
