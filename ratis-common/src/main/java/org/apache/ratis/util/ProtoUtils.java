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
package org.apache.ratis.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.shaded.com.google.protobuf.ByteString;
import org.apache.ratis.shaded.com.google.protobuf.ServiceException;
import org.apache.ratis.shaded.proto.RaftProtos.AppendEntriesReplyProto;
import org.apache.ratis.shaded.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.shaded.proto.RaftProtos.RaftPeerProto;
import org.apache.ratis.shaded.proto.RaftProtos.RaftRpcReplyProto;
import org.apache.ratis.shaded.proto.RaftProtos.RaftRpcRequestProto;
import org.apache.ratis.shaded.proto.RaftProtos.RequestVoteReplyProto;
import org.apache.ratis.shaded.proto.RaftProtos.SMLogEntryProto;

public class ProtoUtils {
  public static ByteString toByteString(Object obj) {
    final ByteString.Output byteOut = ByteString.newOutput();
    try(final ObjectOutputStream objOut = new ObjectOutputStream(byteOut)) {
      objOut.writeObject(obj);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Unexpected IOException when writing an object to a ByteString.", e);
    }
    return byteOut.toByteString();
  }

  public static Object toObject(ByteString bytes) {
    try(final ObjectInputStream in = new ObjectInputStream(bytes.newInput())) {
      return in.readObject();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Unexpected IOException when reading an object from a ByteString.", e);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  public static ByteString toByteString(byte[] bytes) {
    return toByteString(bytes, 0, bytes.length);
  }

  public static ByteString toByteString(byte[] bytes, int offset, int size) {
    // return singleton to reduce object allocation
    return bytes.length == 0 ?
        ByteString.EMPTY : ByteString.copyFrom(bytes, offset, size);
  }

  public static RaftPeerProto toRaftPeerProto(RaftPeer peer) {
    RaftPeerProto.Builder builder = RaftPeerProto.newBuilder()
        .setId(toByteString(peer.getId().toBytes()));
    if (peer.getAddress() != null) {
      builder.setAddress(peer.getAddress());
    }
    return builder.build();
  }

  public static RaftPeer toRaftPeer(RaftPeerProto p) {
    return new RaftPeer(new RaftPeerId(p.getId()), p.getAddress());
  }

  public static RaftPeer[] toRaftPeerArray(List<RaftPeerProto> protos) {
    final RaftPeer[] peers = new RaftPeer[protos.size()];
    for (int i = 0; i < peers.length; i++) {
      peers[i] = toRaftPeer(protos.get(i));
    }
    return peers;
  }

  public static Iterable<RaftPeerProto> toRaftPeerProtos(
      final Collection<RaftPeer> peers) {
    return () -> new Iterator<RaftPeerProto>() {
      final Iterator<RaftPeer> i = peers.iterator();

      @Override
      public boolean hasNext() {
        return i.hasNext();
      }

      @Override
      public RaftPeerProto next() {
        return toRaftPeerProto(i.next());
      }
    };
  }

  public static boolean isConfigurationLogEntry(LogEntryProto entry) {
    return entry.getLogEntryBodyCase() ==
        LogEntryProto.LogEntryBodyCase.CONFIGURATIONENTRY;
  }

  public static LogEntryProto toLogEntryProto(
      SMLogEntryProto operation, long term, long index,
      ClientId clientId, long callId) {
    return LogEntryProto.newBuilder().setTerm(term).setIndex(index)
        .setSmLogEntry(operation)
        .setClientId(toByteString(clientId.toBytes())).setCallId(callId)
        .build();
  }

  public static IOException toIOException(ServiceException se) {
    final Throwable t = se.getCause();
    if (t == null) {
      return new IOException(se);
    }
    return t instanceof IOException? (IOException)t : new IOException(se);
  }

  public static String toString(RaftRpcRequestProto proto) {
    return proto.getRequestorId() + "->" + proto.getReplyId()
        + "#" + proto.getCallId();
  }

  public static String toString(RaftRpcReplyProto proto) {
    return proto.getRequestorId() + "<-" + proto.getReplyId()
        + "#" + proto.getCallId() + ":"
        + (proto.getSuccess()? "OK": "FAIL");
  }
  public static String toString(RequestVoteReplyProto proto) {
    return toString(proto.getServerReply()) + "-t" + proto.getTerm();
  }
  public static String toString(AppendEntriesReplyProto proto) {
    return toString(proto.getServerReply()) + "-t" + proto.getTerm()
        + ", nextIndex=" + proto.getNextIndex()
        + ", result: " + proto.getResult();
  }
}
