package com.denisigo.bluetoothexample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.UUID;

/**
 *
 */
public class BluetoothServer {

    private static final String TAG = BluetoothServer.class.getSimpleName();

    // Arbitrary name for our SPP service
    private static final String SERVICE_NAME = "TestService";
    // 128-bit UUID combined of SPP short UUID 0x1101 and base UUID 00000000-0000-1000-8000-00805F9B34FB
    public static final String SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private MessagesHandler mHandler;
    private static final int MSG_BT_ONCONNECTED = 1;
    private static final int MSG_BT_ONERROR = 2;
    private static final int MSG_BT_ONOPENED = 3;
    private static final int MSG_BT_ONDATA = 4;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private OutputStream mOutputStream;
    private Thread mWorkingThread;

    private IBluetoothServerListener mListener;

    /**
     * Bluetooth server events listener interface
     */
    public interface IBluetoothServerListener{
        public void onStarted();
        public void onConnected();
        public void onData(byte[] data);
        public void onError(String message);
        public void onStopped();
    }

    public static class BluetoothServerException extends Exception{
        public BluetoothServerException(String message){
            super(message);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    public void setListener(IBluetoothServerListener listener){
        mListener = listener;
    }

    /**
     * Sends bytes to the client
     * @param data bytes array
     * @throws BluetoothServerException
     * @throws IOException
     */
    public void send(byte[] data) throws BluetoothServerException, IOException {
        if (mOutputStream == null)
            throw new BluetoothServerException("Server is not started properly");

        mOutputStream.write(data, 0, data.length);
    }

    /**
     * Stops Bluetooth server.
     */
    public void stop(){
        if (mWorkingThread != null)
            mWorkingThread.interrupt();
        mWorkingThread = null;

        if (mSocket != null)
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        mBluetoothAdapter = null;
        mHandler = null;

        if (mListener != null)
            mListener.onStopped();
    }

    /**
     * Starts Bluetooth server. If started successfully, it will be waiting for client connection
     * @throws BluetoothServerException
     */
    public void start() throws BluetoothServerException {

        // We use handler to handle messages from working thread
        mHandler = new MessagesHandler(this);

        // Attempt to get Bluetooth adapter, if it is null, device doesn't support Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null)
            throw new BluetoothServerException("Device does not support Bluetooth");

        // Bluetooth might be disabled, check it
        if (!mBluetoothAdapter.isEnabled()) {
            // You could use something like this to ask user to enable Bluetooth
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivity(enableBtIntent);

            throw new BluetoothServerException("Bluetooth is disabled");
        }

        // Create working thread and go on
        mWorkingThread = new WorkingThread();
        mWorkingThread.start();
    }


    private void onOpened(){
        if (mListener != null)
            mListener.onStarted();
    }

    private void onConnected(BluetoothSocket socket){
        mSocket = socket;
        try {
            mOutputStream = mSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            onError("Unable to get socket OutputStream: " + e);
        }

        if (mListener != null)
            mListener.onConnected();
    }

    private void onError(String message){
        stop();

        if (mListener != null)
            mListener.onError(message);
    }

    private void onData(byte[] data){
        if (mListener != null)
            mListener.onData(data);
    }

    /**
     * Messages handler used to handle messages from WorkingThread. Uses weak reference to avoid
     * memory leaks.
     */
    private static class MessagesHandler extends Handler{
        private WeakReference<BluetoothServer> mBluetoothServer;

        public MessagesHandler(BluetoothServer bluetoothServer){
            mBluetoothServer = new WeakReference<>(bluetoothServer);
        }

        @Override
        public void handleMessage(Message message) {

            BluetoothServer bluetoothServer = mBluetoothServer.get();
            if (bluetoothServer == null)
                return;

            switch (message.what){
                case MSG_BT_ONOPENED:
                    bluetoothServer.onOpened();
                    break;

                case MSG_BT_ONCONNECTED:
                    bluetoothServer.onConnected((BluetoothSocket) message.obj);
                    break;

                case MSG_BT_ONERROR:
                    bluetoothServer.onError((String) message.obj);
                    break;

                case MSG_BT_ONDATA:
                    bluetoothServer.onData((byte[]) message.obj);
                    break;
            }
        }
    }

    /**
     * Thread used to create server socket, listen for connections and ultimately read InputStream
     * of the socket.
     */
    private class WorkingThread extends Thread{

        /**
         * Helper method to send error message
         * @param message String to report
         */
        private void reportError(String message){
            Log.e(TAG, message);
            mHandler.obtainMessage(MSG_BT_ONERROR, message).sendToTarget();
        }

        @Override
        public void run() {
            BluetoothSocket bluetoothSocket;
            BluetoothServerSocket serverSocket;

            // Attempt to create server socket using RFCOMM service
            try {
                serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME, UUID.fromString(SERVICE_UUID));
            } catch (IOException e) {
                reportError("Error creating bluetooth server socket: " + e);
                return;
            }

            Log.i(TAG, "Server socket created");
            mHandler.obtainMessage(MSG_BT_ONOPENED).sendToTarget();

            // Waiting for client to connect (blocking until connected)
            try {
                bluetoothSocket = serverSocket.accept();
            } catch (IOException e) {
                reportError("Error accepting bluetooth connection: " + e);
                try {
                    serverSocket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return;
            }

            // Now we're connected!
            mHandler.obtainMessage(MSG_BT_ONCONNECTED, bluetoothSocket).sendToTarget();

            // Close server socket (we don't need more connections)
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Start reading input stream
            InputStream inputStream;
            try {
                inputStream = bluetoothSocket.getInputStream();
            } catch (IOException e) {
                reportError("Unable to get socket InputStream: " + e);
                return;
            }

            int bytesRead;
            byte[] buffer = new byte[1024];

            while (!Thread.interrupted()) {
                try {
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead != -1) {
                        byte[] message = new byte[bytesRead];
                        System.arraycopy(buffer, 0, message, 0, bytesRead);
                        mHandler.obtainMessage(MSG_BT_ONDATA, message).sendToTarget();
                    } else {
                        reportError("Socket InputStream has no more data (-1)");
                        return;
                    }
                } catch( SocketTimeoutException e ) {
                    reportError("SocketTimeoutException: " + e);
                    break;
                } catch (IOException e) {
                    reportError("IOException: " + e);
                    break;
                }
            }

            Log.d(TAG, "Working thread stopped");
        }
    }
}
