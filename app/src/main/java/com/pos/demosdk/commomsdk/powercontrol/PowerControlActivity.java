package com.pos.demosdk.commomsdk.powercontrol;

import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.common.apiutil.powercontrol.PowerControl;
import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.R;

import java.util.HashMap;

/**
 * Created by Ray.
 * <p>
 * Date: 2024/3/28
 * <p>
 * Description:
 */
public class PowerControlActivity extends BaseActivity {
    TextView tv_name;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_powercontrol);
        tv_name = findViewById(R.id.tv_name);
        tv_name.setText("PowerContro Service");

        PowerControl powerControl = new PowerControl(this);


        findViewById(R.id.btn1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                powerControl.usbPower(1);//  USB上电  900上面指纹仪
            }
        });

        findViewById(R.id.btn2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                powerControl.usbPower(0);//USB下电  900上面指纹仪
            }
        });
    }


}
