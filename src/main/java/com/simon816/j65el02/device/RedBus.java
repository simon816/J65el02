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

/**
 * Implements the 65el02 Redbus.
 */
public class RedBus extends Device {

    /**
     * A Redbus peripheral.
     */
    public interface Peripheral {

        void write(int address, int data);

        int read(int address);

        void update();

    }

    private int activeDeviceId;
    private boolean enabled;

    // TODO This does nothing
    private int memoryWindow;
    @SuppressWarnings("unused")
    private boolean enableWindow;

    private Peripheral[] peripherals = new Peripheral[0x100];

    public RedBus() {
        super(-1, -1); // there is no fixed address for the redbus
    }

    @Override
    public void write(int address, int data) {
        if (!this.enabled) {
            return;
        }
        Peripheral peripheral = this.peripherals[this.activeDeviceId];
        if (peripheral != null) {
            peripheral.write(address, data & 0xff);
        }
    }

    @Override
    public int read(int address, boolean cpuAccess) {
        if (!this.enabled) {
            return 0;
        }
        Peripheral peripheral = this.peripherals[this.activeDeviceId];
        if (peripheral != null) {
            return peripheral.read(address);
        }
        return 0;
    }

    public void setActiveDevice(int id) {
        this.activeDeviceId = id;
    }

    public int getActiveDevice() {
        return this.activeDeviceId;
    }

    public void setWindowOffset(int offset) {
        this.startAddress = offset;
        this.endAddress = offset + 0xff;
    }

    public int getWindowOffset() {
        return this.startAddress;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMemoryWindow(int window) {
        this.memoryWindow = window;
    }

    public int getMemoryWindow() {
        return this.memoryWindow;
    }

    public void setEnableWindow(boolean enabled) {
        this.enableWindow = enabled;
    }

    public void setPeripheral(int id, Peripheral peripheral) {
        this.peripherals[id] = peripheral;
    }

    public void updatePeripheral() {
        Peripheral peripheral = this.peripherals[this.activeDeviceId];
        if (peripheral != null) {
            peripheral.update();
        }
    }

}
