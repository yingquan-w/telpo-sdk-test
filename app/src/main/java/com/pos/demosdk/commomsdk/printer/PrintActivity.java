package com.pos.demosdk.commomsdk.printer;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;



import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.R;

public class PrintActivity extends BaseActivity {

    Button btn_print;
    USBPrint usbPrint;
    TextView tv_name, tv_printerinfo,tv_version;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print);

        init();
    }

    private void init() {


        tv_name = findViewById(R.id.tv_name);
        tv_name.setText("Printer Sample");
        tv_version=findViewById(R.id.tv_version);
        tv_version.setVisibility(View.GONE);
        tv_printerinfo = findViewById(R.id.tv_printerinfo);
        usbPrint = new USBPrint(this, data -> runOnUiThread(() -> {
            switch (data) {
                case 0:
                    tv_printerinfo.setText("Printer status:Normal");
                    break;
                case 16:
                    tv_printerinfo.setText("Printer status:No paper");
                    break;
                default:
                    tv_printerinfo.setText("Printer status:Error");
                    break;

            }
        }));
        btn_print = findViewById(R.id.printtest_btn);
        btn_print.setOnClickListener(view -> {
            print();
        });
    }

    private void print() {
        usbPrint.printContent_58();
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {

        usbPrint.closePrinter();

        super.onDestroy();
    }
}