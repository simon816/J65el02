/*
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

import com.simon816.j65el02.device.RedBus.Peripheral;


/**
 * Implements the Redbus IO Expander peripheral.
 * Currently not fully implemented.
 *
 * <p>
 * <a href="http://www.eloraam.com/blog/2012/04/22/rp-control-internals/">Reference</a>.
 * </p>
 *
 */
public class RPIOExpander implements Peripheral {

    private int readBuffer;
    private int outputLatch;

    @Override
    public void write(int address, int data) {
        switch (address) {
            case 0x00: // Read buffer (lo)
                break;
            case 0x01: // Read buffer (hi)
                break;
            case 0x02: // Output latch (lo)
                this.outputLatch = (this.outputLatch & 0xff00) | (data & 0xff);
                break;
            case 0x03: // Output latch (hi)
                this.outputLatch = ((data & 0xff) << 8) | (this.outputLatch & 0xff);
                break;
        }
    }

    @Override
    public int read(int address) {
        switch (address) {
            case 0x00: // Read buffer (lo)
                return this.readBuffer & 0xff;
            case 0x01: // Read buffer (hi)
                return (this.readBuffer >> 8) & 0xff;
            case 0x02: // Output latch (lo)
                return this.outputLatch & 0xff;
            case 0x03: // Output latch (hi)
                return (this.outputLatch >> 8) & 0xff;
            default:
                return 0;
        }
    }

    @Override
    public void update() {
        // TODO Auto-generated method stub

    }

}
