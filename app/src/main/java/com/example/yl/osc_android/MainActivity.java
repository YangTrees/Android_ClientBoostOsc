package com.example.yl.osc_android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

public class MainActivity extends Activity {

    /* These two variables hold the IP address and port number.
   * You should change them to the appropriate address and port.
   */
    private String myIP ;
    private int myPort ;
    private ImageView iView;
    public float currentX;
    public float currentY;
    public boolean bTouched;
    private float screenWidth;
    private float screenHeight;


    // This is used to send messages
    private OSCPortOut oscPortOut;

    // This thread will contain all the code that pertains to OSC
    private Thread oscSenderThread = new Thread() {
        @Override
        public void run() {
      /* The first part of the run() method initializes the OSCPortOut for sending messages.
       *
       * For more advanced apps, where you want to change the address during runtime, you will want
       * to have this section in a different thread, but since we won't be changing addresses here,
       * we only have to initialize the address once.
       */

            try {
                // Connect to some IP address and port
                oscPortOut = new OSCPortOut(InetAddress.getByName(myIP), myPort);
            } catch(UnknownHostException e) {
                // Error handling when your IP isn't found
                return;
            } catch(Exception e) {
                // Error handling for any other errors
                return;
            }


      /* The second part of the run() method loops infinitely and sends messages every 500
       * milliseconds.
       */
            while (true) {
                if (oscPortOut != null && bTouched) {
                    bTouched=false;
                    // Creating the message
                    Object[] thingsToSend = new Object[2];
                    //thingsToSend[0] = "Hello World";
                    thingsToSend[0] = currentX/screenWidth;
                    thingsToSend[1] = currentY/screenHeight;


          /* The version of JavaOSC from the Maven Repository is slightly different from the one
           * from the download link on the main website at the time of writing this tutorial.
           *
           * The Maven Repository version (used here), takes a Collection, which is why we need
           * Arrays.asList(thingsToSend).
           *
           * If you're using the downloadable version for some reason, you should switch the
           * commented and uncommented lines for message below
           */
                    String addressStr = "/obj/position";
                    OSCMessage message = new OSCMessage(addressStr, Arrays.asList(thingsToSend));
                    // OSCMessage message = new OSCMessage(myIP, thingsToSend);


          /* NOTE: Since this version of JavaOSC uses Collections, we can actually use ArrayLists,
           * or any other class that implements the Collection interface. The following code is
           * valid for this version.
           *
           * The benefit of using an ArrayList is that you don't have to know how much information
           * you are sending ahead of time. You can add things to the end of an ArrayList, but not
           * to an Array.
           *
           * If you want to use this code with the downloadable version, you should switch the
           * commented and uncommented lines for message2
           */
            /*
                    ArrayList<Object> moreThingsToSend = new ArrayList<Object>();
                    moreThingsToSend.add("Hello World2");
                    moreThingsToSend.add(123456);
                    moreThingsToSend.add(12.345);

                    OSCMessage message2 = new OSCMessage(myIP, moreThingsToSend);
            */
                    //OSCMessage message2 = new OSCMessage(myIP, moreThingsToSend.toArray());

                    try {
                        // Send the messages
                        oscPortOut.send(message);
                        //oscPortOut.send(message2);

                        // Pause for half a second
                        sleep(10);
                    } catch (Exception e) {
                        // Error handling for some error
                    }
                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获取屏幕
        WindowManager wm = this.getWindowManager();

        screenWidth = wm.getDefaultDisplay().getWidth();
        screenHeight = wm.getDefaultDisplay().getHeight();

        // 获取IP地址
        Intent intent = getIntent();
        Bundle data = intent.getExtras();
        myIP = data.getString("ipname");
        myPort = data.getInt("portname");


        oscSenderThread.start();

        iView = (ImageView)findViewById(R.id.iView);

        iView.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event){
                bTouched = true;
                currentX = event.getX();
                currentY = event.getY();

                TextView txtView = (TextView)findViewById(R.id.txtView);
                txtView.setText(Float.toString(currentX)+","+Float.toString(currentY));

                return true;
            }
        });

        //启用线程接收并更新图像数据
        Thread th = new MyReceiveThread(iView,myIP,myPort);
        th.start();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

class MyReceiveThread extends Thread{
    public static byte imageByte[];
    private InputStream in;
    private String ipname;
    private  int portnum;
    private ImageView iView;
    private Handler handler;

    public MyReceiveThread(ImageView iView,String ipname,int portnum){

        this.ipname = ipname;
        this.iView = iView;
        this.portnum = portnum;
    }

    public void run() {

        while (true) {
            Looper looper = Looper.getMainLooper(); //主线程的Looper对象
            handler = new MyHandler(looper);        //这里以主线程的Looper对象创建了handler，所以，这个handler发送的Message会被传递给主线程的MessageQueue。

            try {
                //将图像数据通过Socket接收
                int imageSize = 230400;//expected image size 320X240X3
                Socket tempSocket = new Socket(ipname, portnum);

                in = tempSocket.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte buffer[] = new byte[320 * 3];
                int remainingBytes = imageSize; //

                while (remainingBytes > 0) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead < 0) {
                        throw new IOException("Unexpected end of data");
                    }
                    baos.write(buffer, 0, bytesRead);
                    remainingBytes -= bytesRead;
                }

                in.close();
                imageByte = baos.toByteArray();
                baos.close();
                tempSocket.close();//关闭socket，一次连接传一幅图像            int nrOfPixels = imageByte.length / 3; // Three bytes per pixel.

                int pixels[] = new int[imageSize];
                for (int i = 0; i < 76800; i++) {
                    int r = imageByte[3 * i];
                    int g = imageByte[3 * i + 1];
                    int b = imageByte[3 * i + 2];

                    if (r < 0)
                        r = r + 256; //Convert to positive
                    if (g < 0)
                        g = g + 256; //Convert to positive
                    if (b < 0)
                        b = b + 256; //Convert to positive

                    pixels[i] = Color.rgb(b, g, r);
                }

                Bitmap bitmap = Bitmap.createBitmap(pixels, 320, 240, Bitmap.Config.ARGB_8888);

                Message msg = handler.obtainMessage();//构建Message对象
                msg.obj = bitmap;
                handler.sendMessage(msg);//图像已经转换好了，发送消息给UI线程更新图像
                // tempSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class MyHandler extends Handler{

        public MyHandler(Looper looper){
            super(looper);
        }
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            iView.setImageBitmap((Bitmap) msg.obj);//主线程的Handler对象，收到消息
        }

    }
}
