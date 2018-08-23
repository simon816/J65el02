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
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/**
 * A {@link DiskDriver} backed by a real file.
 */
public class FileDiskDriver implements DiskDriver {

    private final SeekableByteChannel channel;
    private byte[] driveName;
    private byte[] driveSerial;

    /**
     * Constructs a disk driver backed by a real file. The file is opened for reading, and optionally
     * writing, until {@link #close} is called.
     *
     * @param file Path to the file backing this disk
     * @param driveName Name of the drive presented to the program
     * @param serial Serial number of the drive
     * @param readOnly Whether the file should be opened for reading only
     * @throws IOException If the file cannot be opened
     */
    public FileDiskDriver(Path file, String driveName, String serial, boolean readOnly) throws IOException {
        EnumSet<StandardOpenOption> openOptions = EnumSet.of(StandardOpenOption.READ);
        if (!readOnly) {
            openOptions.add(StandardOpenOption.WRITE);
        }
        this.channel = Files.newByteChannel(file, openOptions);
        this.driveName = driveName.getBytes(StandardCharsets.US_ASCII);
        this.driveSerial = serial.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Closes the underlying open file for this drive.
     *
     * This should be called after the machine has been terminated. Using the drive after closing is
     * undefined.
     *
     * @throws IOException If an error occurs when closing
     */
    public void close() throws IOException {
        this.channel.close();
    }

    @Override
    public byte[] getDriveName() {
        return this.driveName;
    }

    @Override
    public byte[] getDriveSerial() {
        return this.driveSerial;
    }

    @Override
    public void seek(int location) throws IOException {
        this.channel.position(location);
    }

    @Override
    public void read(ByteBuffer buffer) throws IOException {
        this.channel.read(buffer);
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        this.channel.write(buffer);
    }

    @Override
    public void writeDiskName(byte[] diskName) throws IOException {
        // TODO This does nothing
    }
}
