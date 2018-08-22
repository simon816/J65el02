/*
 * Copyright (c) 2016 Seth J. Morabito <web@loomcom.com>
 *
 * Copyright (c) 2017 Simon816
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.simon816.j65el02.device;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Memory extends Device {

    private byte[] mem;

    public Memory(int startAddress, int endAddress) {
        super(startAddress, endAddress);
        this.mem = new byte[this.getSize()];
    }

    @Override
    public void write(int address, int data) {
        if (address >= this.mem.length) {
            return;
        }
        this.mem[address] = (byte) (data & 0xff);
    }

    @Override
    public int read(int address, boolean cpuAccess) {
        if (address >= this.mem.length) {
            return 0;
        }
        return this.mem[address] & 0xff;
    }

    public void loadFromFile(Path file, int memOffset, int maxLen) throws IOException {
        InputStream stream = Files.newInputStream(file);
        int offset = memOffset;
        int len = Math.min(maxLen, this.mem.length - memOffset);
        int read;
        do {
            read = stream.read(this.mem, offset, len);
            offset += read;
            len -= read;
        } while (read != -1 && len > 0);
        stream.close();
    }

    public void clear() {
        Arrays.fill(this.mem, (byte) 0);
    }

}
