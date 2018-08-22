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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.simon816.j65el02.Machine;
import com.simon816.j65el02.device.RedBus.Peripheral;

/**
 * Implements the Redbus Disk Drive peripheral.
 *
 * <p>
 * <a href="http://www.eloraam.com/blog/2012/04/22/rp-control-internals/">Reference</a>.
 * </p>
 *
 */
public class RPDrive implements Peripheral {

    private int sector;
    private int command;
    private SeekableByteChannel channel;
    private ByteBuffer buffer;
    private final byte[] diskName = new byte[0x80];
    private final byte[] diskSerial = new byte[0x80];
    private Machine machine;

    /**
     * Constructs a drive backed by a real file. The file is opened for reading and writing until
     * {@link #close} is called.
     *
     * @param machine The machine the drive is attached to
     * @param file Path to the file backing this disk
     * @param driveName Name of the drive presented to the program
     * @param serial Serial number of the drive
     * @throws IOException If the file cannot be opened
     */
    public RPDrive(Machine machine, Path file, String driveName, byte[] serial) throws IOException {
        this.machine = machine;
        this.channel = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
        this.buffer = ByteBuffer.allocateDirect(0x80);
        byte[] name = driveName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(name, 0, this.diskName, 0, name.length);
        System.arraycopy(serial, 0, this.diskSerial, 0, serial.length);
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
    public void write(int address, int data) {
        switch (address) {
            case 0x80: // Sector number (lo)
                this.sector = (this.sector & 0xff00) | data;
                break;
            case 0x81: // Sector number (hi)
                this.sector = (data << 8) | (this.sector & 0xff);
                break;
            case 0x82: // Disk command
                this.command = data;
                break;
            default: // Disk sector buffer
                if (address >= 0 && address <= 0x7f) {
                    this.buffer.put(address, (byte) data);
                }
        }
    }


    @Override
    public int read(int address) {
        switch (address) {
            case 0x80: // Sector number (lo)
                return this.sector & 0xff;
            case 0x81: // Sector number (hi)
                return (this.sector >> 8) & 0xff;
            case 0x82: // Disk command
                return this.command;
            default: // Disk sector buffer
                if (address >= 0 && address <= 0x7f) {
                    return this.buffer.get(address);
                }
                return 0;
        }
    }

    @Override
    public void update() {
        this.machine.signal();
        try {
            switch (this.command) {
                case 0x01: // Read Disk Name
                    this.buffer.clear();
                    this.buffer.put(this.diskName);
                    this.command = 0;
                    break;
                case 0x02: // Write Disk Name
                    this.buffer.get(this.diskName);
                    this.command = 0;
                    break;
                case 0x03: // Read Disk Serial
                    this.buffer.clear();
                    this.buffer.put(this.diskSerial);
                    this.command = 0;
                    break;
                case 0x04: // Read Disk Sector
                    if (this.sector >= 0x800) {
                        this.command = 0xff;
                        break;
                    }
                    this.channel.position(this.sector << 7);
                    this.buffer.position(0);
                    if (this.channel.read(this.buffer) != this.buffer.capacity()) {
                        this.command = 0xff;
                    } else {
                        this.command = 0;
                    }
                    break;
                case 0x05: // Write Disk Sector
                    if (this.sector >= 0x800) {
                        this.command = 0xff;
                        break;
                    }
                    this.channel.position(this.sector << 7);
                    this.buffer.position(0);
                    this.channel.write(this.buffer);
                    this.command = 0;
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.command = 0xff;
        }
    }
}
