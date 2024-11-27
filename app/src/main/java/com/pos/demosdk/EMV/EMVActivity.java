package com.pos.demosdk.EMV;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.common.sdk.emv.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pos.demosdk.BaseActivity;
import com.pos.demosdk.EMV.ActivityMasterPaypass;
import com.pos.demosdk.EMV.ActivityVisaPayware;
import com.pos.demosdk.MainActivity;
import com.pos.demosdk.R;
import com.pos.demosdk.databinding.DetectDialogBinding;
import com.telpo.emv.EmvApp;
import com.telpo.emv.EmvCAPK;
import com.telpo.emv.EmvCountryCode;
import com.telpo.emv.EmvCurrencyCode;
import com.telpo.pinpad.PinParam;
import com.telpo.emv.EmvAmountData;
import com.telpo.emv.EmvCandidateApp;
import com.telpo.emv.EmvOnlineData;
import com.telpo.emv.EmvParam;
import com.telpo.emv.EmvPinData;
import com.telpo.emv.EmvService;
import com.telpo.emv.EmvServiceListener;
import com.telpo.emv.EmvTLV;
import com.telpo.util.DefaultAPPCAPK;
import com.telpo.util.ErrMsg;
import com.telpo.util.StringUtil;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class EMVActivity extends BaseActivity {

    //log tag
    private String TAG = "EMVActivity";
    //The last result
    public int _LastCode = 0;
    //Payment amount
    public double amount = 0.00;
    //Display message buffer
    public StringBuffer DisplayBuf = new StringBuffer("");
    //context
    public Context context;
    //Pinpad service
    public com.common.sdk.emv.PinpadService pinpadService;
    // Emv service
    public EmvService emvService;

    public DetectDialogBinding detectDialogBinding;
    public EditText et_MsgView;
    public MaterialButton bt_confirm;


    //Process dialog
    public AlertDialog processDialog;
    //Show pan message dialog
    public AlertDialog panDialog;
    //Select app result
    public int selectAPPResult = 0;
    //whether the UI thread is running
    public boolean UIThreadisRunning = true;


    //PinKey index
    public int PIN_KEY_INDEX = 1;
    //PanKey index
    public int PAN_KEY_INDEX = 2;
    //macKey index
    public int MAC_KEY_INDEX = 3;
    //Dukpt's current KSN
    public String currentKSN = "";
    //PAN Dukpt's current KSN
    public String PanCurrentKSN = "";
    //Is the device initialized succ
    public boolean isDevInit = false;
    //MK/SK mode or not(false:DUKPT mode)
    public boolean isMkMode = true;
    //DES mode or not(fales:AES mode)
    public boolean isDesMode = true;
    //Is pan encryption in Des mode
    public boolean isPanDesMode = true;
    //is pan encryption in mk/sk mode
    public  boolean isPanMkMode = true;
    //PIN Block
    public String pinBlock = "";
    //PIN format（Des：0、1、3；Aes：4）
    //PAN
    public String cardNum = "";

    //Is online transaction or not
    public boolean isOnlineTransaction = false;
    //The signal to stop detect
    public boolean stopDetect = false;
    //Is NFC card
    public boolean isNFC = false;
    //Gson

    //Track1 data
    public String Track1 = null;
    //Track2 data
    public String Track2 = null;

    //Is Mag card
    public boolean isMag = false;
    //If no err ,go to pay
    public boolean isNoErr = true;
    //locker
    public ReentrantLock ThreadLock = new ReentrantLock();
    //terminal mode

    //
    public ActivityVisaPayware activityVisaPayware;
    public ActivityPBOC activityPBOC;
    public ActivityMasterPaypass activityMasterPaypass;
    public ActivityRupay activityRupay;
    public ActivityAmex activityAmex;
    public ActivityMir activityMir;
    public ActivityTransit activityTransit;

    EmvServiceListener MyListener = new EmvServiceListener() {
        @Override
        public int onInputAmount(EmvAmountData emvAmountData) {
            emvAmountData.Amount = (long) (amount * 100);
            emvAmountData.TransCurrCode = 156;
            emvAmountData.ReferCurrCode = 156;// EmvCurrencyCode
            emvAmountData.TransCurrExp = 2;
            emvAmountData.ReferCurrExp = 2;
            emvAmountData.ReferCurrCon = 1;
            emvAmountData.CashbackAmount = 0;
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onInputPin(EmvPinData emvPinData) {

            changePanUIVisibility(false, null);
            if(emvPinData.IsRetry==1){
                changeDialogText("Wrong Pin.Please input PIN");
            }else{
                changeDialogText("Please input PIN");
            }


            int result =0;
            String hidePan = cardNum.substring(0, 6) + "******" + cardNum.substring(cardNum.length() - 4, cardNum.length());
            changePanUIVisibility(true, "PAN:"+hidePan);

            if (emvPinData.type == EmvService.ONLIEN_ENCIPHER_PIN) {
                //online PIN
                if (isMkMode) {
                    //MK/SK mode
                    pinBlock = getMkPin(cardNum);
                } else {
                    //Dukpt mode
                    pinBlock = getDukptPin(cardNum);
                }
            } else {
                //offline PIN（Emv lib calls pinpad,application does not handle that）
                Log.i(TAG, "offline PIN!");
                changeDialogText("EMV Processing...");
                return EmvService.EMV_TRUE;
            }

            if (_LastCode == PinpadService.PIN_OK) {
                emvPinData.Pin = StringUtil.hexStringToByte(pinBlock);
                result = EmvService.EMV_TRUE;
            }
            else if (_LastCode == PinpadService.PIN_ERROR_PINLEN){ //bypass
                result = EmvService.ERR_NOPIN;
            }
            else if (_LastCode == PinpadService.PIN_ERROR_CANCEL){
                result = EmvService.ERR_USERCANCEL;
            }
            else if (_LastCode == PinpadService.PIN_ERROR_NOKEY){
                result = EmvService.ERR_KEY_NOT_FOUND;
            }
            else if(_LastCode == PinpadService.PIN_ERROR_TIMEOUT){
                result = EmvService.ERR_TIMEOUT;
            }else{
                AppendDis("Get PIN Error:"+ErrMsg.GetPinPadErrMsg(result));
                isNoErr =false;
                result = EmvService.EMV_FALSE;
            }
            changeDialogText("EMV Processing...");
            return result;
        }

        @Override
        public int onSelectApp(EmvCandidateApp[] emvCandidateApps) {
            final EmvCandidateApp[] mAppList = emvCandidateApps;
            int appListLen = emvCandidateApps.length;
            selectAPPResult = 0;
            UIThreadisRunning = true;

            final String[] items = new String[appListLen];
            for (int i = 0; i < appListLen; i++) {
                items[i] = emvCandidateApps[i].appName;
            }

            new Handler(context.getMainLooper()).post(new Runnable() {/*Using UI thread resources to create dialog box*/
                @Override
                public void run() {
                    //The first UI application is selected by default
                    selectAPPResult = mAppList[0].index;
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context)
                            .setTitle("Please Select App")
                            .setCancelable(false)
                            .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                                //Set the radio list item, and select the first item by default (index is 0)
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    AppendDis("callback [onSelectApp] You Select \"" + items[which] + "\"");
                                    AppendDis("callback [onSelectApp] It's appIndex is \"" + mAppList[which].index + "\"");
                                    selectAPPResult = mAppList[which].index;
                                }
                            })

                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    UIThreadisRunning = false;
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    UIThreadisRunning = false;
                                    selectAPPResult = EmvService.ERR_USERCANCEL;
                                }
                            });

                    builder.create().show();

                }
            });

            while (UIThreadisRunning) {
                //Waiting for user confirmation
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return selectAPPResult;
        }

        @Override
        public int onSelectAppFail(int i) {
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onFinishReadAppData() {
            int ret = 0;
            EmvTLV tlv = new EmvTLV(0x9F06);
            emvService.Emv_GetTLV(tlv);
            AppendDis("AID:"+ com.telpo.util.StringUtil.bytesToHexString(tlv.Value));

            tlv = new EmvTLV(0x9F1A);
            emvService.Emv_GetTLV(tlv);
            AppendDis("CountryCode:"+ com.telpo.util.StringUtil.bytesToHexString(tlv.Value));

            tlv = new EmvTLV(0x5A);
            ret = emvService.Emv_GetTLV(tlv);
            if(EmvService.EMV_TRUE == ret){
                cardNum = StringUtil.bytesToHexString(tlv.Value).replace("F", "");
                AppendDis("0x5A:"+ com.telpo.util.StringUtil.bytesToHexString(tlv.Value));
            }else{
                tlv = new EmvTLV(0x57);
                ret = emvService.Emv_GetTLV(tlv);
                if(EmvService.EMV_TRUE == ret) {
                    String str_57 = StringUtil.bytesToHexString(tlv.Value);
                    cardNum = str_57.substring(0, str_57.indexOf('D'));
                    AppendDis("0x57:" + com.telpo.util.StringUtil.bytesToHexString(tlv.Value));
                }else {
                    AppendDis("Get cardNum Fail");
                }
            }
            if(!(null == cardNum || cardNum.isEmpty())){
                AppendDis("cardNum:"+cardNum);
                AppendDis("encryptPan:" + encryptPan(cardNum));
            }

            List<EmvTLV> tagList = EMVUtils.getTLVCardDataTags();
            for (EmvTLV emvTLV : tagList) {
                ret = emvService.Emv_GetTLV(emvTLV);
                // AppendDis("TLV"+Integer.toHexString(emvTLV.Tag).toUpperCase()+ ":"+StringUtil.bytesToHexString(emvTLV.Value));
                if (EmvService.EMV_TRUE == ret) {
                    AppendDis("Tag" + Integer.toHexString(emvTLV.Tag).toUpperCase() + ":" + StringUtil.bytesToHexString(emvTLV.Value));
                }else {
                    AppendDis("Tag" + Integer.toHexString(emvTLV.Tag).toUpperCase() + ":N/G" );
                }
                //showMessage(String.format("TLV%s : %s", Integer.toHexString(emvTLV.Tag).toUpperCase(), StringUtil.bytesToHexString(emvTLV.Value)));
                //   Log.e(TAG, String.format("Getting TLV: %s : %s Result %d", Integer.toHexString(emvTLV.Tag), StringUtils.bytesToHex(emvTLV.Value), ret));

            }
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onVerifyCert() {
            changePanUIVisibility(false, null);
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onOnlineProcess(EmvOnlineData emvOnlineData) {

            isOnlineTransaction = true;
            changeDialogText("Online processing...");

            changePanUIVisibility(false, null);
            if (pay()) {
                emvOnlineData.ResponeCode = "00".getBytes();
//                emvOnlineData.ScriptData71 =
//                emvOnlineData.IssuAuthenData =
//                emvOnlineData.ScriptData72 =
//                emvOnlineData.ResponeCode =
                return EmvService.ONLINE_APPROVE;
            } else {
                return EmvService.ONLINE_FAILED;
            }
        }

        @Override
        public int onRequireTagValue(int i, int i1, byte[] bytes) {
            return 0;
        }

        @Override
        public int onRequireDatetime(byte[] bytes) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            Date curDate = new Date(System.currentTimeMillis());//Get the current time
            String str = formatter.format(curDate);
            byte[] time = new byte[0];
            try {
                time = str.getBytes("ascii");
                System.arraycopy(time, 0, bytes, 0, bytes.length);
                return EmvService.EMV_TRUE;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.e("MyEmvService", "onRequireDatetime failed");
                return EmvService.EMV_FALSE;
            }
        }

        @Override
        public int onReferProc() {
            changePanUIVisibility(false, null);
            return EmvService.EMV_TRUE;
        }

        @Override
        public int OnCheckException(String s) {
            return EmvService.EMV_FALSE;
        }

        @Override
        public int OnCheckException_qvsdc(int i, String s) {
            return EmvService.EMV_FALSE;
        }
    };

    TextView tv_name,tv_version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emv);

        tv_name=findViewById(R.id.tv_name);
        tv_name.setText("EMV SDK");
        tv_version=findViewById(R.id.tv_version);
        tv_version.setText("v20240106");
        //Set the context value;
        context = this;
        //Init View
        initView();
        //Init Dev
        initDev();
        //new all the other NFC card Class
        InitTheClasses();
    }

    @Override
    protected void onResume() {
        if (!isDevInit) {
            initDev();
        }

        super.onResume();
    }

    @Override
    protected void onStop() {
        if (isDevInit) {
            emvService.deviceClose();


            pinpadService.Pinpad_Close();


            isDevInit = false;
        }
        super.onStop();
    }

    private void confirm()
    {
        if (!isDevInit) {
            //emv is not ok
            AppendDis("EMV not Init,Please Reopen the App");
            return;
        }

        amount = 8.00;
        detectDialogBinding = DetectDialogBinding.inflate(getLayoutInflater());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        processDialog = builder.setTitle("Amount：¥"+amount)
                .setView(detectDialogBinding.getRoot())
                .setCancelable(false)
                .setOnCancelListener(new DialogInterface.OnCancelListener(){
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        cancelDetect();
                    }
                })
                .show();
        detectDialogBinding.tvCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processDialog.cancel();
            }
        });

        startDetect();
    }

    /**
     * start card detection
     */
    private void startDetect() {
        //new a thread
        MyThread myThread = new MyThread();
        Thread thread = new Thread(myThread);
        thread.start();
    }


    public class MyThread implements Runnable {

        @Override
        public void run() {
            //Lock
            ThreadLock.lock();
            //reset the param
            resetData();

            try {
               // EmvService.MagStripeCloseReader();
                EmvService.NfcCloseReader();
                EmvService.IccCloseReader();

            //    _LastCode = EmvService.MagStripeOpenReader();
            //    if(EmvService.EMV_DEVICE_TRUE == _LastCode)
           //     {
                    _LastCode = EmvService.NfcOpenReader(200);
                    if(EmvService.EMV_DEVICE_TRUE == _LastCode)
                    {
                        _LastCode = EmvService.IccOpenReader();
                        if(EmvService.EMV_DEVICE_TRUE == _LastCode)
                        {
                            AppendDis("Start Detect Card.");
                            while (!stopDetect) {
                                if (EmvService.NfcCheckCard(300) == EmvService.EMV_DEVICE_TRUE) {
                                    stopDetect = true;
                                    changeDialogText("start transaction...");
                                    AppendDis("Find a nfc card start transaction...");
                                    startNFCTransaction();
                                }

                                if (EmvService.IccCheckCard(300) == EmvService.EMV_DEVICE_TRUE) {
                                    stopDetect = true;
                                    EmvService.IccCard_Poweron();
                                    emvService.setListener(MyListener);
                                    changeDialogText("start transaction...");
                                    AppendDis("Find a ic card start transaction...");
                                    startIcTransaction();
                                    EmvService.IccCard_Poweroff();
                                }

                            /*    if (EmvService.MagStripeCheckCard(1000) == EmvService.EMV_DEVICE_TRUE) {
                                    stopDetect = true;
                                    changeDialogText("start transaction...");
                                    AppendDis("Find a MagStripe card start transaction...");
                                    startMagTransaction();
                                }*/
                            }
                            EmvService.IccCloseReader();
                        }
                        else{
                            AppendDis("Icc Reader open fail:$_LastCode");
                        }
                        EmvService.NfcCloseReader();
                    }
                    else{
                        AppendDis("Nfc Reader open fail:$_LastCode");
                    }
 //                   EmvService.MagStripeCloseReader();
//                }
//                else {
//                    AppendDis("Mag Reader open fail:$_LastCode");
//                }
                if (processDialog.isShowing()) {
                    processDialog.dismiss();
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                //unlocck
                ThreadLock.unlock();
                stopDetect = true;
            }
        }
    };

    /**
     * start NFC transaction
     */
    private void startNFCTransaction() {
        //set the NFC flag
        isNFC = true;

        //check the type of card
        switch (emvService.NFC_CheckKernelID()) {
            case EmvService.NFC_KERNEL_DEFAUT_CARD_VISA:
                AppendDis("CardType:VISA");
                /*Because payware and transit cards are both VISA contactless cards,
                 only be distinguished by application scenarios*/
                activityVisaPayware.StartTransaction(amount);
                //activityTransit.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_MASTER:
                AppendDis("CardType:MASTER");
                activityMasterPaypass.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_UNIONPAY:
                AppendDis("CardType:UNIONPAY");
                activityPBOC.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_RUPAY:
                AppendDis("CardType:RUPAY");
                activityRupay.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_AMEX:
                AppendDis("CardType:AMEX");
                activityAmex.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_MIR:
                AppendDis("CardType:MIR");
                activityMir.StartTransaction(amount);
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_JCB:
                AppendDis("CardType:JCB");
                AppendDis("This card is not supported at present");
                break;
            case EmvService.NFC_KERNEL_DEFAUT_CARD_UNKNOWN:
                AppendDis("CardType:UNKNOWN");
                AppendDis("This card is not supported at present");
                break;
            default:
                AppendDis("This card is not supported at present");
                break;
        }

        changePanUIVisibility(false, null);
        if (processDialog.isShowing()) {
            processDialog.dismiss();
        }
    }

    /**
     * start IC transaction
     */
    private void startIcTransaction() {
        _LastCode = emvService.Emv_TransInit();
        if (EmvService.EMV_TRUE != _LastCode) {
            AppendDis("Emv_TransInit fail:" + _LastCode);
            return;
        }
        AppendDis("Emv_TransInit succ.");

        EmvParam param = new EmvParam();
        param.CountryCode=new byte[]{(byte) 0x01, (byte) 0x56};//156  EmvCountryCode


        param.Capability = new byte[]{(byte) 0xE0, (byte) 0xF9, (byte) 0xC8};

        //param.NFC_OffLineFloorLimit=1222333;
        //param.set_NFC_OffLineFloorLimit((long)999999999999.00);
        _LastCode = emvService.Emv_SetParam(param);
        if (EmvService.EMV_TRUE != _LastCode) {
            AppendDis("Emv_SetParam fail,err code:" + _LastCode);
            return;
        }
        AppendDis("Emv_SetParam succ");
        // emvService.Emv_SetOfflinePinCBenable()
        _LastCode = emvService.Emv_StartApp(0);//1  online

        EmvTLV tlv = new EmvTLV(0x9F27);//second cryptogram
        emvService.Emv_GetTLV(tlv);
        AppendDis("Tag9F27:"+ com.telpo.util.StringUtil.bytesToHexString(tlv.Value));

        if (EmvService.EMV_TRUE == _LastCode) {
            AppendDis("Transaction Success");
        } else {
            AppendDis("EMV Fail:" + ErrMsg.GetEmvErrMsg(_LastCode));
            isNoErr = false;
        }

        changePanUIVisibility(false, null);
        if (!isOnlineTransaction) {
            //upload offline transaction data
            pay();
        }

        if (processDialog.isShowing()) {
            processDialog.dismiss();
        }
    }

    /**
     * start mag transaction
     */
    private void startMagTransaction() {

        try {
            isMag = true;
            Track1 = EmvService.MagStripeReadStripeData(1);
            Track2 = EmvService.MagStripeReadStripeData(2);
            String Track3 = EmvService.MagStripeReadStripeData(3);
            AppendDis("Get mag data->\n" +
                    "Track1:" + Track1 +
                    "\nTrack2:" + Track2 +
                    "\nTrack3:" + Track3);

            if (!(null == Track1 || Track1.isEmpty())) {
                AppendDis("encryptTrack1:" + encryptPan(Track1));
            }

            if (!(null == Track2 || Track2.isEmpty()))
            {
                int index = Track2.indexOf("=");
                if (index < 1 || index > 21) {
                    index = Track2.indexOf("D");
                    if (index < 1 || index > 21) {
                        AppendDis("Get Mag Data Fail");
                        if (processDialog.isShowing()) {
                            processDialog.dismiss();
                        }
                        isNoErr = false;
                        return;
                    }
                }
                cardNum = Track2.substring(0, index);
                AppendDis("cardNum->" + cardNum);
                AppendDis("cardNum:" + encryptPan(cardNum));
                AppendDis("encryptTrack2:" + encryptPan(Track2));

                String serviceNum = Track2.substring(index + 5, index + 8);
                changeDialogText("Please input PIN");
                String hidePan = cardNum.substring(0, 6) + "******" + cardNum.substring(cardNum.length() - 4, cardNum.length());
                changePanUIVisibility(true, "PAN:"+ hidePan + "\nServiceNum:" + serviceNum);
                int result;
                if (isMkMode) {
                    //MK/SK mode
                    pinBlock = getMkPin(cardNum);
                } else {
                    //Dukpt mode
                    pinBlock = getDukptPin(cardNum);
                }
                if("" == pinBlock) {
                    changePanUIVisibility(false, null);
                    isNoErr = false;
                    return;
                }
                changePanUIVisibility(false, null);
                changeDialogText("Online processing...");

                if (pay()) {
                    AppendDis("Transaction Success");
                } else {
                    AppendDis("Pay Fail");
                }
            }
            else {
                AppendDis("Get mag data fail! the Track 2 information is required");
            }
        }catch (Exception e){
            AppendDis("err=" + e.getMessage());
        };
    }

    /**
     * cancel card detection
     */
    private void cancelDetect() {
        stopDetect = true;
    }

    /**
     * reset the data
     */
    private void resetData() {
        currentKSN = "";
        pinBlock = "";
        cardNum = "";
        isNFC = false;
        stopDetect = false;
        Track1 = "";
        Track2 = "";
        isMag = false;
        isNoErr = true;
    }

    /**
     * change the message in the process dialog
     * @param msg String
     */
    public void changeDialogText(String msg) {
        EMVActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detectDialogBinding.tvCancel.setVisibility(View.GONE);
                detectDialogBinding.tvMessage.setText(msg);
            }
        });
    }

    public void changePanUIVisibility(Boolean isShow, String text) {
        EMVActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isShow) {
                    panDialog.setMessage(text);
                    panDialog.getWindow().setGravity(Gravity.TOP);
                    panDialog.show();
                } else {
                    if (panDialog.isShowing()) {
                        panDialog.cancel();
                    }
                }
            }
        });
    }

    /**
     * get PIN with MK/SK
     * @param
     * @return String result PinBlockStr
     */
    public String getMkPin(String cardNum) {
        String PinBlockStr = "";

        PinpadBytesOut pinBlock = new PinpadBytesOut();

        _LastCode = pinpadService.Pinpad_GetPin(PIN_KEY_INDEX, cardNum,  PinpadEnum.ENUM_PIN_BLOCK_FORMAT.ISO_9564_FORMAT_0, 6, 0, 60, pinBlock);
        if (_LastCode != PinpadService.PIN_OK) {
            AppendDis("encipher pin error:"+_LastCode);
            isNoErr =false;
        } else {
            PinBlockStr = StringUtil.bytesToHexString(pinBlock.outResult);
            AppendDis("Pinblock:" + PinBlockStr);
        }

        return PinBlockStr;
    }

    /**
     * get PIN with DUKPT
     * @param
     * @return String result PinBlockStr
     */
    public String getDukptPin(String cardNum) {
        String PinBlockStr = "";


        PinpadBytesOut ksn = new PinpadBytesOut();
        if (isDesMode) {
            //start session
            _LastCode = pinpadService.Pinpad_DEA_DUKPT_Session_Start(1, ksn);
            if (PinpadService.PIN_OK == _LastCode) {
                currentKSN = StringUtil.bytesToHexString(ksn.outResult);
                Log.i(TAG, "KSN:" + currentKSN);
                //DES mode
                PinpadBytesOut pinBlock = new PinpadBytesOut();
                _LastCode = pinpadService.Pinpad_DEA_DUKPT_GetPin(cardNum,  PinpadEnum.ENUM_PIN_BLOCK_FORMAT.ISO_9564_FORMAT_0, 6, 0, 60, pinBlock);
                if(PinpadService.PIN_OK == _LastCode) {
                    PinBlockStr = StringUtil.bytesToHexString(pinBlock.outResult);
                }
                else {
                    AppendDis("Pinpad_DEA_DUKPT_GetPin Err:" + ErrMsg.GetPinPadErrMsg(_LastCode));
                    isNoErr =false;
                }
            }
            else{
                AppendDis("Sesssion Start Err:" + ErrMsg.GetPinPadErrMsg(_LastCode));
                isNoErr =false;
            }
            pinpadService.Pinpad_DEA_DUKPT_Session_End();
        } else {
            //start session
            _LastCode = pinpadService.Pinpad_AES_DUKPT_Session_Start(1, ksn);
            if (PinpadService.PIN_OK == _LastCode) {
                currentKSN = StringUtil.bytesToHexString(ksn.outResult);
                Log.i(TAG, "KSN:" + currentKSN);
                //AES mode
                PinpadBytesOut pinBlock = new PinpadBytesOut();
                _LastCode = pinpadService.Pinpad_AES_DUKPT_GetPin(PinpadEnum.ENUM_AES_DUKPT_KeyUsage._PINEncryption, cardNum, 6, 0, 60, pinBlock);
                if(PinpadService.PIN_OK == _LastCode) {
                    PinBlockStr = StringUtil.bytesToHexString(pinBlock.outResult);
                }
                else {
                    AppendDis("Pinpad_DEA_DUKPT_GetPin Err:" + ErrMsg.GetPinPadErrMsg(_LastCode));
                }
            }
            else{
                AppendDis("Sesssion Start Err:" + ErrMsg.GetPinPadErrMsg(_LastCode));
            }
            pinpadService.Pinpad_AES_DUKPT_Session_End();
        }


        AppendDis("Pinblock:" + PinBlockStr);
        return PinBlockStr;
    }

    /**
     * padding the PAN or mag card's str
     * @param data String? raw data
     * @return String PaddingText
     */
    public String PanPadding(String data) {
        int dataLen = data.length();
        int remain = 16 - dataLen % 16;
        byte[] res = new byte[dataLen + remain];
        int i = 0;
        while (i < dataLen + remain) {
            if (i < dataLen)
                res[i] = (byte)data.charAt(i);
            else
                res[i] = (byte) remain;
            i++;
        }
        String PaddingText = StringUtil.bytesToHexString(res);
        return PaddingText;
    }

    /**
     * use PanKey to encrypt data(PAN or mag card's str)
     * @param data String? encrypt data
     * @return String cipherText
     */
    public String encryptPan(String data) {

        //Open task with index 1
        String cipherText = "";
        String DataAfterPadding = PanPadding(data.toString());
        int result = 0;
        if (isPanMkMode) {
            //MK/SK mode
            if (isPanDesMode) {
                //DES
                PinpadBytesOut output = new PinpadBytesOut();
                byte[] iv = new byte[]{0,0,0,0,0,0,0,0};
                result = pinpadService.Pinpad_Calculate_Normal_DES(
                        PAN_KEY_INDEX,
                        StringUtil.hexStringToByte(DataAfterPadding),
                        output,
                        iv,
                        PinpadEnum.ENUM_ENC_MODE.PIN_ENC_ENCRYPT,
                        PinpadEnum.ENUM_ECB_MODE.PIN_ECB_CBC);
                if(PinpadService.PIN_OK == result){
                    cipherText = StringUtil.bytesToHexString(output.outResult);
                }else {
                    AppendDis("Pinpad_Calculate_Normal_DES Err:" + ErrMsg.GetPinPadErrMsg(result));
                }
            }
            else {
                //AES
                PinpadBytesOut output = new PinpadBytesOut();
                byte[] iv = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
                result = pinpadService.Pinpad_Calculate_Normal_AES(
                        PAN_KEY_INDEX,
                        StringUtil.hexStringToByte(DataAfterPadding),
                        output,
                        iv,
                        PinpadEnum.ENUM_ENC_MODE.PIN_ENC_ENCRYPT,
                        PinpadEnum.ENUM_ECB_MODE.PIN_ECB_CBC);
                if(PinpadService.PIN_OK == result) {
                    cipherText = StringUtil.bytesToHexString(output.outResult);
                }
                else {
                    AppendDis("Pinpad_Calculate_Normal_AES Err:" + ErrMsg.GetPinPadErrMsg(result));
                }
            }
        }
        else{
            //DUKPT mode
            PinpadBytesOut Panksn = new PinpadBytesOut();
            int StartResult = 0;

            if (isPanDesMode) {
                //start session
                StartResult = pinpadService.Pinpad_DEA_DUKPT_Session_Start(1, Panksn);
                if (PinpadService.PIN_OK == StartResult){
                    //DES
                    PinpadBytesOut output = new PinpadBytesOut();
                    byte[] iv = new byte[]{0,0,0,0,0,0,0,0};
                    result = pinpadService.Pinpad_DEA_DUKPT_Calculate_Des(
                            StringUtil.hexStringToByte(DataAfterPadding),
                            output,
                            iv,
                            PinpadEnum.ENUM_ENC_MODE.PIN_ENC_ENCRYPT,
                            PinpadEnum.ENUM_ECB_MODE.PIN_ECB_CBC);

                    if(PinpadService.PIN_OK == result) {
                        cipherText = StringUtil.bytesToHexString(output.outResult);
                    }
                    else {
                        AppendDis("Pinpad_DEA_DUKPT_Calculate_Des Err:" + ErrMsg.GetPinPadErrMsg(result));
                    }
                    pinpadService.Pinpad_DEA_DUKPT_Session_End();
                }
                else{
                    AppendDis("Session Start Err:" + ErrMsg.GetPinPadErrMsg(StartResult));
                }
            } else {
                //start session
                StartResult = pinpadService.Pinpad_AES_DUKPT_Session_Start(1, Panksn);
                if(PinpadService.PIN_OK == StartResult){
                    //AES
                    PinpadBytesOut output = new PinpadBytesOut();
                    byte[] iv = new byte[]{0,0,0,0,0,0,0,0};
                    result= pinpadService.Pinpad_AES_DUKPT_Calculate_Des(
                            PinpadEnum.ENUM_AES_DUKPT_KeyUsage._DataEncryptionEncrypt,
                            StringUtil.hexStringToByte(DataAfterPadding),
                            output,
                            iv,
                            PinpadEnum.ENUM_ENC_MODE.PIN_ENC_ENCRYPT,
                            PinpadEnum.ENUM_ECB_MODE.PIN_ECB_CBC);
                    if(PinpadService.PIN_OK == result) {
                        cipherText = StringUtil.bytesToHexString(output.outResult);
                    }
                    else {
                        AppendDis("Pinpad_AES_DUKPT_Calculate_Des Err:" + ErrMsg.GetPinPadErrMsg(result));
                    }
                    pinpadService.Pinpad_AES_DUKPT_Session_End();
                }
                else{
                    AppendDis("Session Start Err:" + ErrMsg.GetPinPadErrMsg(StartResult));
                }
            }
        }
        return cipherText;
    }

    /**
     * init EMV Lib
     */
    private void initDev() {
        //Init the EmvService
        emvService = EmvService.getInstance();
        AppendDis("Debug On:" + emvService.Emv_SetDebugOn(1));
   //     emvService.setListener(MyListener);

        if(EmvService.EMV_TRUE != (_LastCode = emvService.Open(context))) {
            AppendDis("EmvService.Open Fail:"+_LastCode);
            return;
        }
        AppendDis("EmvService.Open succ");

        if(EmvService.EMV_DEVICE_TRUE != (_LastCode = emvService.deviceOpen())) {
            AppendDis("EmvService.deviceOpen Fail:"+_LastCode);
            return;
        }
        AppendDis("EmvService.deviceOpen succ");

        AppendDis("EMV init ok!");
        emvService.Emv_RemoveAllApp();
        emvService.Emv_RemoveAllCapk();
        DefaultAPPCAPK.Add_All_APP(emvService);
        addAID(emvService,"A0000000043060");
        addAID(emvService,"D86200010810B0");
        addAID(emvService,"A0000000032010");
        addAmexAID(emvService);
        DefaultAPPCAPK.Add_All_CAPK(emvService);

        addAID(emvService,"A000000333010101");
        AppendDis("Add Apps and Capks ok!");

        //Init the PinpadService
        _LastCode = InitPinPad();
        if(PinpadService.PIN_OK != _LastCode) {
            AppendDis("InitPinPad Fail:"+_LastCode);
            return;
        }
        //write key
        _LastCode = WriteKey();
        if(PinpadService.PIN_OK != _LastCode) {
            AppendDis("WriteKey Fail:"+_LastCode);
            return;
        }

        isDevInit = true;
        AppendDis("Dev init succ!!!");
    }

    void Add_Amex_CAPK_Public(){
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x01	,
                StringUtil.hexStringToByte("AFAD7010F884E2824650F764D47D7951A16EED6DBB881F384DEDB6702E0FB55C0FBEF945A2017705E5286FA249A591E194BDCD74B21720B44CE986F144237A25F95789F38B47EA957F9ADB2372F6D5D41340A147EAC2AF324E8358AE1120EF3F"),
                new byte[]{0x03},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("ACFE734CF09A84C7BF025F0FFC6FA8CA25A22869"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x02	,
                StringUtil.hexStringToByte("AF4B8D230FDFCB1538E975795A1DB40C396A5359FAA31AE095CB522A5C82E7FFFB252860EC2833EC3D4A665F133DD934EE1148D81E2B7E03F92995DDF7EB7C90A75AB98E69C92EC91A533B21E1C4918B43AFED5780DE13A32BBD37EBC384FA3DD1A453E327C56024DACAEA74AA052C4D"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("33F5B0344943048237EC89B275A95569718AEE20"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x03,
                StringUtil.hexStringToByte("B0C2C6E2A6386933CD17C239496BF48C57E389164F2A96BFF13ac3439AE8A77B20498BD4DC6959AB0C2D05D0723AF3668901937B674E5A2FA92DDD5E78EA9D75D79620173CC269B35F463B3D4AAFF2794F92E6C7A3FB95325D8AB95960C3066BE548087BCB6CE12688144A8B4A66228AE4659C634C99E36011584C095082A3A3E3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("8708A3E3BBC1BB0BE73EBD8D19D4E5D20166BF6C"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x04	,
                StringUtil.hexStringToByte("D0F543F03F2517133EF2BA4A1104486758630DCFE3A883C77B4E4844E39A9BD6360D23E6644E1E071F196DDF2E4A68B4A3D93D14268D7240F6A14F0D714C17827D279D192E88931AF7300727AE9DA80A3F0E366AEBA61778171737989E1EE309"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("FDD7139EC7E0C33167FD61AD3CADBD68D66E91C5"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x10	,
                StringUtil.hexStringToByte("CF98DFEDB3D3727965EE7797723355E0751C81D2D3DF4D18EBAB9FB9D49F38C8C4A826B99DC9DEA3F01043D4BF22AC3550E2962A59639B1332156422F788B9C16D40135EFD1BA94147750575E636B6EBC618734C91C1D1BF3EDC2A46A43901668E0FFC136774080E888044F6A1E65DC9AAA8928DACBEB0DB55EA3514686C6A732CEF55EE27CF877F110652694A0E3484C855D882AE191674E25C296205BBB599455176FDD7BBC549F27BA5FE35336F7E29E68D783973199436633C67EE5A680F05160ED12D1665EC83D1997F10FD05BBDBF9433E8F797AEE3E9F02A34228ACE927ABE62B8B9281AD08D3DF5C7379685045D7BA5FCDE58637"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("C729CF2FD262394ABC4CC173506502446AA9B9FD"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x52	,
                StringUtil.hexStringToByte("B831414E0B4613922BD35B4B36802BC1E1E81C95A27C958F5382003DF646154CA92FC1CE02C3BE047A45E9B02A9089B4B90278237C965192A0FCC86BB49BC82AE6FDC2DE709006B86C7676EFDF597626FAD633A4F7DC48C445D37EB55FCB3B1ABB95BAAA826D5390E15FD14ED403FA2D0CB841C650609524EC555E3BC56CA957"),
                new byte[]{0x01,0x00,0x01},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("9D93DA5E86FDF16318D268CA6AC57031EDFCA3CB"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x60	,
                StringUtil.hexStringToByte("D0F543F03F2517133EF2BA4A1104486758630DCFE3A883C77B4E4844E39A9BD6360D23E6644E1E071F196DDF2E4A68B4A3D93D14268D7240F6A14F0D714C17827D279D192E88931AF7300727AE9DA80A3F0E366AEBA61778171737989E1EE309"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("C08E256F276ED814021B11CAF6EC3701EC7553A1"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x62	,
                StringUtil.hexStringToByte("BA29DE83090D8D5F4DFFCEB98918995A768F41D0183E1ACA3EF8D5ED9062853E4080E0D289A5CEDD4DD96B1FEA2C53428436CE15A2A1BFE69D46197D3F5A79BCF8F4858BFFA04EDB07FC5BE8560D9CE38F5C3CA3C742EDFDBAE3B5E6DDA45557"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("CCC7303FF295A9F35BA61BD31E27EABD59658265"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x64	,
                StringUtil.hexStringToByte("B0DD551047DAFCD10D9A5E33CF47A9333E3B24EC57E8F066A72DED60E881A8AD42777C67ADDF0708042AB943601EE60248540B67E0637018EEB3911AE9C873DAD66CB40BC8F4DC77EB2595252B61C21518F79B706AAC29E7D3FD4D259DB72B6E6D446DD60386DB40F5FDB076D80374C993B4BB2D1DB977C3870897F9DFA454F5"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("792B121D86D0F3A99582DB06974481F3B2E18454"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x65	,
                StringUtil.hexStringToByte("E53EB41F839DDFB474F272CD0CBE373D5468EB3F50F39C95BDF4D39FA82B98DABC9476B6EA350C0DCE1CD92075D8C44D1E57283190F96B3537D9E632C461815EBD2BAF36891DF6BFB1D30FA0B752C43DCA0257D35DFF4CCFC98F84198D5152EC61D7B5F74BD09383BD0E2AA42298FFB02F0D79ADB70D72243EE537F75536A8A8DF962582E9E6812F3A0BE02A4365400D"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("894C5D08D4EA28BB79DC46CEAD998B877322F416"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x66	,
                StringUtil.hexStringToByte("BD1478877B9333612D257D9E3C9C23503E28336B723C71F47C25836670395360F53C106FD74DEEEA291259C001AFBE7B4A83654F6E2D9E8148E2CB1D9223AC5903DA18B433F8E3529227505DE84748F241F7BFCD2146E5E9A8C5D2A06D19097087A069F9AE3D610C7C8E1214481A4F27025A1A2EDB8A9CDAFA445690511DB805"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("F367CB70F9C9B67B580F533819E302BAC0330090"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x67	,
                StringUtil.hexStringToByte("C687ADCCF3D57D3360B174E471EDA693AA555DFDC6C8CD394C74BA25CCDF8EABFD1F1CEADFBE2280C9E81F7A058998DC22B7F22576FE84713D0BDD3D34CFCD12FCD0D26901BA74103D075C664DABCCAF57BF789494051C5EC303A2E1D784306D3DB3EB665CD360A558F40B7C05C919B2F0282FE1ED9BF6261AA814648FBC263B14214491DE426D242D65CD1FFF0FBE4D4DAFF5CFACB2ADC7131C9B147EE791956551076270696B75FD97373F1FD7804F"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("52A2907300C8445BF54B970C894691FEADF2D28E"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x68	,
                StringUtil.hexStringToByte("F4D198F2F0CF140E4D2D81B765EB4E24CED4C0834822769854D0E97E8066CBE465029B3F410E350F6296381A253BE71A4BBABBD516625DAE67D073D00113AAB9EA4DCECA29F3BB7A5D46C0D8B983E2482C2AD759735A5AB9AAAEFB31D3E718B8CA66C019ECA0A8BE312E243EB47A62300620BD51CF169A9194C17A42E51B34D83775A98E80B2D66F4F98084A448FE0507EA27C905AEE72B62A8A29438B6A4480FFF72F93280432A55FDD648AD93D82B9ECF01275C0914BAD8EB3AAF46B129F8749FEA425A2DCDD7E813A08FC0CA7841EDD49985CD8BC6D5D56F17AB9C67CEC50BA422440563ECCE21699E435C8682B6266393672C693D8B7"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("415E5FE9EC966C835FBB3E6F766A9B1A4B8674C3"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x96	,
                StringUtil.hexStringToByte("BC9AA294B1FDD263176E3243D8F448BBFFCB6ABD02C31811289F5085A9262B8B1B7C6477EB58055D9EF32A83D1B72D4A1471ECA30CE76585C3FD05372B686F92B795B1640959201523230149118D52D2425BD11C863D9B2A7C4AD0A2BFDBCA67B2713B290F493CD5521E5DDF05EF1040FC238D0A851C8E3E3B2B1F0D5D9D4AED"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("E7433E5CFC6001151D8ECD252EBC6E61F7AB2217"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x97	,
                StringUtil.hexStringToByte("E178FFE834B4B767AF3C9A511F973D8E8505C5FCB2D3768075AB7CC946A955789955879AAF737407151521996DFA43C58E6B130EB1D863B85DC9FFB4050947A2676AA6A061A4A7AE1EDB0E36A697E87E037517EB8923136875BA2CA1087CBA7EC7653E5E28A0C261A033AF27E3A67B64BBA26956307EC47E674E3F8B722B3AE0498DB16C7985310D9F3D117300D32B09"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("EBDA522B631B3EB4F4CBFC0679C450139D2B69CD"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x98	,
                StringUtil.hexStringToByte("D31A7094FB221CBA6660FB975AAFEA80DB7BB7EAFD7351E748827AB62D4AEECCFC1787FD47A04699A02DB00D7C382E80E804B35C59434C602389D691B9CCD51ED06BE67A276119C4C10E2E40FC4EDDF9DF39B9B0BDEE8D076E2A012E8A292AF8EFE18553470639C1A032252E0E5748B25A3F9BA4CFCEE073038B061837F2AC1B04C279640F5BD110A9DC665ED2FA6828BD5D0FE810A892DEE6B0E74CE8863BDE08FD5FD61A0F11FA0D14978D8CED7DD3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("D4DBA428CF11D45BAEB0A35CAEA8007AD8BA8D71"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x99	,
                StringUtil.hexStringToByte("E1740074229FA0D228A9623581D7A322903FB89BA7686712E601FA8AB24A9789186F15B70CCBBE7421B1CB110D45361688135FFD0DB15A3F516BB291D4A123EBF5A06FBF7E1EE6311B737DABB289570A7959D532B25F1DA6758C84DDCCADC049BC764C05391ABD2CADEFFA7E242D5DD06E56001F0E68151E3388074BD9330D6AFA57CBF33946F531E51E0D4902EE235C756A905FB733940E6EC897B4944A5EDC765705E2ACF76C78EAD78DD9B066DF0B2C88750B8AEE00C9B4D4091FA7338449DA92DBFC908FA0781C0128C492DB993C88BA8BB7CADFE238D477F2517E0E7E3D2B11796A0318CE2AD4DA1DB8E54AB0D94F109DB9CAEEFBEF"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("F0885777642C96BB24441FA057AD9A3490763BD2"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x0E	,
                StringUtil.hexStringToByte("AA94A8C6DAD24F9BA56A27C09B01020819568B81A026BE9FD0A3416CA9A71166ED5084ED91CED47DD457DB7E6CBCD53E560BC5DF48ABC380993B6D549F5196CFA77DFB20A0296188E969A2772E8C4141665F8BB2516BA2C7B5FC91F8DA04E8D512EB0F6411516FB86FC021CE7E969DA94D33937909A53A57F907C40C22009DA7532CB3BE509AE173B39AD6A01BA5BB85"),
                new byte[]{0x03}	,
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("A7266ABAE64B42A3668851191D49856E17F8FBCD"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0x0F	,
                StringUtil.hexStringToByte("C8D5AC27A5E1FB89978C7C6479AF993AB3800EB243996FBB2AE26B67B23AC482C4B746005A51AFA7D2D83E894F591A2357B30F85B85627FF15DA12290F70F05766552BA11AD34B7109FA49DE29DCB0109670875A17EA95549E92347B948AA1F045756DE56B707E3863E59A6CBE99C1272EF65FB66CBB4CFF070F36029DD76218B21242645B51CA752AF37E70BE1A84FF31079DC0048E928883EC4FADD497A719385C2BBBEBC5A66AA5E5655D18034EC5"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("A73472B3AB557493A9BC2179CC8014053B12BAB4"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xA1	,
                StringUtil.hexStringToByte("99D17396421EE3F919BA549D9554BE0D4F92CB8B53B4878ED60CC5B2DEEDC79B85C8BD6FD2F23C22E68B381AEEB74153AFB3C96E6C96AD018E73C2025D1EE77622A72BEE973C1AF7B908468D74FDB53DCE8380523E38C30D0A8A226529726824E209E668F49F43B0E8CD2FE527CE7CC41F33F434F95D6E2FE2F589372032F2D6504340F8C542D298B499A53D95AF4083"),
                new byte[]{0x01,0x00,0x01	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("BBB9ABE889611198C387B5B0AB374934BC2B2EA9"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC1	,
                StringUtil.hexStringToByte("E69E319C34D1B4FB43AED4BD8BBA6F7A8B763F2F6EE5DDF7C92579A984F89C4A9C15B27037764C58AC7E45EFBC34E138E56BA38F76E803129A8DDEB5E1CC8C6B30CF634A9C9C1224BF1F0A9A18D79ED41EBCF1BE78087AE8B7D2F896B1DE8B7E784161A138A0F2169AD33E146D1B16AB595F9D7D98BE671062D217F44EB68C68640C7D57465A063F6BAC776D3E2DAC61"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("DC79D6B5FC879362299BC5A637DAD2E0D99656B8"));
        Add_Test_CAPK(emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC2	,
                StringUtil.hexStringToByte("B875002F38BA26D61167C5D440367604AD38DF2E93D8EE8DA0E8D9C0CF4CC5788D11DEA689E5F41D23A3DA3E0B1FA5875AE25620F5A6BCCEE098C1B35C691889D7D0EF670EB8312E7123FCC5DC7D2F0719CC80E1A93017F944D097330EDF945762FEE62B7B0BA0348228DBF38D4216E5A67A7EF74F5D3111C44AA31320F623CB3C53E60966D6920067C9E082B746117E48E4F00E110950CA54DA3E38E5453BD5544E3A6760E3A6A42766AD2284E0C9AF"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("8E748296359A7428F536ADDA8E2C037E2B697EF6"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC3	,
                StringUtil.hexStringToByte("B93182ABE343DFBF388C71C4D6747DCDEC60367FE63CFAA942D7D323E688D0832836548BF0EDFF1EDEEB882C75099FF81A93FA525C32425B36023EA02A8899B9BF7D7934E86F997891823006CEAA93091A73C1FDE18ABD4F87A22308640C064C8C027685F1B2DB7B741B67AB0DE05E870481C5F972508C17F57E4F833D63220F6EA2CFBB878728AA5887DE407D10C6B8F58D46779ECEC1E2155487D52C78A5C03897F2BB580E0A2BBDE8EA2E1C18F6AAF3EB3D04C3477DEAB88F150C8810FD1EF8EB0596866336FE2C1FBC6BEC22B4FE5D885647726DB59709A505F75C49E0D8D71BF51E4181212BE2142AB2A1E8C0D3B7136CD7B7708E4D"),
                new byte[]{0x03}	,
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("12F1790CB0273DC73C6E70784BC24C12E8DB71F6"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC7	,
                StringUtil.hexStringToByte("CD237E34E0299DE48F1A2C94F478FE972896011E1CA6AB462B68FE0F6109C9A97C2DBEEA65932CDE0625138B9F162B92979DAAB019D3B5561D31EB2D4F09F12F927EA8F740CE0E87154965505E2272F69042B15D57CCC7F771919123978283B3CCE524D9715207BF5F5AD369102176F0F7A78A6DEB2BFF0EDCE165F3B14F14D0035B2756861FE03C43396ED002C894A3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("6221E0C726BAC8F8AC25F8F93B811D1FFD4C131C"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC8	,
                StringUtil.hexStringToByte("BF0CFCED708FB6B048E3014336EA24AA007D7967B8AA4E613D26D015C4FE7805D9DB131CED0D2A8ED504C3B5CCD48C33199E5A5BF644DA043B54DBF60276F05B1750FAB39098C7511D04BABC649482DDCF7CC42C8C435BAB8DD0EB1A620C31111D1AAAF9AF6571EEBD4CF5A08496D57E7ABDBB5180E0A42DA869AB95FB620EFF2641C3702AF3BE0B0C138EAEF202E21D"),
                new byte[]{0x03}	,
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("33BD7A059FAB094939B90A8F35845C9DC779BD50"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xC9	,
                StringUtil.hexStringToByte("B362DB5733C15B8797B8ECEE55CB1A371F760E0BEDD3715BB270424FD4EA26062C38C3F4AAA3732A83D36EA8E9602F6683EECC6BAFF63DD2D49014BDE4D6D603CD744206B05B4BAD0C64C63AB3976B5C8CAAF8539549F5921C0B700D5B0F83C4E7E946068BAAAB5463544DB18C63801118F2182EFCC8A1E85E53C2A7AE839A5C6A3CABE73762B70D170AB64AFC6CA482944902611FB0061E09A67ACB77E493D998A0CCF93D81A4F6C0DC6B7DF22E62DB"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("8E8DFF443D78CD91DE88821D70C98F0638E51E49"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25},
                (byte) 0xCA	,
                StringUtil.hexStringToByte("C23ECBD7119F479C2EE546C123A585D697A7D10B55C2D28BEF0D299C01DC65420A03FE5227ECDECB8025FBC86EEBC1935298C1753AB849936749719591758C315FA150400789BB14FADD6EAE2AD617DA38163199D1BAD5D3F8F6A7A20AEF420ADFE2404D30B219359C6A4952565CCCA6F11EC5BE564B49B0EA5BF5B3DC8C5C6401208D0029C3957A8C5922CBDE39D3A564C6DEBB6BD2AEF91FC27BB3D3892BEB9646DCE2E1EF8581EFFA712158AAEC541C0BBB4B3E279D7DA54E45A0ACC3570E712C9F7CDF985CFAFD382AE13A3B214A9E8E1E71AB1EA707895112ABC3A97D0FCB0AE2EE5C85492B6CFD54885CDD6337E895CC70FB3255E3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31},
                StringUtil.hexStringToByte("6BDA32B1AA171444C7E8F88075A74FBFE845765F"));
    }

    private void Add_Test_CAPK(EmvService emvService,
                               byte[] RID,
                               byte KeyID,
                               byte[] Modul,
                               byte[] Exponent,
                               byte[] ExpDate,
                               byte[] CheckSum){
        int result;
        EmvCAPK capk = new EmvCAPK();
        capk.RID = RID;
        capk.KeyID = KeyID;
        capk.HashInd = (byte) 0x01;
        capk.ArithInd = (byte) 0x01;
        capk.Modul = Modul;
        capk.Exponent = Exponent;
        capk.ExpDate = ExpDate;
        capk.CheckSum = CheckSum;
        result = emvService.Emv_AddCapk(capk);
        Log.i("AppendDis","Add CAPK Amex:" + result + " ID:" + capk.KeyID + "RID:" + StringUtil.bytesToHexString(capk.RID));
    }


    void Add_Amex_CAPK_Test() {
        int result = 0;
        int capkID = 0;
        boolean dbResult = false;

        /*----------------------------------------------------------------------- division line-----------------------------------------------------------------------------------------*/
        EmvCAPK capk_amex_01 = new EmvCAPK();
        capk_amex_01.RID =  new byte[]{(byte)0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25};
        capk_amex_01.KeyID = (byte)0x01;
        capk_amex_01.HashInd = (byte)0x01;
        capk_amex_01.ArithInd = (byte)0x01;
        capk_amex_01.Modul = StringUtil.hexStringToByte("A20DAAD5D5F62E40852521DC9D5AB9F87C610888A32367601E27311" +
                "D6D3DFB5BB6142DB4004651A09C8B3ED229A97200B383689AFB2E55A3F0C16D033A60A1438C7C5D08E4967D2" +
                "953301D32DFE07999039FFE12202491CEEFCC4D014AF2A385B3EAE2ADA0134A7642B513A7330879F46035E20F27578D233ECF35E6CE9B17D9");
        capk_amex_01.Exponent = new byte[]{0x03};
        capk_amex_01.ExpDate = new byte[]{0x25,0x12,0x31};
        capk_amex_01.CheckSum = StringUtil.hexStringToByte("4C3681CD1D207B2BDC6E4DD5E33657F356496994");
        result =  EmvService.Emv_AddCapk(capk_amex_01);

        Log.i("AppendDis","Add CAPK capk_amex_01:" + result + " ID:" + capk_amex_01.KeyID);
        if(result == EmvService.EMV_TRUE){
            Log.i("AppendDis","Create capk_amex_01 database:" + dbResult);
        }

        EmvCAPK capk_03_97 = new EmvCAPK();
        capk_03_97.RID = new byte[]{(byte) 0xA0, 0x00, 0x00, 0x00, (byte) 0x25};
        capk_03_97.KeyID = (byte) 0x97;
        capk_03_97.HashInd = (byte) 0x01;
        capk_03_97.ArithInd = (byte) 0x01;
        capk_03_97.Modul = StringUtil.hexStringToByte("E178FFE834B4B767AF3C9A511F973D8E8505C5FCB2D3768075AB7CC946A95578995587" +
                "9AAF737407151521996DFA43C58E6B130EB1D863B85DC9FFB4050947A2676AA6A061A4A7AE1EDB0E36A697E87E037517EB892313" +
                "6875BA2CA1087CBA7EC7653E5E28A0C261A033AF27E3A67B64BBA26956307EC47E674" +
                "E3F8B722B3AE0498DB16C7985310D9F3D117300D32B09");
        capk_03_97.Exponent = new byte[]{0x03};
        capk_03_97.ExpDate = new byte[]{0x25, 0x12, 0x31};
        capk_03_97.CheckSum = StringUtil.hexStringToByte("EBDA522B631B3EB4F4CBFC0679C450139D2B69CD");
        result = EmvService.Emv_AddCapk(capk_03_97);
        if (result == EmvService.EMV_TRUE) {
            Log.i("AppendDis","Create capk_03_97 database:" + dbResult);
        }

        /*----------------------------------------------------------------------- division line-----------------------------------------------------------------------------------------*/

        EmvCAPK capk_03_71 = new EmvCAPK();
        capk_03_71.RID = new byte[]{(byte) 0xA0, 0x00, 0x00, 0x00, (byte) 0x25};
        capk_03_71.KeyID = (byte) 0x98;
        capk_03_71.HashInd = (byte) 0x01;
        capk_03_71.ArithInd = (byte) 0x01;
        capk_03_71.Modul = StringUtil.hexStringToByte("D31A7094FB221CBA6660FB975AAFEA80DB7BB7E" +
                "AFD7351E748827AB62D4AEECCFC1787FD47A04699A02DB00D7C382E80E804B35C59434C602389D691B9" +
                "CCD51ED06BE67A276119C4C10E2E40FC4EDDF9DF39B9B0BDEE8D076E2A012E8A292AF8EFE18553470639C1A03" +
                "2252E0E5748B25A3F9BA4CFCEE073038B06" +
                "1837F2AC1B04C279640F5BD110A9DC665ED2FA6828BD5D0FE810A892DEE6B0E74CE8863BDE08FD5FD61A0F11FA0D14978D8CED7DD3");
        capk_03_71.Exponent = new byte[]{0x03};
        capk_03_71.ExpDate = new byte[]{0x25, 0x12, 0x31};
        capk_03_71.CheckSum = StringUtil.hexStringToByte("D4DBA428CF11D45BAEB0A35CAEA8007AD8BA8D71");
        result = EmvService.Emv_AddCapk(capk_03_71);
        if (result == EmvService.EMV_TRUE) {
            Log.i("AppendDis","Create capk_03_71 database:" + dbResult);
        }

        //==================================================
        EmvCAPK capk_03_50 = new EmvCAPK();
        capk_03_50.RID = new byte[]{(byte) 0xA0, 0x00, 0x00, 0x00, (byte) 0x25};
        capk_03_50.KeyID = (byte) 0x99;
        capk_03_50.HashInd = (byte) 0x01;
        capk_03_50.ArithInd = (byte) 0x01;
        capk_03_50.Modul = StringUtil.hexStringToByte("E1740074229FA0D228A9623581D7A322903FB89BA7686712E601FA8AB24A9789186F15B70CCBBE7421B1CB110D45361688135FFD0DB15A3F516BB291D4A123EBF5A06FBF7E1EE6311B737DABB289570A7959D532B25F1DA6758C84DDCCADC049BC764C05391ABD2CADEFFA7E242D5DD06E56001F0E68151E3388074BD9330D6AFA57CBF33946F531E51E0D4902EE235C756A905FB733940E6EC897B4944A5EDC765705E2ACF76C78EAD78DD9B066DF0B2C88750B8AEE00C9B4D40" +
                "91FA7338449DA92DBFC908FA0781C0128C492DB993C88BA8BB7CADFE238D477F25" +
                "17E0E7E3D2B11796A0318CE2AD4DA1DB8E54AB0D94F109DB9CAEEFBEF");
        capk_03_50.Exponent = new byte[]{0x03};
        capk_03_50.ExpDate = new byte[]{0x25, 0x12, 0x31};
        capk_03_50.CheckSum = StringUtil.hexStringToByte("94790D020F4F692D59289F36451872078005B63B");
        result = EmvService.Emv_AddCapk(capk_03_50);
        if (result == EmvService.EMV_TRUE) {
            Log.i("AppendDis","Create capk_03_50 database:" + dbResult);
        }

        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x01	,
                StringUtil.hexStringToByte("A20DAAD5D5F62E40852521DC9D5AB9F87C610888A32367601E27311D6D3DFB5BB6142DB4004651A09C8B3ED229A97200B383689AFB2E55A3F0C16D033A60A1438C7C5D08E4967D2953301D32DFE07999039FFE12202491CEEFCC4D014AF2A385B3EAE2ADA0134A7642B513A7330879F46035E20F27578D233ECF35E6CE9B17D9"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("4C3681CD1D207B2BDC6E4DD5E33657F356496994"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x02	,
                StringUtil.hexStringToByte("94EA62F6D58320E354C022ADDCF0559D8CF206CD92E869564905CE21D720F971B7AEA374830EBE1757115A85E088D41C6B77CF5EC821F30B1D890417BF2FA31E5908DED5FA677F8C7B184AD09028FDDE96B6A6109850AA800175EABCDBBB684A96C2EB6379DFEA08D32FE2331FE103233AD58DCDB1E6E077CB9F24EAEC5C25AF"),
                new byte[]{0x01,0x00,0x01	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("AECCAD45A2E189E30CB3CAE8FFEEB3432C23D5AC"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x03	,
                StringUtil.hexStringToByte("9C6BE5ADB10B4BE3DCE2099B4B210672B89656EBA091204F613ECC623BEDC9C6D77B660E8BAEEA7F7CE30F1B153879A4E36459343D1FE47ACDBD41FCD710030C2BA1D9461597982C6E1BDD08554B726F5EFF7913CE59E79E357295C321E26D0B8BE270A9442345C753E2AA2ACFC9D30850602FE6CAC00C6DDF6B8D9D9B4879B2826B042A07F0E5AE526A3D3C4D22C72B9EAA52EED8893866F866387AC05A1399"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("5479CA9E9A89EB4815F348C487553C1F45AB7AC1"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x04	,
                StringUtil.hexStringToByte("A99A6D3E071889ED9E3A0C391C69B0B804FC160B2B4BDD570C92DD5A0F45F53E8621F7C96C40224266735E1EE1B3C06238AE35046320FD8E81F8CEB3F8B4C97B940930A3AC5E790086DAD41A6A4F5117BA1CE2438A51AC053EB002AED866D2C458FD73359021A12029A0C043045C11664FE0219EC63C10BF2155BB2784609A106421D45163799738C1C30909BB6C6FE52BBB76397B9740CE064A613FF8411185F08842A423EAD20EDFFBFF1CD6C3FE0C9821479199C26D8572CC8AFFF087A9C3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("A8E8D35474958BB57CD9E0DCEEFF381F0B7E0AB3"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x05	,
                StringUtil.hexStringToByte("A25A6BD783A5EF6B8FB6F83055C260F5F99EA16678F3B9053E0F6498E82C3F5D1E8C38F13588017E2B12B3D8FF6F50167F46442910729E9E4D1B3739E5067C0AC7A1F4487E35F675BC16E233315165CB142BFDB25E301A632A54A3371EBAB6572DEEBAF370F337F057EE73B4AE46D1A8BC4DA853EC3CC12C8CBC2DA18322D68530C70B22BDAC351DD36068AE321E11ABF264F4D3569BB71214545005558DE26083C735DB776368172FE8C2F5C85E8B5B890CC682911D2DE71FA626B8817FCCC08922B703869F3BAEAC1459D77CD85376BC36182F4238314D6C4212FBDD7F23D3"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("B43A837DD523785CEA963195CA8A58D1FD70D32D"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x06	,
                StringUtil.hexStringToByte("A191CB87473F29349B5D60A88B3EAEE0973AA6F1A082F358D849FDDFF9C091F899EDA9792CAF09EF28F5D22404B88A2293EEBBC1949C43BEA4D60CFD879A1539544E09E0F09F60F065B2BF2A13ECC705F3D468B9D33AE77AD9D3F19CA40F23DCF5EB7C04DC8F69EBA565B1EBCB4686CD274785530FF6F6E9EE43AA43FDB02CE00DAEC15C7B8FD6A9B394BABA419D3F6DC85E16569BE8E76989688EFEA2DF22FF7D35C043338DEAA982A02B866DE5328519EBBCD6F03CDD686673847F84DB651AB86C28CF1462562C577B853564A290C8556D818531268D25CC98A4CC6A0BDFFFDA2DCCA3A94C998559E307FDDF915006D9A987B07DDAEB3B"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("5EDE89510DED5FCC1824A08438327066B519FCF3"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x07	,
                StringUtil.hexStringToByte("A20DAAD5D5F62E40852521DC9D5AB9F87C610888A32367601E27311D6D3DFB5BB6142DB4004651A09C8B3ED229A97200B383689AFB2E55A3F0C16D033A60A1438C7C5D08E4967D2953301D32DFE07999039FFE12202491CEEFCC4D014AF2A385B3EAE2ADA0134A7642B513A7330879F46035E20F27578D233ECF35E6CE9B"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("4C3681CD1D207B2BDC6E4DD5E33657F356496994"));
        Add_Test_CAPK(
                emvService,
                new byte[]{(byte)	0xA0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25	},
                (byte) 0x09	,
                StringUtil.hexStringToByte("A3834CB1D190545D22F0EBE2FFF75E5BE5755A169A7FE42D0A2EC166965A27B04378CEA5A3839A4327B5E68F23DD98E9497BC8DBADC04E7F86E8FF1C2313D70947BEB7AB7C9FC44FD6AF43272F29B90D658C0F77273A20E70CD857E7684596D5694CA7BA50DBAAF40CB4BB39FE0EA0C9441399D72A351F38801074866F37D48A4F156F9CAD6F10DD644649A20DBF5923743F578F146AF8C4BA2FBBD41FAA0486A559E07DF5D3A4BF85F55459986FC345"),
                new byte[]{0x03	},
                new byte[]{0x28, 0x12, 0x31}	,
                StringUtil.hexStringToByte("79896BD7B076C4F94AFF0B063A73861A593413F5"));

    }

    private void addAmexAID(EmvService emvService){
        String name = "";
        int result = 0;
        boolean dbResult = false;

        EmvApp APP_AMEX = new EmvApp();
        name = "AMEX Card";
        try {
            APP_AMEX.AppName = name.getBytes("ascii");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        APP_AMEX.AID = new byte[]{(byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x25};
        APP_AMEX.SelFlag = (byte) 0x00;
        APP_AMEX.Priority = (byte) 0x00;
        APP_AMEX.TargetPer = (byte) 20;
        APP_AMEX.MaxTargetPer = (byte) 50;
        APP_AMEX.FloorLimitCheck = (byte) 1;
        APP_AMEX.RandTransSel = (byte) 1;
        APP_AMEX.VelocityCheck = (byte) 1;
        APP_AMEX.FloorLimit = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00};//9F1B:FloorLimit
        APP_AMEX.Threshold = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00};
        APP_AMEX.TACDenial = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_AMEX.TACOnline = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_AMEX.TACDefault = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_AMEX.AcquierId = new byte[]{(byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67, (byte) 0x89, (byte) 0x10};
        APP_AMEX.DDOL = new byte[]{(byte) 0x03, (byte) 0x9F, (byte) 0x37, (byte) 0x04};
        APP_AMEX.TDOL = new byte[]{(byte) 0x03, (byte) 0x9F, (byte) 0x02, (byte) 0x06};
        APP_AMEX.Version = new byte[]{(byte) 0x00, (byte) 0x01};

        result = emvService.Emv_AddApp(APP_AMEX);
        Log.i("MasterCard","ADD APP_MasterCard:" + result+  "  AID :"+StringUtil.bytesToHexString(APP_AMEX.AID) );
        if (result == emvService.EMV_TRUE) {
            Log.i("MasterCard","creat APP_MasterCard database :" + dbResult);
        }
    }

    private void addAID(EmvService emvService,String AID){
        String name = "";
        int result = 0;
        boolean dbResult = false;

        EmvApp APP_MasterCard = new EmvApp();
        name = "Master Card";
        try {
            APP_MasterCard.AppName = name.getBytes("ascii");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        APP_MasterCard.AID = StringUtil.hexStringToByte(AID);
        APP_MasterCard.SelFlag = (byte) 0x00;
        APP_MasterCard.Priority = (byte) 0x00;
        APP_MasterCard.TargetPer = (byte) 0;
        APP_MasterCard.MaxTargetPer = (byte) 0;
        APP_MasterCard.FloorLimitCheck = (byte) 1;
        APP_MasterCard.RandTransSel = (byte) 1;
        APP_MasterCard.VelocityCheck = (byte) 1;
        APP_MasterCard.FloorLimit = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x50, (byte) 0x00};//9F1B:FloorLimit
        APP_MasterCard.Threshold = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_MasterCard.TACDenial = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_MasterCard.TACOnline = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_MasterCard.TACDefault = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        APP_MasterCard.AcquierId = new byte[]{(byte) 0x01, (byte) 0x22, (byte) 0x55, (byte) 0x66, (byte) 0x33, (byte) 0x40};
        APP_MasterCard.DDOL = new byte[]{(byte) 0x03, (byte) 0x9F, (byte) 0x37, (byte) 0x04};
        APP_MasterCard.TDOL = new byte[]{(byte) 0x0F, (byte) 0x9F, (byte) 0x02, (byte) 0x06, (byte) 0x5F, (byte) 0x2A, (byte) 0x02, (byte) 0x9A, (byte) 0x03, (byte) 0x9C, (byte) 0x01, (byte) 0x95, (byte) 0x05, (byte) 0x9F, (byte) 0x37, (byte) 0x04};
        APP_MasterCard.Version = new byte[]{(byte) 0x00, (byte) 0x02};

        result = emvService.Emv_AddApp(APP_MasterCard);
        Log.i("MasterCard","ADD APP_MasterCard:" + result);
        if (result == emvService.EMV_TRUE) {
            Log.i("MasterCard","creat APP_MasterCard database :" + dbResult);
        }
    }


    /**
     * init views
     */
    private void initView() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        panDialog = builder.setTitle("Please Input PIN")
                .setCancelable(false)
                .create();

        et_MsgView = (EditText) findViewById(R.id.id_MsgView);
        bt_confirm = (MaterialButton) findViewById(R.id.id_confirm);
        bt_confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClearDis();
                confirm();
            }
        });


    }

    public int WriteKey()
    {

        if (isMkMode){
            byte[] MasterKey = new byte[]{0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11};
            byte[] PinKey = new byte[]{0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22,0x22};
            byte[] MacKey = new byte[]{0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33,0x33};
            byte[] PanKey = new byte[]{0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44,0x44};

            _LastCode = saveMasterKey(MasterKey);
            if(PinpadService.PIN_OK != _LastCode) {
                return _LastCode;
            }
            _LastCode = savePinKey(PinKey);
            if(PinpadService.PIN_OK != _LastCode) {
                return _LastCode;
            }
            _LastCode = saveMacKey(MacKey);
            if(PinpadService.PIN_OK != _LastCode) {
                return _LastCode;
            }
            _LastCode = savePanKey(PanKey);


        }else {
            //byte[] IPEK = StringUtil.hexStringToByte("0123456789ABCDEFFEDCBA9876543210");
            byte[] IPEK = StringUtil.hexStringToByte("3F2216D8297BCE9C");
            byte[] IKSN = StringUtil.hexStringToByte("0000000002DDDDE00000");
            _LastCode = pinpadService.Pinpad_Write_DEA_DUKPT_IPEK(1,IPEK,IKSN);
        }
        return _LastCode;
    }


    public int InitPinPad()
    {

        pinpadService = new com.common.sdk.emv.PinpadService(context);
        return pinpadService.Pinpad_Open(context);

    }

    /**
     * transaction request
     */
    public boolean pay() {
        return true;
    }

    /**
     * write masterKey
     * @param masterKey masterKey
     */
    private int saveMasterKey(byte[] masterKey) {


        return pinpadService.Pinpad_Write_Normal_Key(0,
                PinpadEnum.ENUM_WRITE_MODE.KEY_WRITE_DIRECT,
                0,
                PinpadEnum.PINPAD_NOR_KEY_TYPE.PINKEY_MASTER,
                masterKey);
    }

    /**
     * write pinKey
     * @param pinKey pinKey
     */
    private int savePinKey(byte[] pinKey) {


        return pinpadService.Pinpad_Write_Normal_Key(PIN_KEY_INDEX,
                PinpadEnum.ENUM_WRITE_MODE.KEY_WRITE_DECRYPT,
                0,
                PinpadEnum.PINPAD_NOR_KEY_TYPE.PINKEY_PIN,
                pinKey);
    }

    /**
     * write macKey
     * @param macKey macKey
     */
    private int saveMacKey(byte[] macKey) {

        return pinpadService.Pinpad_Write_Normal_Key(MAC_KEY_INDEX,
                PinpadEnum.ENUM_WRITE_MODE.KEY_WRITE_DECRYPT,
                0,
                PinpadEnum.PINPAD_NOR_KEY_TYPE.PINKEY_MAC,
                macKey);
    }

    /**
     * write panKey
     * @param panKey panKey
     */
    private int savePanKey(byte[] panKey){

        if (isPanDesMode)
        {
            return pinpadService.Pinpad_Write_Normal_Key(PAN_KEY_INDEX,
                    PinpadEnum.ENUM_WRITE_MODE.KEY_WRITE_DECRYPT,
                    0,PinpadEnum.PINPAD_NOR_KEY_TYPE.PINKEY_DES,
                    panKey);
        }
        else {
            return pinpadService.Pinpad_Write_Normal_Key(PAN_KEY_INDEX,
                    PinpadEnum.ENUM_WRITE_MODE.KEY_WRITE_DECRYPT,
                    0,PinpadEnum.PINPAD_NOR_KEY_TYPE.PINKEY_AES,
                    panKey);
        }

    }

    /**
     * use MacKey to calc data
     * @param data String? data need to calc
     * @return String MacResult HexString format
     */
    private String GetMac(byte[] data){
        String  ResultString = "";
        PinpadBytesOut MacResult = new PinpadBytesOut();
        int result = 0;
        if (isMkMode) {
            result = pinpadService.Pinpad_Calculate_Normal_MAC(
                    MAC_KEY_INDEX,
                    data, MacResult,
                    PinpadEnum.ENUM_MAC_MODE.MAC_X99);
            if(PinpadService.PIN_OK == result){
                ResultString = StringUtil.bytesToHexString(MacResult.outResult);
            }
            else {
                AppendDis("Pinpad_Calculate_Normal_MAC Err:" + ErrMsg.GetPinPadErrMsg(result));
            }
        }
        else{
            if (isDesMode) {
                result = pinpadService.Pinpad_DEA_DUKPT_Calculate_Mac(
                        data,
                        MacResult, 0);
                if(PinpadService.PIN_OK == result)
                {
                    ResultString = StringUtil.bytesToHexString(MacResult.outResult);
                }else{
                    AppendDis("Pinpad_DEA_DUKPT_Calculate_Mac Err:" + ErrMsg.GetPinPadErrMsg(result));
                }
            }
            else{
                result = pinpadService.Pinpad_AES_DUKPT_Calculate_Mac(
                        PinpadEnum.ENUM_AES_DUKPT_KeyUsage._MessageAuthenticationGeneration,
                        data,
                        MacResult, 0);
                if(PinpadService.PIN_OK == result) {
                    ResultString = StringUtil.bytesToHexString(MacResult.outResult);
                }
                else {
                    AppendDis("Pinpad_AES_DUKPT_Calculate_Mac Err:" + ErrMsg.GetPinPadErrMsg(result));
                }
            }
        }
        return  ResultString;
    }

    private void InitTheClasses()
    {
        //set the init flag
        isDevInit = false;
        //new the payware class
        activityVisaPayware = new ActivityVisaPayware(this);
        if(null == activityVisaPayware){
            return;
        }
        //new the PBOC class
        activityPBOC = new ActivityPBOC(this);
        if(null == activityPBOC){
            return;
        }
        //new the MasterPaypass class
        activityMasterPaypass = new ActivityMasterPaypass(this);
        if(null == activityMasterPaypass){
            return;
        }
        //new the Rupay class
        activityRupay = new ActivityRupay(this);
        if(null == activityRupay){
            return;
        }
        //new the Amex class
        activityAmex = new ActivityAmex(this);
        if(null == activityAmex){
            return;
        }
        //new the Mir class
        activityMir = new ActivityMir(this);
        if(null == activityMir){
            return;
        }

        //new the Transit class
        activityTransit = new ActivityTransit(this);
        if(null == activityTransit){
            return;
        }

        //init succ
        isDevInit = true;
        return ;
    }

    void ClearDis() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                et_MsgView.setText("");
                DisplayBuf = new StringBuffer("");
            }
        });
    }


    void AppendDis(String Mes) {
        Log.i("AppendDis", Mes);
        DisplayBuf.append(Mes);
        DisplayBuf.append("\n");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                et_MsgView.requestFocus();
                et_MsgView.setText(DisplayBuf.toString());
                et_MsgView.setSelection(et_MsgView.getText().length());
            }
        });
    }
}