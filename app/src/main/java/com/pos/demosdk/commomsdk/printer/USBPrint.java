package com.pos.demosdk.commomsdk.printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.common.apiutil.printer.UsbThermalPrinter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.pos.demosdk.R;

import java.util.Hashtable;

public class USBPrint {

    Context mContext;
    UsbThermalPrinter mUsbThermalPrinter;
    private OnTPRINTERSuccessListener monTPRINTERSuccessListener;

    public interface OnTPRINTERSuccessListener {
        void onTPRINTERSuccess(int data);
    }

    public USBPrint(Context context,OnTPRINTERSuccessListener onTPRINTERSuccessListener) {
        this.mContext = context;
        this.monTPRINTERSuccessListener=onTPRINTERSuccessListener;
        mUsbThermalPrinter = new UsbThermalPrinter(mContext);
        new Thread(() -> {
            try {
                mUsbThermalPrinter.start(0);
                String version = mUsbThermalPrinter.getVersion();
                int status = mUsbThermalPrinter.checkStatus();

                if (monTPRINTERSuccessListener != null)
                    monTPRINTERSuccessListener.onTPRINTERSuccess(status);

                Log.d("printer version---",version);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void printContent_58(){
        new Thread(() -> {
            try{
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(4);
                Bitmap barcode = CreateCode("12345678", BarcodeFormat.CODE_128, 320, 176);
                if (barcode != null) mUsbThermalPrinter.printLogo(barcode, true);
                Bitmap qrcode = CreateCode("12345678", BarcodeFormat.QR_CODE, 248, 248);
                if (qrcode != null) mUsbThermalPrinter.printLogo(qrcode, true);
                Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.telpoe);
                if(bitmap != null) mUsbThermalPrinter.printLogo(bitmap,true);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent));
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent1));
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent1));
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent1));
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent1));
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent2));
                mUsbThermalPrinter.enlargeFontSize(1,2);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent2));
                mUsbThermalPrinter.enlargeFontSize(2,1);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent2));
                mUsbThermalPrinter.enlargeFontSize(2,2);
                mUsbThermalPrinter.addString(mContext.getString(R.string.printContent2));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(20);
            }catch (Exception e){
                e.printStackTrace();
            }
        }).start();
    }

    public void printContent_80(){

    }

    public void closePrinter(){
        try {
            if(mUsbThermalPrinter != null) mUsbThermalPrinter.stop();
        }catch (Exception e){
            e.printStackTrace();
        }
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
