package com.kelton.demo.camerademo;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by kelton on 16/10/11.
 */

public class Utils {

    public static void showTip(Context context, String tip) {
        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show();
    }

}
