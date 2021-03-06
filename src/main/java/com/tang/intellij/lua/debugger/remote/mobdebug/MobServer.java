/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.debugger.remote.mobdebug;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.io.BaseOutputReader;
import com.tang.intellij.lua.debugger.remote.LuaDebugProcess;
import com.tang.intellij.lua.debugger.remote.commands.DebugCommand;
import com.tang.intellij.lua.debugger.remote.commands.DefaultCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;

/**
 *
 * Created by TangZX on 2016/12/30.
 */
public class MobServer implements Runnable {

    class LuaDebugReader extends BaseOutputReader {
        LuaDebugReader(@NotNull InputStream inputStream, @Nullable Charset charset) {
            super(inputStream, charset);
            start(getClass().getName());
        }

        @Override
        protected void onTextAvailable(@NotNull String s) {
            MobServer.this.onResp(s);
        }

        @NotNull
        @Override
        protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
            return ApplicationManager.getApplication().executeOnPooledThread(runnable);
        }
    }

    private final Object locker = new Object();
    private ServerSocket server;
    private Thread thread;
    private Future threadSend;
    private LuaDebugProcess listener;
    private Queue<DebugCommand> commands = new LinkedList<>();
    private LuaDebugReader debugReader;
    private DebugCommand currentCommandWaitForResp;

    public MobServer(LuaDebugProcess listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        if (server == null)
            server = new ServerSocket(8172);
        thread = new Thread(this);
        thread.start();
    }

    private void onResp(String data) {
        data = data.trim();
        if (currentCommandWaitForResp != null && currentCommandWaitForResp.handle(data)) {
            currentCommandWaitForResp = null;
        } else {
            String[] list = data.split(" ");
            String[] params = Arrays.copyOfRange(list, 1, list.length);
            int code = Integer.parseInt(list[0]);
            listener.handleResp(code, params);
        }
    }

    @Override
    public void run() {
        try {
            final Socket accept = server.accept();
            debugReader = new LuaDebugReader(accept.getInputStream(), Charset.defaultCharset());

            threadSend = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    OutputStreamWriter stream = new OutputStreamWriter(accept.getOutputStream());
                    boolean firstTime = true;

                    while (accept.isConnected()) {
                        DebugCommand command;
                        synchronized (locker) {
                            while (commands.size() > 0) {
                                if (currentCommandWaitForResp == null) {
                                    command = commands.poll();
                                    command.setDebugProcess(listener);
                                    command.write(stream);
                                    stream.write("\n");
                                    stream.flush();
                                    if (command.getRequireRespLines() > 0)
                                        currentCommandWaitForResp = command;
                                }
                            }
                            if (firstTime) {
                                firstTime = false;
                                stream.write("RUN\n");
                                stream.flush();
                            }
                            Thread.sleep(5);
                        }
                    }

                    System.out.println("disconnect");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        currentCommandWaitForResp = null;
        if (thread != null)
            thread.interrupt();
        if (threadSend != null)
            threadSend.cancel(true);
        if (debugReader != null)
            debugReader.stop();
        try {
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addCommand(String command) {
        addCommand(new DefaultCommand(command));
    }

    public void addCommand(DebugCommand command) {
        synchronized (locker) {
            commands.add(command);
        }
    }
}
