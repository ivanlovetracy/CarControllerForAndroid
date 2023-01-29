package com.example.myapplication;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    private MjpegView view1;
    private DatagramSocket socket;
    private Button connectBtn;
    private Button stopConnectBtn;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        connectBtn = (Button) findViewById(R.id.connectbtn);
        stopConnectBtn = (Button) findViewById(R.id.stopconnectbtn);
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
                Log.i("stick","angle:"+angle+" strength:"+strength);
            }
        });

        JoyStickView joyStickView2 = findViewById(R.id.joy2);
        joyStickView2.setInnerCircleImageResId(R.drawable.ic_directions_run_black_24dp);
        joyStickView2.setInnerCircleColor(Color.BLACK);
        joyStickView2.setOnMoveListener(new JoyStickView.OnMoveListener() {
            @Override
            public void onMove(double angle, float strength) {
                Log.i("stick","angle:"+angle+" strength:"+strength);
            }
        });

        view1 = findViewById(R.id.mjpegview1);
        view1.setAdjustHeight(true);
//        view1.setAdjustWidth(true);
//        view1.setMode(MjpegView.MODE_FIT_WIDTH);
        view1.setMode(MjpegView.MODE_FIT_HEIGHT);
        //view.setMsecWaitAfterReadImageError(1000);

//      view1.setRecycleBitmap(true);

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        //在Android通信协议必须要放在线程里面进行
                        serverIp = ipInupt.getText().toString();
                        camPort = Integer.parseInt(camPortInput.getText().toString());
                        cmdPort = Integer.parseInt(cmdPortInput.getText().toString());
                        mjpegUrl = "http://"+serverIp+":"+camPort+"/stream";
                        Log.i("stick","mjpegUrl="+mjpegUrl);

                        view1.setUrl(mjpegUrl);
                        view1.startStream();
                        ismjpegOn = true;

                        Log.i("stick","mjpegView start");
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

        laserBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        sendMsg("laserOn");

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

    @Override
    protected void onResume() {
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
                // 20020是本机的端口号
                socket = new DatagramSocket();
            }
            //将字符串转换成字节流，因为底层的传输都是字节传输
            byte data[] = msg.getBytes();
            // 对方的IP和端口号
            DatagramPacket pack = new DatagramPacket(data, data.length, InetAddress.getByName(serverIp), cmdPort); // 创建DatagramPacket 对象数据包，这里的port是我们要发送信息主机的端口号
            Log.v("stick", "发送"+serverIp+":"+cmdPort+"--"+msg);
            socket.send(pack);
            Log.v("stick", "发送成功！");
        } catch (Exception e) {
            Log.v("stick", "发送失败！");
            e.printStackTrace();
        }
    }


}