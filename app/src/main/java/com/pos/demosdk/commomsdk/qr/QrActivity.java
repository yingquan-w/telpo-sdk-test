package com.pos.demosdk.commomsdk.qr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.R;

import java.util.Hashtable;

/**
 * Created by Ray.
 * <p>
 * Date: 2024/1/4
 * <p>
 * Description:
 */
public class QrActivity extends BaseActivity {
    TextView tv_name;
    String info;
    EditText et_qr;

    ImageView img_qr;

    Bitmap qrBitmap;


    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_service);
        tv_name = findViewById(R.id.tv_name);
        tv_name.setText("QR Code");

        et_qr=findViewById(R.id.et_qr);
        img_qr=findViewById(R.id.img_qr);

        info=et_qr.getText().toString();

        try {
            qrBitmap=CreateCode(info,BarcodeFormat.QR_CODE,256,256);
            img_qr.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }

        findViewById(R.id.btn_qr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                info=et_qr.getText().toString();
                try {
                    qrBitmap=CreateCode(info,BarcodeFormat.QR_CODE,256,256);
                    img_qr.setImageBitmap(qrBitmap);
                } catch (WriterException e) {
                    throw new RuntimeException(e);
                }

            }
        });


    }

    public Bitmap CreateCode(String str, BarcodeFormat type, int bmpWidth, int bmpHeight)
            throws WriterException {
        Hashtable<EncodeHintType, String> mHashtable = new Hashtable<EncodeHintType, String>();
        mHashtable.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(str, type, bmpWidth, bmpHeight, mHashtable);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    pixels[y * width + x] = 0xff000000;
                } else {
                    pixels[y * width + x] = 0xffffffff;
                }
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
