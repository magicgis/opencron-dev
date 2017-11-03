/**
 * Copyright (c) 2015 The Opencron Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.opencron.rpc.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.opencron.common.Constants;
import org.opencron.common.job.Request;
import org.opencron.common.job.Response;
import org.opencron.common.logging.LoggerFactory;
import org.opencron.common.serialize.Decoder;
import org.opencron.common.serialize.Encoder;
import org.opencron.common.util.ExceptionUtils;
import org.opencron.rpc.ServerHandler;
import org.opencron.rpc.Server;
import org.slf4j.Logger;

import static org.opencron.common.util.ExceptionUtils.stackTrace;

public class NettyServer implements Server {

    private static Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    protected final HashedWheelTimer timer = new HashedWheelTimer();

    public NettyServer() {
    }

    @Override
    public void open(final int prot, ServerHandler serverHandler) {

        final NettyServerHandler handler = new NettyServerHandler(serverHandler);

        this.bootstrap = new ServerBootstrap();
        this.bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("NettyServerBoss", true));
        this.workerGroup = new NioEventLoopGroup(Constants.DEFAULT_IO_THREADS, new DefaultThreadFactory("NettyServerWorker", true));

        this.bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(
                                new Decoder(Request.class, 1024 * 1024, 2, 4),
                                new Encoder(Response.class),
                                handler
                        );
                    }
                });
        try {

           this.bootstrap.bind(prot).sync().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        logger.info("[opencron]NettyServer start at address:{} success", prot);
                    } else {
                        logger.error("[opencron]NettyServer start at address:{} failure", prot);
                    }
                }
            }).channel().closeFuture().sync();

        } catch (InterruptedException e) {
            logger.error("[opencron] NettyServer start failure: {}", stackTrace(e));
        }
    }


    @Override
    public void close() throws Throwable {
        try {
            if (bootstrap != null) {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
            logger.info("[opencron]NettyServer stoped!");
        } catch (Throwable e) {
            logger.error("[opencron]NettyServer stop error:{}",stackTrace(e));
        }
    }

}





