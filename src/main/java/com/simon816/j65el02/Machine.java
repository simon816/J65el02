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

package com.simon816.j65el02;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

import com.simon816.j65el02.device.Memory;
import com.simon816.j65el02.device.RedBus;
import com.simon816.j65el02.device.RedBus.Peripheral;

/**
 * This class is the wrapper for the machine as a whole. It provides access to the bus so the
 * machine can be configured. The machine can be started as a runnable or by calling {@link #step}
 * by an external runner.
 */
public class Machine implements Runnable {

    private boolean isRunning = false;
    private Semaphore interruptWait = new Semaphore(2);

    private final Bus bus;
    private final Cpu cpu;
    private final RedBus redBus;

    private int defaultDriveId = 2;
    private int defaultMonitorId = 1;

    /**
     * Constructs the machine with and empty 8k of memory.
     */
    public Machine() {
        this(null, 0x2000);
    }

    /**
     * Constructs the machine with and empty memory of the given size.
     *
     * @param coreRamSize The size of RAM in bytes
     */
    public Machine(int coreRamSize) {
        this(null, coreRamSize);
    }

    /**
     * Constructs the machine with the given RAM size and load a bootloader into memory.
     *
     * The bootloader is loaded at address 0x400 to 0x500.
     *
     * @param bootloader The path to the bootloader file
     * @param coreRamSize The size of RAM in bytes
     */
    public Machine(Path bootloader, int coreRamSize) {
        try {
            this.cpu = new Cpu();
            this.redBus = new RedBus();
            this.bus = new Bus(this.redBus);
            this.cpu.setBus(this.bus);

            Memory ram = new Memory(0x0000, coreRamSize - 1);
            if (bootloader != null) {
                ram.loadFromFile(bootloader, 0x400, 0x100);
            }
            this.bus.addDevice(ram);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        reset();
    }

    public Bus getBus() {
        return this.bus;
    }

    public void setPeripheral(int id, Peripheral peripheral) {
        this.redBus.setPeripheral(id, peripheral);
    }

    public int getDefaultDriveId() {
        return this.defaultDriveId;
    }

    public void setDefaultDriveId(int id) {
        this.defaultDriveId = id;
    }

    public int getDefaultMonitorId() {
        return this.defaultMonitorId;
    }

    public void setDefaultMonitorId(int id) {
        this.defaultMonitorId = id;
    }

    /**
     * Used by peripherals to signal that an operation has completed.
     *
     * If the CPU is currently blocked by a WAI, this will wake it up.
     */
    public void signal() {
        if (this.interruptWait.availablePermits() < 2) {
            this.interruptWait.release();
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void run() {
        this.isRunning = true;
        do {
            step();
        } while (this.isRunning);
    }

    public void stop() {
        this.isRunning = false;
        while (this.interruptWait.availablePermits() <= 0) {
            this.interruptWait.release();
        }
        this.interruptWait.drainPermits();
        this.interruptWait.release(2);
    }

    public void reset() {
        stop();
        this.cpu.reset();
        this.bus.write(0, this.defaultDriveId);
        this.bus.write(1, this.defaultMonitorId);
    }

    /**
     * Perform a single step of the simulated system.
     *
     * If waiting for an interrupt, this blocks until {@link #signal} is called.
     */
    public void step() {
        this.interruptWait.acquireUninterruptibly();
        this.cpu.step();
        this.bus.update();
        if (this.cpu.isStopped()) {
            this.stop();
            return;
        }
        if (this.cpu.isWaitingForInterrupt()) {
            this.interruptWait.acquireUninterruptibly();
            this.cpu.assertIrq();
        }
        if (this.interruptWait.availablePermits() < 2) {
            this.interruptWait.release();
        }
    }

}
