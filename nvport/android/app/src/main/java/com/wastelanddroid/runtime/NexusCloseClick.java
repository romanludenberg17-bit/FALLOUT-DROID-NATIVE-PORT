package com.wastelanddroid.runtime;

import android.app.Dialog;
import android.view.View;

public class NexusCloseClick implements View.OnClickListener {
    public Dialog dlg;

    public NexusCloseClick(Dialog dialog) {
        this.dlg = dialog;
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        try {
            this.dlg.dismiss();
        } catch (Throwable th) {
        }
    }
}
