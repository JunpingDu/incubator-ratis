/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.server.simulation;

import org.apache.ratis.MiniRaftCluster;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.ConfUtils;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.impl.RaftConfiguration;
import org.apache.ratis.server.impl.RaftServerImpl;
import org.apache.ratis.server.impl.ServerImplUtils;
import org.apache.ratis.statemachine.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.ratis.conf.ConfUtils.requireMin;

public class MiniRaftClusterWithSimulatedRpc extends MiniRaftCluster {
  static final Logger LOG = LoggerFactory.getLogger(MiniRaftClusterWithSimulatedRpc.class);

  public static final Factory<MiniRaftClusterWithSimulatedRpc> FACTORY
      = new Factory<MiniRaftClusterWithSimulatedRpc>() {
    @Override
    public MiniRaftClusterWithSimulatedRpc newCluster(
        String[] ids, RaftProperties prop) {
      RaftConfigKeys.Rpc.setType(prop, SimulatedRpc.INSTANCE);
      if (ThreadLocalRandom.current().nextBoolean()) {
        // turn off simulate latency half of the times.
        prop.setInt(SimulatedRequestReply.SIMULATE_LATENCY_KEY, 0);
      }
      final int simulateLatencyMs = ConfUtils.getInt(prop::getInt,
          SimulatedRequestReply.SIMULATE_LATENCY_KEY,
          SimulatedRequestReply.SIMULATE_LATENCY_DEFAULT, requireMin(0));
      final SimulatedRequestReply<RaftServerRequest, RaftServerReply> serverRequestReply
          = new SimulatedRequestReply<>(simulateLatencyMs);
      final SimulatedClientRpc client2serverRequestReply
          = new SimulatedClientRpc(simulateLatencyMs);
      return new MiniRaftClusterWithSimulatedRpc(ids, prop,
          serverRequestReply, client2serverRequestReply);
    }
  };

  private final SimulatedRequestReply<RaftServerRequest, RaftServerReply> serverRequestReply;
  private final SimulatedClientRpc client2serverRequestReply;

  private MiniRaftClusterWithSimulatedRpc(
      String[] ids, RaftProperties properties,
      SimulatedRequestReply<RaftServerRequest, RaftServerReply> serverRequestReply,
      SimulatedClientRpc client2serverRequestReply) {
    super(ids, properties,
        SimulatedRpc.Factory.newRaftParameters(serverRequestReply, client2serverRequestReply));
    this.serverRequestReply = serverRequestReply;
    this.client2serverRequestReply = client2serverRequestReply;
  }

  @Override
  public void restart(boolean format) throws IOException {
    serverRequestReply.clear();
    client2serverRequestReply.clear();
    super.restart(format);
  }

  @Override
  protected RaftServerImpl newRaftServer(
      RaftPeerId id, StateMachine stateMachine, RaftConfiguration conf,
      RaftProperties properties) throws IOException {
    serverRequestReply.addPeer(id);
    client2serverRequestReply.addPeer(id);
    return ServerImplUtils.newRaftServer(id, stateMachine, conf, properties,
        parameters);
  }

  @Override
  public void blockQueueAndSetDelay(String leaderId, int delayMs)
      throws InterruptedException {
    // block leader sendRequest if delayMs > 0
    final boolean block = delayMs > 0;
    LOG.debug("{} leader queue {} and set {}ms delay for the other queues",
        block? "Block": "Unblock", leaderId, delayMs);
    serverRequestReply.getQueue(leaderId).blockSendRequestTo.set(block);

    // set delay takeRequest for the other queues
    getServers().stream().filter(s -> !s.getId().toString().equals(leaderId))
        .map(s -> serverRequestReply.getQueue(s.getId().toString()))
        .forEach(q -> q.delayTakeRequestTo.set(delayMs));

    final long sleepMs = 3 * getMaxTimeout() / 2;
    Thread.sleep(sleepMs);
  }

  @Override
  public void setBlockRequestsFrom(String src, boolean block) {
    serverRequestReply.getQueue(src).blockTakeRequestFrom.set(block);
  }
}
