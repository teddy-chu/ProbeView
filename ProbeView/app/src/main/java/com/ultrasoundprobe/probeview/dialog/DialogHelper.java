package com.ultrasoundprobe.probeview.dialog;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

public class DialogHelper {
    static private ProgressDialog progressDialog;

    static {
        progressDialog = null;
    }

    static public void showProgressDialog(Context context, String title, String message,
                                          String button,
                                          DialogInterface.OnClickListener listener) {
        progressDialog = new ProgressDialog(context);

        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle(title);
        progressDialog.setMessage(message);

        if (button != null && listener != null) {
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, button, listener);
        }

        progressDialog.show();
    }

    static public void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    static public void showDialog(Context context, String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(dialog -> {
        });
        builder.show();
    }
}
