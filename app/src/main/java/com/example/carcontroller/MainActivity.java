package com.example.carcontroller;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity {
    enum Cmd{
        STOP("0000"),
        FRONT("1111"),
        BACK("2222"),
        LEFT("3333"),
        RIGHT("4444"),
        LEFTFRONT("5555"),
        LEFTBACK("6666"),
        RIGHTFRONT("7777"),
        RIGHTBACK("8888"),
        LEFTROUND("leftround"),
        RIGHTROUND("rightround"),
        TURNLEFT("turnleft"),
        TURNRIGHT("turnright"),
        TURNUP("turnup"),
        TURNDOWN("turndown"),
        TURNSTOP("turnstop"),
        LASERON("laseron"),
        LASEROFF("laseroff"),
        FIRE("fire");

        private String cmd;

        private Cmd(String cmd){
            this.cmd = cmd;
        }

        public String getCmd(){
            return cmd;
        }
    }
    private MjpegView view1;
    private DatagramSocket socket;
    private Button connectBtn;
    private Button stopConnectBtn;
    private Switch trackerSwitch;
    private Button laserBtn;
    private Button fireBtn;
    private EditText ipInupt;
    private EditText camPortInput;
    private EditText cmdPortInput;
    private String serverIp;
    private int camPort;
    private int cmdPort;
    private String mjpegUrl;
    private boolean ismjpegOn = false;
    private boolean isLaserOn = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        connectBtn = (Button) findViewById(R.id.connectbtn);
        stopConnectBtn = (Button) findViewById(R.id.stopconnectbtn);
        trackerSwitch = (Switch) findViewById(R.id.tracker_switch);
        laserBtn = (Button) findViewById(R.id.laserbtn);
        fireBtn = (Button) findViewById(R.id.firebtn);
        ipInupt = (EditText) findViewById(R.id.ipinput);
        cmdPortInput = (EditText) findViewById(R.id.cmdport);
        camPortInput = (EditText) findViewById(R.id.camport);


        JoyStickView joyStickView = findViewById(R.id.joy);
        joyStickView.setInnerCircleImageResId(R.drawable.ic_directions_run_black_24dp);
        joyStickView.setInnerCircleColor(Color.BLACK);
        joyStickView.setOnMoveListener(new JoyStickView.OnMoveListener() {
            @Override
            public void onMove(double angle, float strength) {
                new Thread(new Runnable() {
                    public void run() {

                        String cmd = convertToDirectionCmd(angle, strength);
                        Log.i("stick","[directorn] angle:"+angle+" strength:"+strength+" cmd:"+cmd);
                        sendMsg(cmd);

                    }
                }).start();

            }
        });

        JoyStickView joyStickView2 = findViewById(R.id.joy2);
        joyStickView2.setInnerCircleImageResId(R.drawable.ic_directions_run_black_24dp);
        joyStickView2.setInnerCircleColor(Color.BLACK);
        joyStickView2.setOnMoveListener(new JoyStickView.OnMoveListener() {
            @Override
            public void onMove(double angle, float strength) {
                new Thread(new Runnable() {
                    public void run() {
                        String cmd = convertToTurnCmd(angle,strength);
                        Log.i("stick","[turn] angle:"+angle+" strength:"+strength+" cmd:"+cmd);
                        sendMsg(cmd);
                    }
                }).start();

            }
        });

        view1 = findViewById(R.id.mjpegview1);
        view1.setAdjustHeight(true);
        view1.setAdjustWidth(true);
        view1.setMode(MjpegView.MODE_BEST_FIT);
//        view1.setMode(MjpegView.MODE_FIT_WIDTH);
//        view1.setMode(MjpegView.MODE_FIT_HEIGHT);
//        view1.setMsecWaitAfterReadImageError(1000);
//        view1.setRecycleBitmap(true);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        //???Android?????????????????????????????????????????????
                        serverIp = ipInupt.getText().toString();
                        camPort = Integer.parseInt(camPortInput.getText().toString());
                        cmdPort = Integer.parseInt(cmdPortInput.getText().toString());
                        mjpegUrl = "http://"+serverIp+":"+camPort+"/stream";
                        Log.i("stick","mjpegUrl="+mjpegUrl);

                        view1.setUrl(mjpegUrl);
                        view1.startStream();
                        ismjpegOn = true;

                        Log.i("stick","mjpegView start");
                        try {
                            socket = new DatagramSocket();
                            view1.setSocket(socket);
                        } catch (SocketException e) {
                            Log.e("mjpeg",e.toString());
                        }
                        view1.setServerIp(serverIp);
                        view1.setCmdPort(cmdPort);

                        sendMsg("0000");

                    }
                }).start();
            }
        });

        stopConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        view1.stopStream();
                        ismjpegOn = false;

                    }
                }).start();
            }
        });

        trackerSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    public void run() {

                        if (trackerSwitch.isChecked()){
                            view1.setTrackerOn(true);
                        }else {
                            view1.setTrackerOn(false);
                        }

                    }
                }).start();
            }
        });


        laserBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {
                        if (isLaserOn){
                            sendMsg("laseroff");
                        }else {
                            sendMsg("laseron");
                        }

                        isLaserOn = !isLaserOn;
                    }
                }).start();
            }
        });

        fireBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        sendMsg("fire");

                    }
                }).start();
            }
        });

    }

    private String convertToTurnCmd(double angle, float strength) {
        String cmd = "";
        float sector = 45f;
        if (strength < 20f){
            cmd = Cmd.TURNSTOP.getCmd();
        } else if ((angle>=0 && angle<sector) || (angle>=sector*7 && angle<=360)) {
            cmd = Cmd.TURNRIGHT.getCmd();
        } else if (angle>=sector && angle < sector*3) {
            cmd = Cmd.TURNUP.getCmd();
        } else if (angle>=sector*3 && angle<sector*5) {
            cmd = Cmd.TURNLEFT.getCmd();
        } else if (angle>=sector*5 && angle<sector*7) {
            cmd = Cmd.TURNDOWN.getCmd();
        }

        return cmd;
    }

    private String convertToDirectionCmd(double angle, float strength) {
        String cmd = "" ;
        float sector = 22.5f;
        if (strength < 20f){
            cmd = Cmd.STOP.getCmd();
        }else if ((angle>=0 && angle<sector) || (angle>=sector*15) && angle<=360){
            cmd = Cmd.RIGHT.getCmd();
        } else if (angle >=sector && angle < sector*3) {
            cmd = Cmd.RIGHTFRONT.getCmd();
        } else if (angle >= sector*3 && angle < sector*5) {
            cmd = Cmd.FRONT.getCmd();
        } else if (angle >= sector*5 && angle < sector*7) {
            cmd = Cmd.LEFTFRONT.getCmd();
        } else if (angle >= sector * 7 && angle< sector*9) {
            cmd = Cmd.LEFT.getCmd();
        } else if (angle >= sector*9 && angle < sector*11) {
            cmd = Cmd.LEFTBACK.getCmd();
        } else if (angle >= sector*11 && angle < sector*13) {
            cmd = Cmd.BACK.getCmd();
        } else if (angle >= sector*13 && angle < sector*15) {
            cmd = Cmd.RIGHTBACK.getCmd();
        }

        return cmd;
    }

    @Override
    protected void onResume() {
        if(getRequestedOrientation()!= ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

//        view1.startStream();
//        view2.startStream();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (ismjpegOn){
            view1.stopStream();
        }
//        view2.stopStream();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (ismjpegOn){
            view1.stopStream();
        }
//        view2.stopStream();
        super.onStop();
    }

    public void sendMsg(String msg) {
        try {
            if (socket == null) {
                // 20020?????????????????????
                socket = new DatagramSocket();
            }
            //????????????????????????????????????????????????????????????????????????
            byte data[] = msg.getBytes();
            // ?????????IP????????????
            DatagramPacket pack = new DatagramPacket(data, data.length, InetAddress.getByName(serverIp), cmdPort); // ??????DatagramPacket ???????????????????????????port??????????????????????????????????????????
            Log.v("stick", "??????"+serverIp+":"+cmdPort+"--"+msg);
            socket.send(pack);
            Log.v("stick", "???????????????");
        } catch (Exception e) {
            Log.v("stick", "???????????????");
            e.printStackTrace();
        }
    }


//    public static void main(String[] args) {
//        MainActivity a = new MainActivity();
//        float angle = 0.0f;
//        float strenth = 0.0f;
//        System.out.println(a.convertToDirectionCmd(angle, strenth));
//    }

}