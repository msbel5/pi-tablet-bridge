package com.pitabletbridge;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import java.util.List;

public final class KeyboardDialogController {
    public interface Listener {
        void onCommand(KeyCommand command);
    }

    private final Activity activity;
    private final Listener listener;

    public KeyboardDialogController(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void show() {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_keyboard);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        final EditText editText = (EditText) dialog.findViewById(R.id.keyboardInput);
        final boolean[] suppress = new boolean[]{false};
        final String[] previousText = new String[]{""};

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppress[0]) {
                    return;
                }
                String currentText = editable.toString();
                List<KeyCommand> commands = TextDeltaEncoder.computeCommands(previousText[0], currentText);
                for (KeyCommand command : commands) {
                    listener.onCommand(command);
                }
                previousText[0] = currentText;
                if (currentText.length() > 48) {
                    suppress[0] = true;
                    editText.setText("");
                    previousText[0] = "";
                    editText.setSelection(0);
                    suppress[0] = false;
                }
            }
        });

        bindSpecialKey(dialog, R.id.buttonEnter, KeyCommand.special("enter"));
        bindSpecialKey(dialog, R.id.buttonTab, KeyCommand.special("tab"));
        bindSpecialKey(dialog, R.id.buttonBackspace, KeyCommand.backspace(1));
        bindSpecialKey(dialog, R.id.buttonEsc, KeyCommand.special("esc"));
        bindSpecialKey(dialog, R.id.buttonLeft, KeyCommand.special("left"));
        bindSpecialKey(dialog, R.id.buttonRight, KeyCommand.special("right"));
        bindSpecialKey(dialog, R.id.buttonUp, KeyCommand.special("up"));
        bindSpecialKey(dialog, R.id.buttonDown, KeyCommand.special("down"));

        dialog.show();
        editText.requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void bindSpecialKey(Dialog dialog, int buttonId, final KeyCommand command) {
        Button button = (Button) dialog.findViewById(buttonId);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onCommand(command);
            }
        });
    }
}

