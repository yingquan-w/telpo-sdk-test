package com.pos.demosdk.commomsdk;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.EMV.EMVActivity;
import com.pos.demosdk.MainActivity;
import com.pos.demosdk.R;
import com.pos.demosdk.commomsdk.buzzer.BuzzerActivity;
import com.pos.demosdk.commomsdk.ic.IccActivityNew;
import com.pos.demosdk.commomsdk.magneticstripecard.MegneticActivity;
import com.pos.demosdk.commomsdk.nfc.NFCActivity;
import com.pos.demosdk.commomsdk.powercontrol.PowerControlActivity;
import com.pos.demosdk.commomsdk.printer.PrintActivity;
import com.pos.demosdk.commomsdk.psam.PsamCardActivity;

public class CommonSDKActivity extends BaseActivity {

    TextView tv_name,tv_version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commonsdk_main);

        tv_name=findViewById(R.id.tv_name);
        tv_name.setText("COMMON SDK");
        tv_version=findViewById(R.id.tv_version);
        tv_version.setText("v20231218");

        findViewById(R.id.printer_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, PrintActivity.class));
            }
        });
        findViewById(R.id.ic_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, IccActivityNew.class));
            }
        });
        findViewById(R.id.psam_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, PsamCardActivity.class));
            }
        });
        findViewById(R.id.mag_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, MegneticActivity.class));
            }
        });
        findViewById(R.id.nfc_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, NFCActivity.class));
            }
        });
        findViewById(R.id.power_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, PowerControlActivity.class));
            }
        });
        findViewById(R.id.buzzer_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CommonSDKActivity.this, BuzzerActivity.class));
            }
        });


    }
}