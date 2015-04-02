package com.denisigo.bluetoothexample;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.IOException;


public class MainActivity extends ActionBarActivity {

    private EditText mInputView;
    private EditText mOutputView;
    private Button mStartButton;
    private Button mStopButton;
    private Button mSendButton;

    private BluetoothServer mBluetoothServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInputView = (EditText) findViewById(R.id.input);
        mOutputView = (EditText) findViewById(R.id.output);
        mStartButton = (Button) findViewById(R.id.startButton);
        mStopButton = (Button) findViewById(R.id.stopButton);
        mSendButton = (Button) findViewById(R.id.sendButton);

        mBluetoothServer = new BluetoothServer();
        mBluetoothServer.setListener(mBluetoothServerListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBluetoothServer.stop();
        mBluetoothServer = null;
    }

    /**
     * Bluetooth server events listener.
     */
    private BluetoothServer.IBluetoothServerListener mBluetoothServerListener =
            new BluetoothServer.IBluetoothServerListener() {
         @Override
        public void onStarted() {
            writeMessage("*** Server has started, waiting for client connection ***");
             mStopButton.setEnabled(true);
             mStartButton.setEnabled(false);
        }

        @Override
        public void onConnected() {
            writeMessage("*** Client has connected ***");
            mSendButton.setEnabled(true);
        }

        @Override
        public void onData(byte[] data) {
            writeMessage(new String(data));
        }

        @Override
        public void onError(String message) {
            writeError(message);
        }

        @Override
        public void onStopped() {
            writeMessage("*** Server has stopped ***");
            mSendButton.setEnabled(false);
            mStopButton.setEnabled(false);
            mStartButton.setEnabled(true);
        }
    };

    public void onStartClick(View view){
        try {
            mBluetoothServer.start();
        } catch (BluetoothServer.BluetoothServerException e) {
            e.printStackTrace();
            writeError(e.getMessage());
        }
    }

    public void onStopClick(View view){
        mBluetoothServer.stop();
    }

    public void onSendClick(View view){
        try {
            mBluetoothServer.send(mOutputView.getText().toString().getBytes());
            mOutputView.setText("");
        } catch (BluetoothServer.BluetoothServerException e) {
            e.printStackTrace();
            writeError(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            writeError(e.getMessage());
        }
    }


    private void writeMessage(String message){
        mInputView.setText(message + "\r\n" + mInputView.getText().toString());
    }

    private void writeError(String message){
        writeMessage("ERROR: " + message);
    }
}
