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
 * Interface that holds methods called by the {@link RPMonitor} when the monitor data changes.
 */
public interface MonitorDriver {

    /**
     * Called when the {@link RPMonitor} is constructed in order to associate the monitor with the
     * driver.
     *
     * @param monitor The monitor
     */
    public void setMonitor(RPMonitor monitor);

    /**
     * Called when the cursor data is changed.
     *
     * @param cursorX
     * @param cursorY
     * @param cursorMode
     */
    public void updateCursor(int cursorX, int cursorY, int cursorMode);

    /**
     * Called when the display data has changed. The array is a 2D array of size
     * [{@link RPMonitor#HEIGHT}][{@link RPMonitor#WIDTH}].
     *
     * The display data is character data (not pixels). The lower 7 bits is the character while the
     * MSB is a flag whether the color should be inverted (i.e. foreground and background color
     * should be switched). Mapping for the 7 bit value to character is defined by a charset, which
     * must be agreed upon by the display driver and programs written for the device.
     *
     * @param windowData The character data
     */
    public void update(byte[][] windowData);

}
