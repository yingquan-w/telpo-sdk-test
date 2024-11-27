package com.pos.demosdk.EMV;

import android.content.Context;
import android.util.Log;

import com.pos.demosdk.MainActivity;
import com.telpo.emv.EmvParam;
import com.telpo.emv.EmvService;
import com.telpo.emv.EmvTLV;
import com.telpo.emv.PaypassErrorData;
import com.telpo.emv.PaypassListener;
import com.telpo.emv.PaypassOutCome;
import com.telpo.emv.PaypassParam;
import com.telpo.emv.PaypassResult;
import com.telpo.emv.PaypassUserData;
import com.telpo.util.StringUtil;

import java.util.List;

public class ActivityMasterPaypass {

    private EMVActivity nContext;
    private int _LastCode = 0;

    public ActivityMasterPaypass(Context context) {
        this.nContext = (EMVActivity) context;
    }

    PaypassListener paypassListener =new PaypassListener() {
        @Override
        public int onPaypass_InitApp() {

            int ret = 0;
            EmvTLV tlv = new EmvTLV(0x9F06);
            ret = nContext.emvService.Emv_GetTLV(tlv);
            Log.e("yw_AID","AID:"+ StringUtil.bytesToHexString(tlv.Value));

            nContext.AppendDis("AID:"+ StringUtil.bytesToHexString(tlv.Value));

            if (StringUtil.bytesToHexString(tlv.Value).equals("A0000000041010")){
                PaypassParam param = new PaypassParam();
                param.TacDefault= StringUtil.hexStringToByte("F45084800C");
                param.TacDenial= StringUtil.hexStringToByte("0000000000");
                param.TacOnline= StringUtil.hexStringToByte("F45084800C");

                _LastCode = nContext.emvService.Paypass_SetPaypassParam(param);
                if(EmvService.EMV_TRUE != _LastCode) {
                    nContext.AppendDis("Paypass_SetPaypassParam fail, err code:"+_LastCode);
                }
                else{
                    nContext.AppendDis("Paypass_SetPaypassParam succ!");
                }
            }else if (StringUtil.bytesToHexString(tlv.Value).equals("A0000000043060")){ //Maestro
                PaypassParam param = new PaypassParam();
                param.TacDefault= StringUtil.hexStringToByte("F45004800C");
                param.TacDenial= StringUtil.hexStringToByte("0000800000");
                param.TacOnline= StringUtil.hexStringToByte("F45004800C");
                _LastCode = nContext.emvService.Paypass_SetPaypassParam(param);
                if(EmvService.EMV_TRUE != _LastCode) {
                    nContext.AppendDis("Paypass_SetPaypassParam fail, err code:"+_LastCode);
                }
                else{
                    nContext.AppendDis("Paypass_SetPaypassParam succ!");
                }
            }


            return EmvService.EMV_TRUE;
        }

        @Override
        public int onPaypass_SendDEK(int i) {
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onPaypass_WaitDET(int i) {
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onPaypass_SendOut(int i) {
            return EmvService.EMV_TRUE;
        }

        @Override
        public int onPaypass_SendMsg(int i) {
            return EmvService.EMV_TRUE;
        }
    };

    public boolean StartTransaction(double amount)
    {
        //set the listener
        nContext.emvService.setListener(paypassListener);
        //set emv param
        EmvParam mEMVParam;
        mEMVParam = new EmvParam();
        _LastCode = nContext.emvService.Emv_SetParam(mEMVParam);
        if(EmvService.EMV_TRUE != _LastCode) {
            nContext.AppendDis("Emv_SetParam fail,err code:"+_LastCode);
            return false;
        }
        else{ nContext.AppendDis("Emv_SetParam succ"); }
        //Paypass_TransInit
        PaypassParam param = new PaypassParam();
        param.TermCountryCode=156;
        param.TacDefault= StringUtil.hexStringToByte("F45084800C");
        param.TacDenial= StringUtil.hexStringToByte("0000000000");
        param.TacOnline= StringUtil.hexStringToByte("F45084800C");
        //Paypass_SetPaypassParam
        _LastCode = nContext.emvService.Paypass_TransInit(param);
        if(EmvService.EMV_TRUE != _LastCode) {
            nContext.AppendDis("Paypass_TransInit fail, err code:"+_LastCode);
            return false;
        }
        else{
            nContext.AppendDis("Paypass_TransInit succ!");
        }
        //Paypass_StartApp
        _LastCode = nContext.emvService.Paypass_StartApp((int)amount*100, 0, 978, 2, 0);

        List<EmvTLV> tagList=EMVUtils.getTLVContactlessCardDataTags();
        int ret=0;
        for (EmvTLV emvTLV : tagList) {
            ret = nContext.emvService.Emv_GetTLV(emvTLV);
            // AppendDis("TLV"+Integer.toHexString(emvTLV.Tag).toUpperCase()+ ":"+StringUtil.bytesToHexString(emvTLV.Value));
            if (EmvService.EMV_TRUE == ret) {
                nContext.AppendDis("Tag" + Integer.toHexString(emvTLV.Tag).toUpperCase() + ":" + StringUtil.bytesToHexString(emvTLV.Value));
            } else {
                nContext.AppendDis("Tag" + Integer.toHexString(emvTLV.Tag).toUpperCase() + ":N/G");
            }
        }
        //get result
        PaypassOutCome OutCome = new PaypassOutCome();
        PaypassUserData UserData = new PaypassUserData();
        PaypassErrorData ErrorData = new PaypassErrorData();
        nContext.emvService.Paypass_GetResult(OutCome,UserData,ErrorData);

        if(PaypassResult.PAYPASS_STATUS_APPROVED == OutCome.Status) {
            //offline APPROVED
        }
        else if(PaypassResult.PAYPASS_STATUS_ONLINE == OutCome.Status)
        {
            if(OutCome.CVM == PaypassResult.PAYPASS_CVM_ONLINEPIN) {
                if(null == nContext.cardNum || nContext.cardNum.isEmpty()){
                    nContext.AppendDis("Transaction Fail,No Card info!");
                    return false;
                }
                String hidePan = nContext.cardNum.substring(0, 6) + "******" + nContext.cardNum.substring(nContext.cardNum.length() - 4,nContext.cardNum.length());
                nContext.changePanUIVisibility(true, "PAN:" + hidePan);
                //online PIN
                if (nContext.isMkMode) {
                    //MK/SK mode
                    nContext.pinBlock = nContext.getMkPin(nContext.cardNum);
                } else {
                    //Dukpt mode
                    nContext.pinBlock = nContext.getDukptPin(nContext.cardNum);
                }
                if("" == nContext.pinBlock) {
                    nContext.changePanUIVisibility(false, null);
                    nContext.AppendDis("Transaction Fail");
                    return false;
                }
            }
        }
        else{
            nContext.AppendDis("Paypass_StartApp Fail:" + _LastCode);
            return false;
        }
        nContext.AppendDis("Transaction Success");
        return true;
    }
}
