/*
 * Copyright (c) 2018 Simon816
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
import java.nio.ByteBuffer;

public interface DiskDriver {

    /**
     * Gets the name of the drive.
     *
     * @return Drive name
     */
    byte[] getDriveName();

    /**
     * Gets the serial name of the drive.
     *
     * @return Drive serial
     */
    byte[] getDriveSerial();

    /**
     * Seeks to the given location in the drive.
     *
     * @param location Absolute location in the drive
     * @throws IOException If the seek operation fails
     */
    void seek(int location) throws IOException;

    /**
     * Reads data from the drive at the current position into the buffer.
     *
     * @param buffer Buffer where data is to be put into
     * @throws IOException If the read operation fails
     */
    void read(ByteBuffer buffer) throws IOException;

    /**
     * Write data from the buffer into the drive at the current position.
     *
     * @param buffer Buffer to pull data from
     * @throws IOException If the write operation fails
     */
    void write(ByteBuffer buffer) throws IOException;

    /**
     * Updates the name of the disk to the given name.
     *
     * @param diskName New name of the disk
     * @throws IOException If the name update operation fails
     */
    void writeDiskName(byte[] diskName) throws IOException;

}
