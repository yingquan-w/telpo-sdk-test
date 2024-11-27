package com.pos.demosdk.osapi;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.EMV.EMVActivity;
import com.pos.demosdk.MainActivity;
import com.pos.demosdk.R;
import com.pos.demosdk.commomsdk.CommonSDKActivity;

/**
 * Created by Ray.
 * <p>
 * Date: 2024/4/2
 * <p>
 * Description:
 */
public class OSAPIActivity extends BaseActivity {

    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_osapi);

        textView=findViewById(R.id.tv_text);

        String SN=Util.getSN();

        String IMEI=Util.getImei();

        textView.setText("SN:"+SN+"\n"+"IMEI:"+IMEI);

    }


}
