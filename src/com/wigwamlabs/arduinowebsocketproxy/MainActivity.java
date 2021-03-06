package com.wigwamlabs.arduinowebsocketproxy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wigwamlabs.arduinowebsocketproxy.BridgeService.LocalBinder;

public class MainActivity extends Activity implements ServiceConnection, BridgeService.Callback {
    private static final int ACCESSORY_DISCONNECTED = 0;
    private static final int ACCESSORY_CONNECTED = 1;
    private static final int ACCESSORY_AVAILABLE = 2;
    protected BridgeService mService;
    private TextView mArduinoState;
    private TextView mAccessoryManufacturerLabel;
    private TextView mAccessoryManufacturer;
    private TextView mAccessoryModelLabel;
    private TextView mAccessoryModel;
    private TextView mAccessoryDescriptionLabel;
    private TextView mAccessoryDescription;
    private TextView mWebSocketState;
    private TextView mWebSocketServer;
    private TextView mWebSocketServerLabel;
    private TextView mWebSocketClient;
    private TextView mWebSocketClientLabel;
    private TextView mLogReadFromAccessory;
    private TextView mLogWriteToAccessory;
    private AccessoryDetector mAccessoryDetector;
    private int mAccessoryState = ACCESSORY_DISCONNECTED;
    private MenuItem mConnectMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Resources res = getResources();

        setContentView(R.layout.activity_main);

        mArduinoState = (TextView) findViewById(R.id.arduinoState);
        mAccessoryManufacturerLabel = (TextView) findViewById(R.id.accessoryManufacturerLabel);
        mAccessoryManufacturer = (TextView) findViewById(R.id.accessoryManufacturer);
        mAccessoryModelLabel = (TextView) findViewById(R.id.accessoryModelLabel);
        mAccessoryModel = (TextView) findViewById(R.id.accessoryModel);
        mAccessoryDescriptionLabel = (TextView) findViewById(R.id.accessoryDescriptionLabel);
        mAccessoryDescription = (TextView) findViewById(R.id.accessoryDescription);
        mWebSocketState = (TextView) findViewById(R.id.websocketState);
        mWebSocketServerLabel = (TextView) findViewById(R.id.websocketServerLabel);
        mWebSocketServer = (TextView) findViewById(R.id.websocketServer);
        mWebSocketClientLabel = (TextView) findViewById(R.id.websocketClientLabel);
        mWebSocketClient = (TextView) findViewById(R.id.websocketClient);
        mLogReadFromAccessory = (TextView) findViewById(R.id.logReadFromAccessory);
        scrollToBottom(mLogReadFromAccessory);
        mLogWriteToAccessory = (TextView) findViewById(R.id.logWriteToAccessory);
        scrollToBottom(mLogWriteToAccessory);

        if (res.getBoolean(R.bool.layoutLogsHorizontally)) {
            layoutLogsHorizontally(res);
        }

        // TODO move to service
        mAccessoryDetector = new AccessoryDetector(this);

