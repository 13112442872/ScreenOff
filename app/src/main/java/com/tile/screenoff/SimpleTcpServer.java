package com.tile.screenoff;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class SimpleTcpServer {

    public interface TcpConnectionListener {
        void onReceive(byte[] data);
        void onResponseSent();
    }
    private ServerSocket serverSocket;
    private static final int CAPACITY = 1024 * 1024;

    private final TcpConnectionListener listener;
    private BufferedInputStream in;
    private OutputStream out;
    private volatile boolean isRunning = false;
    private volatile boolean isRestarting = false;
    private final int port;

    public SimpleTcpServer(TcpConnectionListener listener, int port) {
        this.listener = listener;
        this.port = port;
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (serverSocket == null || isRunning) {
            return;
        }

        isRunning = true;
        new Thread(() -> {
            while (isRunning && !isRestarting) {
                try {
                    Socket socket = serverSocket.accept();
                    if (!isRunning) break;

                    in = new BufferedInputStream(socket.getInputStream());
                    out = new BufferedOutputStream(socket.getOutputStream());
                    startInputThread();
                } catch (IOException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }).start();
    }

    private void startInputThread() {
        new Thread(() -> {
            try {
                while(isRunning && !isRestarting) {
                    byte[] buf = new byte[CAPACITY];
                    if (in == null || !isRunning) {
                        break;
                    }
                    int size = in.read(buf);
                    if (size > 0) {
                        byte[] chunk = Arrays.copyOfRange(buf, 0, size);
                        listener.onReceive(chunk);
                    } else {
                        // 客户端断开连接，等待新连接
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void output(String data) {
        output(data.getBytes());
    }

    public void output(final byte[] data) {
        new Thread(() -> {
            if (out != null && isRunning && !isRestarting) {
                try {
                    synchronized (this) {
                        out.write(data);
                        out.flush();
                    }
                    listener.onResponseSent();
                } catch (IOException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (in != null) {
                in.close();
                in = null;
            }
            if (out != null) {
                out.close();
                out = null;
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            in = null;
            out = null;
        }
    }

    public void restart() {
        if (isRestarting) {
            return; // 防止重复重启
        }

        isRestarting = true;

        // 延迟重启以避免并发问题
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 等待1秒
                stop();
                Thread.sleep(500);  // 再等待0.5秒确保资源释放

                // 重新创建ServerSocket
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.bind(new InetSocketAddress(port));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                isRestarting = false;
                start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
