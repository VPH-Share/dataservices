/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.ajp;

import org.apache.coyote.ajp.AjpMessage;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * Ported from https://github.com/kohsuke/ajp-client
 * %%Ignore-License%%
 */
public class ClientMessage extends AjpMessage {
  private final MessageBytes message = MessageBytes.newInstance();

  public ClientMessage(int packetSize) {
    super(packetSize);
  }

  public byte readByte() {
    return buf[pos++];
  }

  public int readInt() {
    int val = (buf[pos++] & 0xFF) << 8;
    val += buf[pos++] & 0xFF;
    return val;
  }

  public String readString() {
    int len = readInt();
    return readString(len);
  }

  public String readString(int len) {
    StringBuilder buffer = new StringBuilder(len);

    for (int i = 0; i < len; i++) {
      char c = (char) buf[pos++];
      buffer.append(c);
    }
    // Read end of string marker
    readByte();

    return buffer.toString();
  }

  public ClientMessage append(final String text) {
    message.setString(text);
    this.appendBytes(message);
    message.recycle();
    return this;
  }

  @Override
  public void end() {
    len = pos;
    int dLen = len - 4;

    buf[0] = (byte) 0x12;
    buf[1] = (byte) 0x34;
    buf[2] = (byte) ((dLen >>> 8) & 0xFF);
    buf[3] = (byte) (dLen & 0xFF);
  }

  @Override
  public String toString() {
    final StringBuilder text = new StringBuilder();
    text.append(HexUtils.toHexString(buf)).append(' ').append(pos).append('/').append(len + 4)
        .append(System.lineSeparator());
    int max = pos;
    if (len + 4 > pos)
      max = len + 4;
    if (max > 1000)
      max = 1000;
    for (int j = 0; j < max; j += 16) {
      text.append(hexLine(buf, j, len)).append(System.lineSeparator());
    }
    return text.toString();
  }
}