        bindBridgeService();
    }

    private void layoutLogsHorizontally(Resources res) {
        final LinearLayout logContainer = (LinearLayout) findViewById(R.id.logContainer);
        logContainer.setOrientation(LinearLayout.HORIZONTAL);
        final int margin = res.getDimensionPixelOffset(R.dimen.margin_between_logs_horizontally);

        final int childCount = logContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = logContainer.getChildAt(i);
            final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            params.height = LayoutParams.MATCH_PARENT;
            params.width = 0;
            if (i > 0) {
                params.leftMargin = margin;
            }
            if (i < childCount - 1) {
                params.rightMargin = margin;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        openAccessoryIfNeeded(getAccessoryFromIntent());
    }

    private UsbAccessory getAccessoryFromIntent() {
        return (UsbAccessory) getIntent().getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
    }

    void openAccessoryIfNeeded(UsbAccessory accessory) {
        if (mService == null) {
            return;
        }

        if (accessory != null) {
            mService.openAccessory(accessory);

            // WebSocketClient.sendMessageAndWaitForAnswer("Hello from client");
        }
    }

    private void bindBridgeService() {
        final Intent intent = new Intent(this, BridgeService.class);
        startService(intent);
        bindService(intent, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        final LocalBinder binder = (LocalBinder) service;
        mService = binder.getService();

        mService.addCallback(this);

        openAccessoryIfNeeded(getAccessoryFromIntent());
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mService != null) {
            mService.addCallback(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mService != null) {
            mService.removeCallback(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mAccessoryDetector != null) {
            mAccessoryDetector.onDestroy();
        }

        unbindService(this);
        mService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mConnectMenuItem = menu.findItem(R.id.action_connect);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_connect:
            connectToAccessory();
            return true;
        case R.id.action_settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public void onAccessoryState(boolean connected, UsbAccessory accessory) {
        final Resources res = getResources();
        mArduinoState.setText(connected ? R.string.accessory_connected : R.string.accessory_disconnected);
        mArduinoState.setTextColor(res.getColor(connected ? R.color.green : R.color.red));
        mAccessoryDetector.setEnabled(!connected);

        if (connected) {
            mAccessoryState = ACCESSORY_CONNECTED;
        }
        if (!connected && mAccessoryState == ACCESSORY_CONNECTED) {
            mAccessoryState = ACCESSORY_DISCONNECTED;
        }
        updateAccessoryStateConnectVisibility(accessory);

        if (!connected) {
            if (mLogWriteToAccessory.getText().length() > 0) {
                appendToLog(mLogWriteToAccessory, "\n--------------------");
            }
            if (mLogReadFromAccessory.getText().length() > 0) {
                appendToLog(mLogReadFromAccessory, "\n--------------------");
            }
        }
    }

    public void onAccessoryAvailable(UsbAccessory accessory) {
        if (mAccessoryState != ACCESSORY_CONNECTED) {
            mAccessoryState = (accessory == null ? ACCESSORY_DISCONNECTED : ACCESSORY_AVAILABLE);
        }
        updateAccessoryStateConnectVisibility(accessory);
    }

    private void updateAccessoryStateConnectVisibility(UsbAccessory accessory) {
        if (mConnectMenuItem != null) {
            mConnectMenuItem.setVisible(mAccessoryState == ACCESSORY_AVAILABLE);
        }
        final int visibility = (getResources().getBoolean(R.bool.showAccessoryDetails) && accessory != null ? View.VISIBLE : View.GONE);
        if (visibility == View.VISIBLE) {
            mAccessoryManufacturer.setText(accessory.getManufacturer());
            mAccessoryModel.setText(accessory.getModel());
            mAccessoryDescription.setText(accessory.getDescription());
        }
        mAccessoryManufacturerLabel.setVisibility(visibility);
        mAccessoryManufacturer.setVisibility(visibility);
        mAccessoryModelLabel.setVisibility(visibility);
        mAccessoryModel.setVisibility(visibility);
        mAccessoryDescriptionLabel.setVisibility(visibility);
        mAccessoryDescription.setVisibility(visibility);
    }

    protected void connectToAccessory() {
        mAccessoryDetector.connect();
    }

    @Override
    public void onWebSocketState(boolean running, int port, int clients, String connectionInfo) {
        final Resources res = getResources();
        mWebSocketState.setText(running ? res.getQuantityString(R.plurals.websocket_running, clients, clients) : res.getString(R.string.websocket_stopped));
        mWebSocketState.setTextColor(res.getColor(running ? R.color.green : R.color.red));

        int visibility = View.GONE;
        if (running && res.getBoolean(R.bool.showWebSocketServer)) {
            final String ipAddress = NetworkUtils.getIPAddress();
            mWebSocketServer.setText(String.format("ws://%s:%d", ipAddress, port));
            if (ipAddress.length() > 0) {
                visibility = View.VISIBLE;
            }
        }
        mWebSocketServerLabel.setVisibility(visibility);
        mWebSocketServer.setVisibility(visibility);

        mWebSocketClient.setText(connectionInfo);
        visibility = (connectionInfo.length() > 0 && res.getBoolean(R.bool.showWebSocketClient) ? View.VISIBLE : View.GONE);
        mWebSocketClientLabel.setVisibility(visibility);
        mWebSocketClient.setVisibility(visibility);
    }

    @Override
    public void onReadFromAccessory(byte[] bytes) {
        updateLog(mLogReadFromAccessory, bytes);
    }

    @Override
    public void onWriteToAccessory(byte[] bytes) {
        updateLog(mLogWriteToAccessory, bytes);
    }

    private void updateLog(final TextView log, byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        final CharSequence existingText = log.getText();
        if (existingText.length() > 0) {
            if (existingText.charAt(existingText.length() - 1) == '-') {
                sb.append('\n');
            } else {
                sb.append(' ');
            }
        }
        boolean first = true;
        for (final byte b : bytes) {
            if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            sb.append(String.format("%02x", b));
        }
        appendToLog(log, sb.toString());
    }

    private void appendToLog(final TextView log, final String text) {
        log.append(text);
        scrollToBottom(log);
    }

    private void scrollToBottom(final TextView log) {
        final ScrollView scroller = (ScrollView) log.getParent();
        scroller.post(new Runnable() {
            @Override
            public void run() {
                scroller.smoothScrollTo(0, log.getBottom());
            }
        });
    }
}
