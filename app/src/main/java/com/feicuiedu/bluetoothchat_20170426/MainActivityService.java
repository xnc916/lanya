package com.feicuiedu.bluetoothchat_20170426;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by gqq on 2017/4/26.
 */

// 主要管理一下连接和通讯相关
public class MainActivityService {

    private static final String SOCKET_NAME = "MainActivityService";
    // 普遍的格式：8-4-4-4-12的类型
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a89");

    private int mState;

    public static final int STATE_NONE = 0;// 无连接，不可以做任何操作
    public static final int STATE_LISTEN = 1;// 等待连接的状态
    public static final int STATE_CONNECTING = 2;// 正在建立一个连接
    public static final int STATE_CONNECTED = 3;// 连接到一个远程设备

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    public MainActivityService(Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    // 获取当前的连接状态
    public synchronized int getState() {
        return mState;
    }

    // 设置当前的连接状态
    public synchronized void setState(int state) {
        mState = state;
        mHandler.sendMessage(Message.obtain(mHandler,MainActivity.MESSAGE_STATE_CHANGE,state,-1));
    }

    // 重新开始监听连接
    public synchronized void start() {
        setState(STATE_LISTEN);

        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    // 管理连接
    private synchronized void manageConnectedSocket(BluetoothSocket socket, BluetoothDevice device) {
        // 连接之后开启管理连接的线程处理通讯
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // 可以将连接的信息传递到主线程
        Message message = Message.obtain(mHandler, MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        message.setData(bundle);
        mHandler.sendMessage(message);

        setState(STATE_CONNECTED);
    }

    // 连接失败
    private void connectionFailed() {

        // 连接失败了，UI上展示
        Message message = Message.obtain(mHandler, MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "不能连接到设备");
        message.setData(bundle);
        mHandler.sendMessage(message);

        // 重新建立连接
        MainActivityService.this.start();
    }

    // 连接中断
    private void connectionLost() {
        // 连接中断了，UI上展示
        Message message = Message.obtain(mHandler, MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "连接到设备中断");
        message.setData(bundle);
        mHandler.sendMessage(message);

        // 重新建立连接
        MainActivityService.this.start();
    }

    // 设备连接
    public void connect(BluetoothDevice device) {
        // 开启一个去连接的线程(客户端线程)
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    // 发送消息
    public void write(byte[] bytes) {
        ConnectedThread r;
        synchronized (this){
            if (mState!=STATE_CONNECTED) return;
            r= mConnectedThread;
        }
        r.write(bytes);
    }

    // 1. 服务端的线程
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // 使用一个临时的对象拿到BluetoothServerSocket
            BluetoothServerSocket tmp = null;
            try {
                // 建立服务端的Socket
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SOCKET_NAME, MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // 一直处于阻塞状态等待连接
            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // 如果有连接的客户端
                if (socket != null) {

                    synchronized (MainActivityService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // 去连接，连接之后，做一些通讯等的操作，可以根据当前的连接状态
                                manageConnectedSocket(socket,socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    mmServerSocket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                }
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // 2. 客户端：去连接
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // 创建一个临时对象拿到客户端Socket
            BluetoothSocket tmp = null;
            mmDevice = device;

            // 利用要连接的设备得到一个客户端Socket
            try {
                // UUID要和服务端的一致
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {

            // 要连接了，就不让再去扫描其他设备了
            mBluetoothAdapter.cancelDiscovery();

            try {
                // 主动去连接到其他设备
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }

                // 可不可以处理一下连接失败等情况
                connectionFailed();
                return;
            }

            // 连接之后处理通讯
            manageConnectedSocket(mmSocket,mmDevice);
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }


    // 管理连接的线程：主要进行的是利用IO流进行通讯
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // 创建的临时对象为了拿到输入输出流
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // 如果连接的状态，一直读取消息
            while (true) {
                try {
                    // 读取传递的信息
                    bytes = mmInStream.read(buffer);
                    // 拿到消息之后可以在UI上有所展示
                    mHandler.sendMessage(Message.obtain(mHandler, MainActivity.MESSAGE_READ, bytes, -1, buffer));

                } catch (IOException e) {
                    // 连接中断了
                    // 处理一下连接中断的情况
                    connectionLost();
                    break;
                }
            }
        }

        // 可以通过write方法发送消息
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                // 在UI上有所展示
                mHandler.sendMessage(Message.obtain(mHandler, MainActivity.MESSAGE_WRITE, -1, -1, bytes));
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
