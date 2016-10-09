package com.kelton.demo.camerademo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    private static final int TAKE_PHOTO_ACTIVITY = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = null;

        // take photo view
        btn = (Button) findViewById(R.id.btn_take_photo);
        btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionTakePhoto();
            }
        });
    }

    private void actionTakePhoto() {
        Intent intent = new Intent();
        intent.setClass(this, TakePhotoActivity.class);
        this.startActivityForResult(intent, TAKE_PHOTO_ACTIVITY);
    }
}
