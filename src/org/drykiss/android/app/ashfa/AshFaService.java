
package org.drykiss.android.app.ashfa;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class AshFaService extends Service {
    private static final String TAG = "AgiService";
    private static final boolean DEBUG_LOG = false;

    private static final int CURSOR_DISPLAY_TIMEOUT = 5000;
    ImageView mCursorView;
    WindowManager mWindowManager;
    WindowManager.LayoutParams mParams;
    Display mCurrentDisplay;
    final Handler mHandler = new Handler();
    Socket monkeySocket;
    PrintWriter monkeyWriter;
    BufferedReader monkeyReader;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        init();
        startServer();
        try {
            monkeySocket = new Socket("localhost", 12345);
            monkeyWriter = new PrintWriter(monkeySocket.getOutputStream(), true);
            monkeyReader = new BufferedReader(new InputStreamReader(
                    monkeySocket.getInputStream()));
            Log.i(TAG, "Success to connect monkey server!!!");
        } catch (UnknownHostException e) {
            Log.e(TAG, "Unknow host while connect monkey server!", e);
        } catch (IOException e) {
            Log.e(TAG, "IO error while connect monkey server!", e);
        }

        mHandler.postDelayed(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), "press HOME!!!",
                        Toast.LENGTH_LONG).show();
                press("KEYCODE_HOME", "down");
            }
        }, 3000);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                press("KEYCODE_HOME", "up");
            }
        }, 3100);
    }

    @Override
    public void onDestroy() {
        try {
            monkeyWriter.close();
            monkeyReader.close();
            monkeySocket.close();
            Log.e(TAG, "Closed connection to monkey server.");
        } catch (IOException e) {
            Log.e(TAG, "Fail to close connections!", e);
        }
    }

    private void init() {
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager = windowManager;

        ImageView cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.cursor_pressed);
        mCursorView = cursorView;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        mParams = params;
        windowManager.addView(mCursorView, params);
        hideCursor();

        mCurrentDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
    }

    private void hideCursor() {
        mHandler.post(new Runnable() {
            public void run() {
                mCursorView.setVisibility(View.INVISIBLE);
            }
        });
    }

    private int calcAxis(int value, boolean isXAxis) {
        int diff = 0;
        if (isXAxis) {
            diff = mCurrentDisplay.getWidth() / 2;
        } else {
            diff = mCurrentDisplay.getHeight() / 2;
        }
        return value - diff;
    }

    private void doShowCursor(final int x, final int y, final boolean isDown) {
        if (DEBUG_LOG) {
            Log.d(TAG, "show cursor at " + x + ", " + y + isDown);
        }
        mCursorView.setImageResource(isDown ? R.drawable.cursor_pressed
                : R.drawable.cursor);
        mCursorView.setVisibility(View.VISIBLE);
        mParams.x = calcAxis(x, true);
        mParams.y = calcAxis(y, false);
        mWindowManager.updateViewLayout(mCursorView, mParams);
        // This will remove all callbacks.
        mHandler.removeMessages(0);
        mHandler.postDelayed(new Runnable() {
            public void run() {
                hideCursor();
            }
        }, CURSOR_DISPLAY_TIMEOUT);
    }

    private void showCursor(final int x, final int y, final boolean isDown) {
        mHandler.post(new Runnable() {
            public void run() {
                doShowCursor(x, y, isDown);
            }
        });
    }

    private void press(final String keyCode, final String type) {
        if (monkeySocket.isConnected()) {
            String command = "press " + keyCode;
            // String command = "key " + type + " " + keyCode;
            Log.v(TAG, "do key control: " + command);
            monkeyWriter.println(command);
        }
    }

    private void touchScreen(final int x, final int y, final String type) {
        if (monkeySocket.isConnected()) {
            String command = "touch " + type + " " + x + " " + y;
            Log.v(TAG, "send command to monkey: " + command);
            monkeyWriter.println(command);
        } else {
            Log.i(TAG, "monkey is not connected!");
        }
    }

    private void startServer() {
        Thread thread = new HolyGrailServerThread();
        thread.start();
    }

    private class HolyGrailServerThread extends Thread {
        int PORT = 9991;
        ServerSocket mServerSocket;
        Socket mAcceptSocket;
        Socket mClientSocket;
        Thread mClientThread;

        public void run() {
            Log.i(TAG, "server thread start.");
            try {
                mServerSocket = new ServerSocket(PORT);
                mServerSocket.setReuseAddress(true);

                while (true) {
                    Log.d(TAG, "waiting...");
                    mAcceptSocket = mServerSocket.accept();
                    Log.d(TAG, "accepted! mClientSocket : " + mClientSocket
                            + ", mAcceptSocket : " + mAcceptSocket);

                    if (mClientSocket != null) {
                        Log.d(TAG, "Disconnect already existing connection.");
                        mClientSocket.close();
                    }
                    mClientSocket = mAcceptSocket;
                    mClientThread = new ClientThread();
                    mClientThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // HIDE
        // SHOW <x> <y> [pressed]
        // PRESS <DOWN | UP> <keycode>
        // TOUCH <x> <y> <type>
        // GETPROP <property name>
        // GETSYSTEMPROP <system property name>
        private class ClientThread extends Thread {
            static final int LENGTH_HEADER_SIZE = 3;
            static final int BUFFER_SIZE = 256;
            InputStreamReader mStreamFromClient;
            OutputStreamWriter mStreamToClient;
            BufferedReader mBufferFromClient;
            BufferedWriter mBufferToClient;

            public void run() {
                Log.d(TAG, "Client thread Start.");
                try {
                    if (mClientSocket == null) {
                        return;
                    }
                    mStreamFromClient = new InputStreamReader(
                            mClientSocket.getInputStream());
                    mStreamToClient = new OutputStreamWriter(
                            mClientSocket.getOutputStream());

                    char[] packetBuffer = new char[BUFFER_SIZE];
                    int receivedCount = 0;
                    String packet = null;

                    while (true) {
                        Log.d(TAG, "loop entry.");
                        mBufferFromClient = new BufferedReader(
                                mStreamFromClient);
                        mBufferToClient = new BufferedWriter(mStreamToClient);

                        receivedCount = mBufferFromClient.read(packetBuffer, 0,
                                LENGTH_HEADER_SIZE);
                        if (receivedCount < 0) {
                            Log.d(TAG,
                                    "Received packet size is -1."
                                            + " Maybe client disconnected unexpectedly.");
                            mClientSocket.close();
                            return;
                        }
                        packet = String.valueOf(packetBuffer, 0, receivedCount);
                        if (DEBUG_LOG) {
                            Log.d(TAG, "received : " + packet);
                        }

                        int length = 0;
                        try {
                            length = Integer.valueOf(packet);
                        } catch (Throwable e) {
                            Log.d(TAG, "Length header is wrong!", e);
                        }

                        receivedCount = mBufferFromClient.read(packetBuffer, 0,
                                length);
                        if (receivedCount < 0) {
                            Log.d(TAG,
                                    "Received packet size is -1."
                                            + " Maybe client disconnected unexpectedly.");
                            mClientSocket.close();
                            return;
                        }
                        packet = String.valueOf(packetBuffer, 0, receivedCount);
                        if (DEBUG_LOG) {
                            Log.d(TAG, "received : " + packet);
                        }

                        String[] splitted = packet.split(" ");
                        for (int i = 0; i < splitted.length; i++) {
                        }
                        if ("HIDE".equals(splitted[0])) {
                            hideCursor();
                        } else if ("SHOW".equals(splitted[0])) {
                            int x = Integer.valueOf(splitted[1]);
                            int y = Integer.valueOf(splitted[2]);
                            boolean isPressed = splitted.length >= 4
                                    && "pressed".equals(splitted[3]);
                            showCursor(x, y, isPressed);
                        } else if ("TOUCH".equals(splitted[0])) {
                            final int x = Integer.valueOf(splitted[1]);
                            final int y = Integer.valueOf(splitted[2]);
                            final String type = splitted[3];
                            touchScreen(x, y, type);
                        } else {
                            Log.e(TAG, "Invalid packet!" + packet + splitted);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException while run client thread loop!", e);
                }
            }
        }
    }
}
