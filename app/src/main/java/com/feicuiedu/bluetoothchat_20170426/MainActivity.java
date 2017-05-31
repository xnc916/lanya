package com.feicuiedu.bluetoothchat_20170426;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 3;
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_WRITE = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final String DEVICE_NAME = "device_name";
    public static final int MESSAGE_TOAST = 5;
    public static final String TOAST = "toast";
    public static final int MESSAGE_STATE_CHANGE = 6;
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> mDevicesArrayAdapter;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private MainActivityService mChatService;
    private EditText mEtMessage;
    private Button mBtnConnect;
    private String mConnectedDeviceName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 获取蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // 适配器为空，不支持蓝牙
            Toast.makeText(this, "不支持蓝牙设备", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. 开启蓝牙：判断是否已经开启
        if (!mBluetoothAdapter.isEnabled()) {
            // 提示打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        } else {
            // 视图的初始化
            setupChat();
        }

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);// 找到
        registerReceiver(mReceiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);// 扫描结束
        registerReceiver(mReceiver, filter);

    }

    // 初始化视图相关
    private void setupChat() {

        // 设备列表视图
        mDevicesArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        ListView newDevicesListView = (ListView) findViewById(R.id.devicesList);
        newDevicesListView.setAdapter(mDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 将本地的蓝牙和点击的蓝牙建立连接
                mBluetoothAdapter.cancelDiscovery();

                // 拿到连接的设备：Item展示的是地址
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                // 根据地址找到设备连接
                connectDevices(address);
            }
        });

        // 聊天的列表
        mConversationArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        ListView chatList = (ListView) findViewById(R.id.listchat);
        chatList.setAdapter(mConversationArrayAdapter);

        // 输入框消息
        mEtMessage = (EditText) findViewById(R.id.edit_text_out);

        // 3. 扫描发现设备并打开本地蓝牙的可检测性
        mBtnConnect = (Button) findViewById(R.id.btnConnect);
        mBtnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mDevicesArrayAdapter.clear();

                // 判断是否可被检测到，不是，打开可检测性
                if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {

                    // 打开可检测性
                    Intent discoverableIntent = new
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    // 可被检测时长300s
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    startActivity(discoverableIntent);
                }

                // 扫描，如果在扫描，先取消，然后再开始
                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                mBluetoothAdapter.startDiscovery();

            }
        });

        // 发送消息
        Button btnSend = (Button) findViewById(R.id.button_send);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //发送消息建立通讯
                String message = mEtMessage.getText().toString();
                sendMessage(message);

            }
        });

        mChatService = new MainActivityService(mHandler);
    }

    // 发送消息
    private void sendMessage(String message) {
        if (mChatService.getState() != MainActivityService.STATE_CONNECTED) {
            //不是在连接的状态
            Toast.makeText(this, "设备未连接，不可通讯", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TextUtils.isEmpty(message)) {
            byte[] bytes = message.getBytes();
            mChatService.write(bytes);
            mEtMessage.setText(null);
        }
    }

    // 连接设备
    private void connectDevices(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device);
    }

    // 广播接收器：接收搜索到的蓝牙设备
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 判断是不是"发现"的Action
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 拿到得到的蓝牙设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 可以将蓝牙设备在视图上展示
                mDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 判断：如果当前没有设备，可以提示一下
                if (mDevicesArrayAdapter.getCount() == 0) {
                    Toast.makeText(context, "设备未找到", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                setupChat();
                break;
        }
    }

    // 处理视图信息的展示
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case MainActivityService.STATE_CONNECTED:
                            mBtnConnect.setText(String.format("连接到%s", mConnectedDeviceName));
                            break;
                        case MainActivityService.STATE_CONNECTING:
                            mBtnConnect.setText("连接中");
                            break;
                        case MainActivityService.STATE_NONE:
                        case MainActivityService.STATE_LISTEN:
                            mBtnConnect.setText("设备未连接");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBytes = (byte[]) msg.obj;
                    String message = new String(writeBytes);
                    // 展示到聊天的列表上
                    mConversationArrayAdapter.add("Me:" + message);
                    Toast.makeText(MainActivity.this, "Me：" + message, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_READ:
                    byte[] read = (byte[]) msg.obj;
                    String readMsg = new String(read);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":" + readMsg);
                    Toast.makeText(MainActivity.this, mConnectedDeviceName + ":" + readMsg, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "连接到：" + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
}
