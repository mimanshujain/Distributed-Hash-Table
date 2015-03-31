package edu.buffalo.cse.cse486586.simpledht;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import java.io.IOException;
import java.net.ServerSocket;

import edu.buffalo.cse.cse486586.simpledht.SimpleDhtProvider.*;

public class SimpleDhtActivity extends Activity {
    static String myPort = "";
    static final int SERVER_PORT = 10000;
    static final String TAG = SimpleDhtActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));

        SimpleDhtProvider.startUp(myPort);
        SimpleDhtProvider obj1 = new SimpleDhtProvider();
        try
        {
            ServerSocket socket = new ServerSocket(SERVER_PORT);
            obj1.new ServerTask().executeOnExecutor(SimpleDhtProvider.myPool, socket);
        }
        catch (IOException ex)
        {
            Log.v(TAG, "Server Socket creation Error");
        }
        if(!myPort.equals("11108")) {
            String[] message = new String[2];
            message[0] = "JoinMaster";
            message[1] = myPort;
            ClientTask obj = new ClientTask();
            obj.executeOnExecutor(SimpleDhtProvider.myPool, message);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

}
