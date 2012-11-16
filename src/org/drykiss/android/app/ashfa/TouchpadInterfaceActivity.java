
package org.drykiss.android.app.ashfa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class TouchpadInterfaceActivity extends Activity {
    private static final String TAG = "AshFA_touchpadinterface";
    private static final int ASH_DEVMGR_MON_PORT = 10101;
    private static final String END_OF_MSG = "end_of_expr";

    private Socket mDevmgrMonSocket = null;
    private InputStreamReader mInputStream = null;
    private OutputStreamWriter mOutputStream = null;
    private BufferedReader mBufferedReader = null;
    private BufferedWriter mBufferedWriter = null;
    private boolean mConnected = false;
    private String mPeerAddress = "";

    PowerManager.WakeLock mWakeLock = null;
    WifiManager.WifiLock mWifiLock = null;

    private TextView mConnectionStateTextView = null;

    private View.OnClickListener mConnectionSettingListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    TouchpadInterfaceActivity.this);
            final EditText peerAshAddrEditText = new EditText(
                    TouchpadInterfaceActivity.this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            peerAshAddrEditText.setLayoutParams(lp);
            builder.setView(peerAshAddrEditText)
                    // Add action buttons
                    .setPositiveButton(R.string.connect,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    connectAshDevmgrMon(peerAshAddrEditText
                                            .getText().toString());
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dialog.cancel();
                                }
                            });
            builder.create().show();
        }
    };

    private void sendCmdToPeer(String cmd) {
        if (mConnected) {
            try {
                mBufferedWriter.write(cmd);
                mBufferedWriter.flush();
            } catch (IOException e) {
                mConnected = false;
                Log.d(TAG, "Failed to send cmd!", e);
                updateConnectionState();
            }
        }
    }

    float mLastX = 50;
    float mLastY = 50;
    float mStartX = 50;
    float mStartY = 50;
    float mCurrentX = 50;
    float mCurrentY = 50;

    private View.OnTouchListener mTouchpadListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // Log.d(TAG, "Move mouse to  " + event.getX() * 100 / v.getWidth()
            // + ", "
            // + event.getY() * 100 / v.getHeight());

            if (mConnected) {
                final int action = event.getAction() & MotionEvent.ACTION_MASK;

                float x = event.getX() * 100 / v.getWidth();
                float y = event.getY() * 100 / v.getHeight();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        mStartX = x;
                        mStartY = y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = x - mStartX;
                        float deltaY = y - mStartY;

                        mCurrentX = mLastX + deltaX;
                        mCurrentY = mLastY + deltaY;
                        if (mCurrentX < 0) {
                            mCurrentX = 0;
                        } else if (mCurrentX > 100) {
                            mCurrentX = 100;
                        }
                        if (mCurrentY < 0) {
                            mCurrentY = 0;
                        } else if (mCurrentY > 100) {
                            mCurrentY = 100;
                        }
                        String command = "move_mouse " + mCurrentX + " " + mCurrentY
                                + " True True " + END_OF_MSG;
                        if (event.getPointerCount() > 1) {
                            command = "wheel_mouse " + deltaY + " True" + END_OF_MSG;
                        }
                        sendCmdToPeer(command);
                        return true;
                    case MotionEvent.ACTION_UP:
                        mLastX = mCurrentX;
                        mLastY = mCurrentY;
                }
            }
            return false;
        }
    };
    private View.OnTouchListener mLeftButtonListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // Log.d(TAG, "left mouse");
            if (mConnected) {
                String cmd = "";
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    cmd = "press_mouse";
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    cmd = "release_mouse";
                } else {
                    return false;
                }
                cmd += " False True" + END_OF_MSG;
                sendCmdToPeer(cmd);
                return true;
            }
            return false;
        }
    };
    private View.OnTouchListener mRightButtonListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // Log.d(TAG, "right mouse");
            if (mConnected) {
                String cmd = "";
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    cmd = "press_mouse";
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    cmd = "release_mouse";
                } else {
                    return false;
                }
                cmd += " True True" + END_OF_MSG;
                sendCmdToPeer(cmd);
                return true;
            }
            return false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.touchpad_interface);

        mConnectionStateTextView = (TextView) findViewById(R.id.connectionStateTextView);
        updateConnectionState();

        findViewById(R.id.connectionSettingButton).setOnClickListener(
                mConnectionSettingListener);

        findViewById(R.id.touchPadLayout).setOnTouchListener(mTouchpadListener);
        findViewById(R.id.leftClickButton).setOnTouchListener(
                mLeftButtonListener);
        findViewById(R.id.rightClickButton).setOnTouchListener(
                mRightButtonListener);

        if (mWakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }

        if (mWifiLock == null) {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            mWifiLock = wifiManager.createWifiLock(TAG);
            mWifiLock.setReferenceCounted(true);
            mWifiLock.acquire();
        }
    }

    @Override
    public void onDestroy() {
        if (mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        super.onDestroy();

    }

    private void updateConnectionState() {
        String state = getString(R.string.not_connected);
        if (mConnected) {
            state = getString(R.string.connected_to) + "mPeerAddress";
        }
        mConnectionStateTextView.setText(state);
    }

    private void connectAshDevmgrMon(final String address) {
        try {
            mDevmgrMonSocket = new Socket(address, ASH_DEVMGR_MON_PORT);
            mInputStream = new InputStreamReader(
                    mDevmgrMonSocket.getInputStream());
            mOutputStream = new OutputStreamWriter(
                    mDevmgrMonSocket.getOutputStream());
            mBufferedReader = new BufferedReader(mInputStream);
            mBufferedWriter = new BufferedWriter(mOutputStream);
            mConnected = true;
            mPeerAddress = address;
        } catch (IOException e) {
            Log.d(TAG, "Fail to connect!", e);
            mConnected = false;
        }
        updateConnectionState();
    }
}
