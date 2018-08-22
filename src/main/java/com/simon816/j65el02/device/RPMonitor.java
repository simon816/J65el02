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

import com.simon816.j65el02.Machine;
import com.simon816.j65el02.device.RedBus.Peripheral;

/**
 * Implements the Redbus Monitor peripheral.
 *
 * <p>
 * <a href="http://www.eloraam.com/blog/2012/04/22/rp-control-internals/">Reference</a>.
 * </p>
 *
 */
public class RPMonitor implements Peripheral {

    public static final int WIDTH = 80;
    public static final int HEIGHT = 50;

    private int accessRow;
    private int cursorX;
    private int cursorY;
    private int cursorMode = 2; // (0: hidden, 1: solid, 2: blink)
    private int keyBufferStart;
    private int keyBufferPos;
    private int blitMode; // (1: fill, 2: invert; 3: shift)
    private int blitXStartOrFill;
    private int blitYStart;
    private int blitXOffset;
    private int blitYOffset;
    private int blitWidth;
    private int blitHeight;

    private byte[] keyBuffer = new byte[0x10];
    private byte[][] windowData = new byte[HEIGHT][WIDTH];

    private boolean isDisplayDirty;
    private boolean isCursorDirty;

    private final Machine machine;
    private final MonitorDriver driver;

    /**
     * Constructs the monitor peripheral. The monitor calls methods on the {@link MonitorDriver}
     * when the display content changes.
     *
     * @param machine The machine this monitor is attached to.
     * @param driver The monitor driver.
     */
    public RPMonitor(Machine machine, MonitorDriver driver) {
        this.machine = machine;
        this.driver = driver;
        driver.setMonitor(this);
    }

    @Override
    public void write(int address, int data) {
        switch (address) {
            case 0x00:
                this.accessRow = data;
                break;
            case 0x01:
                this.isCursorDirty = this.cursorX != data;
                this.cursorX = data;
                break;
            case 0x02:
                this.isCursorDirty = this.cursorY != data;
                this.cursorY = data;
                break;
            case 0x03:
                this.isCursorDirty = this.cursorMode != data;
                this.cursorMode = data;
                break;
            case 0x04:
                this.keyBufferStart = data & 0x0f;
                break;
            case 0x05:
                this.keyBufferPos = data & 0x0f;
                break;
            case 0x06:
                break;
            case 0x07:
                this.blitMode = data;
                break;
            case 0x08:
                this.blitXStartOrFill = data;
                break;
            case 0x09:
                this.blitYStart = data;
                break;
            case 0x0A:
                this.blitXOffset = data;
                break;
            case 0x0B:
                this.blitYOffset = data;
                break;
            case 0x0C:
                this.blitWidth = data;
                break;
            case 0x0D:
                this.blitHeight = data;
                break;
            default:
                if (address >= 0x10 && address < 0x60) {
                    this.isDisplayDirty = true;
                    this.windowData[this.accessRow][address - 0x10] = (byte) data;
                }
        }
    }

    @Override
    public int read(int address) {
        switch (address) {
            case 0x00:
                return this.accessRow;
            case 0x01:
                return this.cursorX;
            case 0x02:
                return this.cursorY;
            case 0x03:
                return this.cursorMode;
            case 0x04:
                return this.keyBufferStart;
            case 0x05:
                return this.keyBufferPos;
            case 0x06:
                return this.keyBuffer[this.keyBufferStart] & 0xff;
            case 0x07:
                return this.blitMode;
            case 0x08:
                return this.blitXStartOrFill;
            case 0x09:
                return this.blitYStart;
            case 0x0A:
                return this.blitXOffset;
            case 0x0B:
                return this.blitYOffset;
            case 0x0C:
                return this.blitWidth;
            case 0x0D:
                return this.blitHeight;
            default:
                if (address >= 0x10 && address < 0x60) {
                    return this.windowData[this.accessRow][address - 0x10] & 0xff;
                }
                return 0;
        }
    }

    @Override
    public void update() {
        int maxWidth = Math.min(WIDTH, this.blitWidth + this.blitXOffset);
        int maxHeight = Math.min(HEIGHT, this.blitHeight + this.blitYOffset);

        int row = this.blitYOffset;
        int col;

        this.isDisplayDirty |= this.blitMode != 0;

        switch (this.blitMode) {
            case 1: // fill
                for (; row < maxHeight; row++) {
                    for (col = this.blitXOffset; col < maxWidth; col++) {
                        this.windowData[row][col] = (byte) this.blitXStartOrFill;
                    }
                }
                break;
            case 2: // invert
                for (; row < maxHeight; row++) {
                    for (col = this.blitXOffset; col < maxWidth; col++) {
                        this.windowData[row][col] ^= 0x80;
                    }
                }
                break;
            case 3: // shift
                int shiftX = this.blitXStartOrFill - this.blitXOffset;
                int shiftY = this.blitYStart - this.blitYOffset;
                for (; row < maxHeight; row++) {
                    int srcRow = row + shiftY;
                    if (srcRow >= 0 & srcRow < HEIGHT) {
                        for (col = this.blitXOffset; col < maxWidth; col++) {
                            int srcCol = col + shiftX;
                            if (srcCol >= 0 && srcCol < WIDTH) {
                                this.windowData[row][col] = this.windowData[srcRow][srcCol];
                            }
                        }
                    }
                }
                break;
        }

        this.blitMode = 0;

        if (this.isCursorDirty || this.isDisplayDirty) {
            this.machine.signal(); // Send interrupt
        }

        if (this.isCursorDirty) {
            this.isCursorDirty = false;
            this.driver.updateCursor(this.cursorX, this.cursorY, this.cursorMode);
        }

        if (this.isDisplayDirty) {
            this.isDisplayDirty = false;
            this.driver.update(this.windowData);
        }
    }

    /**
     * Appends a key code to the key buffer.
     *
     * @param key The key code
     */
    public void onKey(byte key) {
        int nextPos = (this.keyBufferPos + 1) & 0x0f;
        if (nextPos != this.keyBufferStart) {
            this.keyBuffer[this.keyBufferPos] = key;
            this.keyBufferPos = nextPos;
        }
        this.machine.signal();
    }

}
