/*
 * Copyright (c) 2016 Seth J. Morabito <web@loomcom.com>
 *
 * Copyright (c) 2017 Simon816
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.simon816.j65el02;

import java.util.function.IntConsumer;

/**
 * This class provides a simulation of the 65el02 CPU's state machine. A simple interface allows
 * this 65el02 to read and write to a simulated bus, and exposes some of the internal state for
 * inspection and debugging.
 *
 * <p>
 * This class is modified from the "symon" project in order to support the 65el02 instruction set.
 * </p>
 *
 * <p>
 * Resources:
 * <ul>
 * <li><a href="http://www.eloraam.com/nonwp/redcpu.php">Instruction Table</a></li>
 * <li><a href="http://www.eloraam.com/blog/2012/04/22/rp-control-internals/"> Memory Layout and
 * MMU</a></li>
 * <li><a href=
 * "https://github.com/sethm/symon/blob/master/src/main/java/com/loomcom/symon/Cpu.java"> Upstream
 * Cpu class from symon that this is based on. </a></li>
 * </ul>
 * </p>
 */
public class Cpu implements InstructionTable {

    /* Process status register mnemonics */
    public static final int P_CARRY       = 0x01;
    public static final int P_ZERO        = 0x02;
    public static final int P_IRQ_DISABLE = 0x04;
    public static final int P_DECIMAL     = 0x08;
    public static final int P_BREAK_OR_X  = 0x10;
    public static final int P_MFLAG       = 0x20;
    public static final int P_OVERFLOW    = 0x40;
    public static final int P_NEGATIVE    = 0x80;

    private static final int S_STACK_TOP = 0x200;
    private static final int R_STACK_TOP = 0x300;

    private boolean stackBug = true;

    /* The Bus */
    private Bus bus;

    /* The CPU state */
    private final CpuState state = new CpuState();

    private IntConsumer logCallback;

    /**
     * Construct a new CPU.
     */
    public Cpu() {
    }
    /**
     * Set the bus reference for this CPU.
     */
    public void setBus(Bus bus) {
        this.bus = bus;
    }

    /**
     * Return the Bus that this CPU is associated with.
     */
    public Bus getBus() {
        return this.bus;
    }

    /**
     * Set the function to be called when the 0xFF MMU instruction (Output A register to MC
     * logfile.) is executed.
     *
     * @param logCallback Consumer that is called with the value of the A register.
     */
    public void setLogCallback(IntConsumer logCallback) {
        this.logCallback = logCallback;
    }

    public void setStackBug(boolean stackBug) {
        this.stackBug = stackBug;
    }

    /**
     * Reset the CPU to known initial values.
     */
    public void reset() {
        this.state.sp = this.stackBug ? S_STACK_TOP : S_STACK_TOP - 1;
        this.state.r = R_STACK_TOP;

        // Set program counter to the power-on-reset address
        // Default = 0x400
        this.state.pc = this.state.por;

        // Clear instruction register.
        this.state.ir = 0;

        // Clear status register bits.
        this.state.carryFlag = false;
        this.state.zeroFlag = false;
        this.state.irqDisableFlag = false;
        this.state.decimalModeFlag = false;
        this.state.breakFlag = false;
        this.state.overflowFlag = false;
        this.state.negativeFlag = false;
        this.state.emulationFlag = true;
        this.state.mWidthFlag = true;
        this.state.indexWidthFlag = true;

        this.state.irqAsserted = false;

        this.state.signalStop = false;

        // Clear illegal opcode trap.
        this.state.opTrap = false;

        // Reset step counter
        this.state.stepCounter = 0L;

        // Reset registers.
        this.state.a = 0;
        this.state.x = 0;
        this.state.y = 0;

        this.state.por = 0x2000;
        // Default BRK address
        this.state.brk = 0x2000;

        peekAhead();
    }

    private int maskMWidth() {
        return this.state.mWidthFlag ? 0xff : 0xffff;
    }

    private int maskXWidth() {
        return this.state.indexWidthFlag ? 0xff : 0xffff;
    }

    private int negativeMWidth() {
        return this.state.mWidthFlag ? 0x80 : 0x8000;
    }

    private int negativeXWidth() {
        return this.state.indexWidthFlag ? 0x80 : 0x8000;
    }

    private int readMemory(int address, boolean x) {
        if (this.state.emulationFlag || (x ? this.state.indexWidthFlag : this.state.mWidthFlag)) {
            return readByte(address);
        }
        return readWord(address);
    }

    private int readByte(int address) {
        return this.bus.read(address, true);
    }

    private int readWord(int address) {
        return readByte(address) | (readByte(address + 1) << 8);
    }

    private void writeMemory(int address, int value, boolean x) {
        this.bus.write(address, value);
        boolean flag = x ? this.state.indexWidthFlag : this.state.mWidthFlag;
        if (!this.state.emulationFlag && !flag) {
            this.bus.write(address + 1, value >>> 8);
        }
    }

    private int immediateArgs(boolean x) {
        if (!this.state.emulationFlag) {
            if (x && !this.state.indexWidthFlag) {
                return Utils.address(this.state.args[0], this.state.args[1]);
            }
            if (!x && !this.state.mWidthFlag) {
                return Utils.address(this.state.args[0], this.state.args[1]);
            }
        }
        return this.state.args[0];
    }

    /**
     * Performs an individual instruction cycle.
     */
    public void step() {
        if (this.state.signalStop) {
            return;
        }
        // Store the address from which the IR was read, for debugging
        this.state.lastPc = this.state.pc;

        // Check for Interrupts before doing anything else.
        // This will set the PC and jump to the interrupt vector.
        if (this.state.nmiAsserted) {
            handleNmi();
        } else if (this.state.irqAsserted) {
            if (!this.state.intWait && !getIrqDisableFlag()) {
                handleIrq(this.state.pc);
            }
            this.state.intWait = false;
        }

        // Fetch memory location for this instruction.
        this.state.ir = this.bus.read(this.state.pc, true);
        int irAddressMode = (this.state.ir >> 2) & 0x07;  // Bits 3-5 of IR:  [ | | |X|X|X| | ]
        int irOpMode = this.state.ir & 0x03;              // Bits 6-7 of IR:  [ | | | | | |X|X]

        incrementPC();

        clearOpTrap();

        // Decode the instruction and operands
        this.state.instSize = this.state.getInstructionSize(this.state.ir);
        for (int i = 0; i < this.state.instSize - 1; i++) {
            this.state.args[i] = this.bus.read(this.state.pc, true);
            // Increment PC after reading
            incrementPC();
        }

        this.state.stepCounter++;

        // Get the data from the effective address (if any)
        int effectiveAddress = 0;
        int tmp; // Temporary storage

        switch (irOpMode) {
            case 0:
            case 2:
                switch (irAddressMode) {
                    case 0: // #Immediate
                        break;
                    case 1: // Zero Page
                        effectiveAddress = this.state.args[0];
                        break;
                    case 2: // Accumulator - ignored
                        break;
                    case 3: // Absolute
                        effectiveAddress = Utils.address(this.state.args[0], this.state.args[1]);
                        break;
                    case 4: // 65C02 (Zero Page)
                        effectiveAddress = Utils.address(readByte(this.state.args[0]), readByte((this.state.args[0] + 1) & 0xff));
                        break;
                    case 5: // Zero Page,X / Zero Page,Y
                        if (this.state.ir == 0x14) { // 65C02 TRB Zero Page
                            effectiveAddress = this.state.args[0];
                        }
                        else if (this.state.ir == 0x96 || this.state.ir == 0xb6) {
                            effectiveAddress = zpyAddress(this.state.args[0]);
                        } else {
                            effectiveAddress = zpxAddress(this.state.args[0]);
                        }
                        break;
                    case 7:
                        if (this.state.ir == 0x9c || this.state.ir == 0x1c) { // 65C02 STZ & TRB Absolute
                            effectiveAddress = Utils.address(this.state.args[0], this.state.args[1]);
                        }
                        else if (this.state.ir == 0xbe) { // Absolute,X / Absolute,Y
                            effectiveAddress = yAddress(this.state.args[0], this.state.args[1]);
                        } else {
                            effectiveAddress = xAddress(this.state.args[0], this.state.args[1]);
                        }
                        break;
                }
                break;
            case 3: // Rockwell/WDC 65C02
                switch (irAddressMode) {
                    case 3:
                        if (((this.state.ir >> 5) & 1) == 0) { // Zero Page
                            effectiveAddress = this.state.args[0];
                        } else { // Absolute
                            effectiveAddress = Utils.address(this.state.args[0], this.state.args[1]);
                        }
                        break;
                    case 7:
                        if (((this.state.ir >> 5) & 1) == 0) { // Zero Page, X
                            effectiveAddress = zpxAddress(this.state.args[0]);
                        } else { // Absolute, X
                            effectiveAddress = xAddress(this.state.args[0], this.state.args[1]);
                        }
                        break;
                    case 0: // stk,S
                        effectiveAddress = this.state.args[0] + this.state.sp & 0xffff;
                        break;
                    case 4: // (stk,S),Y
                        effectiveAddress = this.state.args[0] + this.state.sp & 0xffff;
                        effectiveAddress = yAddress(this.bus.read(effectiveAddress, true),
                                this.bus.read(effectiveAddress + 1, true));
                        break;
                    case 1: // r,R
                        effectiveAddress = this.state.args[0] + this.state.r & 0xffff;
                        break;
                    case 5:// (r,R),Y
                        effectiveAddress = this.state.args[0] + this.state.r & 0xffff;
                        effectiveAddress = yAddress(this.bus.read(effectiveAddress, true),
                                this.bus.read(effectiveAddress + 1, true));
                        break;
                }
                break;
            case 1:
                switch (irAddressMode) {
                    case 0: // (Zero Page,X)
                        tmp = (this.state.args[0] + this.state.x) & 0xff;
                        effectiveAddress = Utils.address(this.bus.read(tmp, true), this.bus.read(tmp + 1, true));
                        break;
                    case 1: // Zero Page
                        effectiveAddress = this.state.args[0];
                        break;
                    case 2: // #Immediate
                        effectiveAddress = -1;
                        break;
                    case 3: // Absolute
                        effectiveAddress = Utils.address(this.state.args[0], this.state.args[1]);
                        break;
                    case 4: // (Zero Page),Y
                        tmp = Utils.address(this.bus.read(this.state.args[0], true),
                                      this.bus.read((this.state.args[0] + 1) & 0xff, true));
                        effectiveAddress = (tmp + this.state.y) & 0xffff;
                        break;
                    case 5: // Zero Page,X
                        effectiveAddress = zpxAddress(this.state.args[0]);
                        break;
                    case 6: // Absolute, Y
                        effectiveAddress = yAddress(this.state.args[0], this.state.args[1]);
                        break;
                    case 7: // Absolute, X
                        effectiveAddress = xAddress(this.state.args[0], this.state.args[1]);
                        break;
                }
                break;
        }

        // Execute
        switch (this.state.ir) {

            /** Single Byte Instructions; Implied and Relative **/
            case 0x00: // BRK - Force Interrupt - Implied
                handleBrk(this.state.pc + 1);
                break;
            case 0x08: // PHP - Push Processor Status - Implied
                // Break flag is always set in the stack value.
                stackPushByte(this.state.getStatusFlag());
                break;
            case 0x10: // BPL - Branch if Positive - Relative
                if (!getNegativeFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0x18: // CLC - Clear Carry Flag - Implied
                clearCarryFlag();
                break;
            case 0x20: // JSR - Jump to Subroutine - Implied
                stackPushWord(this.state.pc - 1);
                this.state.pc = Utils.address(this.state.args[0], this.state.args[1]);
                break;
            case 0xfc: // JSR - (Absolute Indexed Indirect,X)
                stackPushWord(this.state.pc - 1);
                tmp = (((this.state.args[1] << 8) | this.state.args[0]) + this.state.x) & 0xffff;
                this.state.pc = Utils.address(this.bus.read(tmp, true), this.bus.read(tmp + 1, true));
                break;
            case 0x28: // PLP - Pull Processor Status - Implied
                setProcessorStatus(stackPopByte());
                break;
            case 0x30: // BMI - Branch if Minus - Relative
                if (getNegativeFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0x38: // SEC - Set Carry Flag - Implied
                setCarryFlag();
                break;
            case 0x40: // RTI - Return from Interrupt - Implied
                setProcessorStatus(stackPopByte());
                int lo = stackPopByte();
                int hi = stackPopByte();
                setProgramCounter(Utils.address(lo, hi));
                break;
            case 0x48: // PHA - Push Accumulator - Implied
                stackPush(this.state.a, false);
                break;
            case 0x50: // BVC - Branch if Overflow Clear - Relative
                if (!getOverflowFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0x58: // CLI - Clear Interrupt Disable - Implied
                clearIrqDisableFlag();
                break;
            case 0x5a: // 65C02 PHY - Push Y to stack
                stackPush(this.state.y, true);
                break;
            case 0x60: // RTS - Return from Subroutine - Implied
                lo = stackPopByte();
                hi = stackPopByte();
                setProgramCounter((Utils.address(lo, hi) + 1) & 0xffff);
                break;
            case 0x68: // PLA - Pull Accumulator - Implied
                this.state.a = stackPop(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x70: // BVS - Branch if Overflow Set - Relative
                if (getOverflowFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0x78: // SEI - Set Interrupt Disable - Implied
                setIrqDisableFlag();
                break;
            case 0x7a: // 65C02 PLY - Pull Y from Stack
                this.state.y = stackPop(true);
                setArithmeticFlags(this.state.y, true);
                break;
            case 0x80: // 65C02 BRA - Branch Always
                this.state.pc = relAddress(this.state.args[0]);
                break;
            case 0x88: // DEY - Decrement Y Register - Implied
                this.state.y = --this.state.y & maskXWidth();
                setArithmeticFlags(this.state.y, true);
                break;
            case 0x8a: // TXA - Transfer X to Accumulator - Implied
                this.state.a = this.state.x;
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x90: // BCC - Branch if Carry Clear - Relative
                if (!getCarryFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0x98: // TYA - Transfer Y to Accumulator - Implied
                this.state.a = this.state.y;
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x9a: // TXS - Transfer X to Stack Pointer - Implied
                if (this.state.indexWidthFlag) {
                    setStackPointer(this.state.sp & 0xff00 | (this.state.x & 0xff));
                } else {
                    setStackPointer(this.state.x);
                }
                break;
            case 0xa8: // TAY - Transfer Accumulator to Y - Implied
                this.state.y = this.state.a;
                setArithmeticFlags(this.state.y, true);
                break;
            case 0xaa: // TAX - Transfer Accumulator to X - Implied
                this.state.x = this.state.a;
                setArithmeticFlags(this.state.x, true);
                break;
            case 0x9b: // TXY - Transfer X to Y
                this.state.y = this.state.x;
                setArithmeticFlags(this.state.y, true);
                break;
            case 0xbb: // TYX - Transfer Y to X
                this.state.x = this.state.y;
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xb0: // BCS - Branch if Carry Set - Relative
                if (getCarryFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0xb8: // CLV - Clear Overflow Flag - Implied
                clearOverflowFlag();
                break;
            case 0xba: // TSX - Transfer Stack Pointer to X - Implied
                this.state.x = getStackPointer();
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xc8: // INY - Increment Y Register - Implied
                this.state.y = ++this.state.y & maskXWidth();
                setArithmeticFlags(this.state.y, true);
                break;
            case 0xca: // DEX - Decrement X Register - Implied
                this.state.x = --this.state.x & maskXWidth();
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xd0: // BNE - Branch if Not Equal to Zero - Relative
                if (!getZeroFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0xd8: // CLD - Clear Decimal Mode - Implied
                clearDecimalModeFlag();
                break;
            case 0xda: // 65C02 PHX - Push X to stack
                stackPush(this.state.x, true);
                break;
            case 0xe8: // INX - Increment X Register - Implied
                this.state.x = ++this.state.x & maskXWidth();
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xea: // NOP
                // Do nothing.
                break;
            case 0xf0: // BEQ - Branch if Equal to Zero - Relative
                if (getZeroFlag()) {
                    this.state.pc = relAddress(this.state.args[0]);
                }
                break;
            case 0xf8: // SED - Set Decimal Flag - Implied
                setDecimalModeFlag();
                break;
            case 0xfa: // 65C02 PLX - Pull X from Stack
                this.state.x = stackPop(true);
                setArithmeticFlags(this.state.x, true);
                break;

            case 0x62: // PER
                stackPushWord(this.state.args[0] + this.state.pc);
                break;
            case 0xd4: // PEI
                stackPushWord(
                        this.bus.read(this.state.args[0], true) | (this.bus.read(this.state.args[0] + 1, true) << 8));
                break;
            case 0xf4: // PEA
                stackPushWord(Utils.address(this.state.args[0], this.state.args[1]));
                break;

            case 0xeb: // XBA - Exchange A bytes
                if (this.state.mWidthFlag) {
                    tmp = this.state.aTop >>> 8;
                    this.state.aTop = this.state.a << 8;
                    this.state.a = tmp;
                } else {
                    this.state.a = ((this.state.a & 0xff) << 8) | ((this.state.a >> 8) & 0xff);
                }
                break;
            case 0xfb: // XCE - Exchange Carry and Emulation flags
                boolean oldCarry = this.state.carryFlag;
                this.state.carryFlag = this.state.emulationFlag;
                this.state.emulationFlag = oldCarry;
                if (oldCarry) {
                    if (!this.state.mWidthFlag) {
                        this.state.aTop = this.state.a & 0xff00;
                    }
                    this.state.mWidthFlag = this.state.indexWidthFlag = true;
                    this.state.a &= 0xff;
                    this.state.x &= 0xff;
                    this.state.y &= 0xff;
                }
                break;

            case 0xc2: // REP - Reset status bits
                setProcessorStatus(getProcessorStatus() & (this.state.args[0] ^ 0xffffffff));
                break;
            case 0xe2: // SEP - Set status bits
                setProcessorStatus(getProcessorStatus() | this.state.args[0]);
                break;

            case 0xdb: // STP
                this.state.signalStop = true;
                break;
            case 0xcb: // WAI
                this.state.intWait = true;
                break;

            case 0xef: // MMU
                switch (this.state.args[0]) {
                    case 0x00: // Map device in Reg A to redbus window
                        this.bus.getRedBus().setActiveDevice(this.state.a & 0xff);
                        break;
                    case 0x80: // Get mapped device to A
                        this.state.a = this.bus.getRedBus().getActiveDevice();
                        break;

                    case 0x01: // Redbus Window offset to A
                        this.bus.getRedBus().setWindowOffset(this.state.a);
                        break;
                    case 0x81:// Get RB window offset to A
                        this.state.a = this.bus.getRedBus().getWindowOffset();
                        if (this.state.mWidthFlag) {
                            this.state.aTop = this.state.a & 0xff00;
                            this.state.a &= 0xff;
                        }
                        break;

                    case 0x02: // Enable redbus
                        this.bus.getRedBus().setEnabled(true);
                        break;
                    case 0x82: // Disable redbus
                        this.bus.getRedBus().setEnabled(false);
                        break;

                    case 0x03: // Set external memory mapped window to A
                        this.bus.getRedBus().setMemoryWindow(this.state.a);
                        break;
                    case 0x83: // Get memory mapped window to A
                        this.state.a = this.bus.getRedBus().getMemoryWindow();
                        if (this.state.mWidthFlag) {
                            this.state.aTop = this.state.a & 0xff00;
                            this.state.a &= 0xff;
                        }
                        break;

                    case 0x04: // Enable external memory mapped window
                        this.bus.getRedBus().setEnableWindow(true);
                        break;
                    case 0x84: // Disable external memory mapped window
                        this.bus.getRedBus().setEnableWindow(false);
                        break;

                    case 0x05: // Set BRK address to A
                        this.state.brk = this.state.a;
                        break;
                    case 0x85: // Get BRK address to A
                        this.state.a = this.state.brk;
                        if (this.state.mWidthFlag) {
                            this.state.aTop = this.state.a & 0xff00;
                            this.state.a &= 0xff;
                        }
                        break;

                    case 0x06: // Set POR address to A
                        this.state.por = this.state.a;
                        break;
                    case 0x86: // Get POR address to A
                        this.state.a = this.state.por;
                        if (this.state.mWidthFlag) {
                            this.state.aTop = this.state.a & 0xff00;
                            this.state.a &= 0xff;
                        }
                        break;

                    case 0xff: // Output A register to MC logfile
                        if (this.logCallback != null) {
                            this.logCallback.accept(this.state.a);
                        }
                        break;
                }
                break;

            case 0x22: // ENT - Enter word, RHI, I=PC+2, PC=(PC)
                stackRPushWord(this.state.i);
                this.state.i = this.state.pc + 2;
                this.state.pc = readMemory(this.state.pc, false);
                break;
            case 0x42: // NXA - Next word into A, A=(I), I=I+1/I=I+2
                this.state.a = readMemory(this.state.i, false);
                this.state.i += this.state.mWidthFlag ? 1 : 2;
                break;
            case 0x02: // NXT - Next word, PC=(I), I=I+2
                this.state.pc = readMemory(this.state.i, false);
                this.state.i += 2;
                break;
            case 0x8b: // TXR - Transfer X to R
                if (this.state.indexWidthFlag) {
                    this.state.r = (this.state.r & 0xff00) | (this.state.x & 0xff);
                } else {
                    this.state.r = this.state.x;
                }
                setArithmeticFlags(this.state.r, true);
                break;
            case 0xab: // TRX - Transfer R to X
                this.state.x = this.state.r;
                setArithmeticFlags(this.state.x, true);
                break;
            case 0x5c: // TXI - Transfer X to I
                this.state.i = this.state.x;
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xdc: // TIX - Transfer I to X
                this.state.x = this.state.i;
                setArithmeticFlags(this.state.x, true);
                break;
            case 0x4b: // RHA - Push accumulator to R stack
                stackRPush(this.state.a, false);
                break;
            case 0x6b: // RLA - Pull accumulator from R stack
                this.state.a = stackRPop(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x1b: // RHX - Push X register to R stack
                stackRPush(this.state.x, true);
                break;
            case 0x3b: // RLX - Pull X register from R stack
                this.state.x = stackRPop(true);
                setArithmeticFlags(this.state.x, true);
                break;
            case 0x5b: // RHY - Push Y register to R stack
                stackRPush(this.state.y, true);
                break;
            case 0x7b: // RLY - Pull Y register from R stack
                this.state.y = stackRPop(true);
                setArithmeticFlags(this.state.y, true);
                break;
            case 0x0b: // RHI - Push I register to R stack
                stackRPushWord(this.state.i);
                break;
            case 0x2b: // RLI - Pull I register from R stack
                this.state.i = stackRPopWord();
                setArithmeticFlags(this.state.i, true);
                break;
            case 0x82: // RER - Push effective relative address to R stack
                stackRPushWord(this.state.pc + this.state.args[0]);
                break;

            // Undocumented. See http://bigfootinformatika.hu/65el02/archive/65el02_instructions.txt
            case 0x44: // REA - push address to R stack
                stackRPushWord(Utils.address(this.state.args[0], this.state.args[1]));
                break;
            case 0x54: // REI - push indirect zp address to R stack
                stackRPushWord(readWord(this.state.args[0]));
                break;

            // MUL - Signed multiply A into D:A
            case 0x0f: // Zp
            case 0x1f: // Zp,X
            case 0x2f: // Abs
            case 0x3f: // Abs, X
                mul(readMemory(effectiveAddress, false));
                break;

            // DIV - Signed divide D:A, quotient in A, remainder in D
            case 0x4f: // Zp
            case 0x5f: // Zp, X
            case 0x6f: // Abs
            case 0x7f: // Abs, X
                div(readMemory(effectiveAddress, false));
                break;

            case 0x8f: // ZEA - Zero extend A into D:A
                this.state.d = 0;
                this.state.aTop = 0;
//                this.state.a &= 0xff; // b = 0
                break;
            case 0x9f: // SEA - Sign extend A into D:A
                this.state.d = (this.state.a & negativeMWidth()) == 0 ? 0 : 0xffff;
                this.state.aTop = (this.state.d & 0xff) << 8;
//                this.state.a = ((this.state.d & 0xff) << 8) | (this.state.a & 0xff);
                break;
            case 0xaf: // TDA - Transfer D to A
                this.state.a = this.state.d & maskMWidth();
                setArithmeticFlags(this.state.a, false);
                break;
            case 0xbf: // TAD - Transfer A to D
                if (this.state.mWidthFlag) {
                    this.state.d = this.state.aTop | (this.state.a & 0xff);
                } else {
                    this.state.d = this.state.a;
                }
                setArithmeticFlags(this.state.a, false);
                break;
            case 0xcf: // PLD - Pull D register from stack
                this.state.d = stackPop(false);
                setArithmeticFlags(this.state.d, false);
                break;
            case 0xdf: // PHD - Push D register on stack
                stackPush(this.state.d, false);
                break;



            /** JMP *****************************************************************/
            case 0x4c: // JMP - Absolute
                this.state.pc = Utils.address(this.state.args[0], this.state.args[1]);
                break;
            case 0x6c: // JMP - Indirect
                lo = Utils.address(this.state.args[0], this.state.args[1]); // Address of low byte
                this.state.pc = Utils.address(this.bus.read(lo, true), this.bus.read(lo + 1, true));
                break;
            case 0x7c: // 65C02 JMP - (Absolute Indexed Indirect,X)
                lo = (((this.state.args[1] << 8) | this.state.args[0]) + this.state.x) & 0xffff;
                hi = lo + 1;
                this.state.pc = Utils.address(this.bus.read(lo, true), this.bus.read(hi, true));
                break;

            /** ORA - Logical Inclusive Or ******************************************/
            case 0x09: // #Immediate
                this.state.a |= immediateArgs(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x12: // 65C02 ORA (ZP)
            case 0x01: // (Zero Page,X)
            case 0x05: // Zero Page
            case 0x0d: // Absolute
            case 0x11: // (Zero Page),Y
            case 0x15: // Zero Page,X
            case 0x19: // Absolute,Y
            case 0x1d: // Absolute,X
            case 0x03: // stk,S
            case 0x13: // (stk,S),Y
            case 0x07: // r,R
            case 0x17: // (r,R),Y
                this.state.a |= readMemory(effectiveAddress, false);
                setArithmeticFlags(this.state.a, false);
                break;


            /** ASL - Arithmetic Shift Left *****************************************/
            case 0x0a: // Accumulator
                this.state.a = asl(this.state.a);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x06: // Zero Page
            case 0x0e: // Absolute
            case 0x16: // Zero Page,X
            case 0x1e: // Absolute,X
                tmp = asl(readMemory(effectiveAddress, false));
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;


            /** BIT - Bit Test ******************************************************/
            case 0x89: // 65C02 #Immediate
                setZeroFlag((this.state.a & immediateArgs(false)) == 0);
                break;
            case 0x34: // 65C02 Zero Page,X
            case 0x24: // Zero Page
            case 0x2c: // Absolute
            case 0x3c: // Absolute,X
                tmp = readMemory(effectiveAddress, false);
                setZeroFlag((this.state.a & tmp) == 0);
                setNegativeFlag((tmp & negativeMWidth()) != 0);
                setOverflowFlag((tmp & (this.state.mWidthFlag ? 0x40 : 0x4000)) != 0);
                break;


            /** AND - Logical AND ***************************************************/
            case 0x29: // #Immediate
                this.state.a &= immediateArgs(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x32: // 65C02 AND (ZP)
            case 0x21: // (Zero Page,X)
            case 0x25: // Zero Page
            case 0x2d: // Absolute
            case 0x31: // (Zero Page),Y
            case 0x35: // Zero Page,X
            case 0x39: // Absolute,Y
            case 0x3d: // Absolute,X
            case 0x23: // stk,S
            case 0x33: // (stk,S),Y
            case 0x27: // r,R
            case 0x37: // (r,R),Y
                this.state.a &= readMemory(effectiveAddress, false);
                setArithmeticFlags(this.state.a, false);
                break;


            /** ROL - Rotate Left ***************************************************/
            case 0x2a: // Accumulator
                this.state.a = rol(this.state.a);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x26: // Zero Page
            case 0x2e: // Absolute
            case 0x36: // Zero Page,X
            case 0x3e: // Absolute,X
                tmp = rol(readMemory(effectiveAddress, false));
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;


            /** EOR - Exclusive OR **************************************************/
            case 0x49: // #Immediate
                this.state.a ^= immediateArgs(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x52: // 65C02 EOR (ZP)
            case 0x41: // (Zero Page,X)
            case 0x45: // Zero Page
            case 0x4d: // Absolute
            case 0x51: // (Zero Page,Y)
            case 0x55: // Zero Page,X
            case 0x59: // Absolute,Y
            case 0x5d: // Absolute,X
            case 0x43: // stk,S
            case 0x53: // (stk,S),Y
            case 0x47: // r,R
            case 0x57: // (r,R),Y
                this.state.a ^= readMemory(effectiveAddress, false);
                setArithmeticFlags(this.state.a, false);
                break;


            /** LSR - Logical Shift Right *******************************************/
            case 0x4a: // Accumulator
                this.state.a = lsr(this.state.a);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x46: // Zero Page
            case 0x4e: // Absolute
            case 0x56: // Zero Page,X
            case 0x5e: // Absolute,X
                tmp = lsr(readMemory(effectiveAddress, false));
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;


            /** ADC - Add with Carry ************************************************/
            case 0x69: // #Immediate
                if (this.state.decimalModeFlag) {
                    this.state.a = adcDecimal(this.state.a, immediateArgs(false));
                } else {
                    this.state.a = adc(this.state.a, immediateArgs(false));
                }
                break;
            case 0x72: // 65C02 ADC (ZP)
            case 0x61: // (Zero Page,X)
            case 0x65: // Zero Page
            case 0x6d: // Absolute
            case 0x71: // (Zero Page),Y
            case 0x75: // Zero Page,X
            case 0x79: // Absolute,Y
            case 0x7d: // Absolute,X
            case 0x63: // stk,S
            case 0x73: // (stk,S),Y
            case 0x67: // r,R
            case 0x77: // (r,R),Y
                if (this.state.decimalModeFlag) {
                    this.state.a = adcDecimal(this.state.a, readMemory(effectiveAddress, false));
                } else {
                    this.state.a = adc(this.state.a, readMemory(effectiveAddress, false));
                }
                break;


            /** ROR - Rotate Right **************************************************/
            case 0x6a: // Accumulator
                this.state.a = ror(this.state.a);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0x66: // Zero Page
            case 0x6e: // Absolute
            case 0x76: // Zero Page,X
            case 0x7e: // Absolute,X
                tmp = ror(readMemory(effectiveAddress, false));
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;


            /** STA - Store Accumulator *********************************************/
            case 0x92: // 65C02 STA (ZP)
            case 0x81: // (Zero Page,X)
            case 0x85: // Zero Page
            case 0x8d: // Absolute
            case 0x91: // (Zero Page),Y
            case 0x95: // Zero Page,X
            case 0x99: // Absolute,Y
            case 0x9d: // Absolute,X
            case 0x83: // stk,S
            case 0x93: // (stk,S),Y
            case 0x87: // r,R
            case 0x97: // (r,R),Y
                writeMemory(effectiveAddress, this.state.a, false);
                break;


            /** STY - Store Y Register **********************************************/
            case 0x84: // Zero Page
            case 0x8c: // Absolute
            case 0x94: // Zero Page,X
                writeMemory(effectiveAddress, this.state.y, true);
                break;


            /** STX - Store X Register **********************************************/
            case 0x86: // Zero Page
            case 0x8e: // Absolute
            case 0x96: // Zero Page,Y
                writeMemory(effectiveAddress, this.state.x, true);
                break;

            /** STZ - 65C02 Store Zero ****************************************************/
            case 0x64: // Zero Page
            case 0x74: // Zero Page,X
            case 0x9c: // Absolute
            case 0x9e: // Absolute,X
                writeMemory(effectiveAddress, 0, false);
                break;

            /** LDY - Load Y Register ***********************************************/
            case 0xa0: // #Immediate
                this.state.y = immediateArgs(true);
                setArithmeticFlags(this.state.y, true);
                break;
            case 0xa4: // Zero Page
            case 0xac: // Absolute
            case 0xb4: // Zero Page,X
            case 0xbc: // Absolute,X
                this.state.y = readMemory(effectiveAddress, true);
                setArithmeticFlags(this.state.y, true);
                break;


            /** LDX - Load X Register ***********************************************/
            case 0xa2: // #Immediate
                this.state.x = immediateArgs(true);
                setArithmeticFlags(this.state.x, true);
                break;
            case 0xa6: // Zero Page
            case 0xae: // Absolute
            case 0xb6: // Zero Page,Y
            case 0xbe: // Absolute,Y
                this.state.x = readMemory(effectiveAddress, true);
                setArithmeticFlags(this.state.x, true);
                break;


            /** LDA - Load Accumulator **********************************************/
            case 0xa9: // #Immediate
                this.state.a = immediateArgs(false);
                setArithmeticFlags(this.state.a, false);
                break;
            case 0xb2: // 65C02 LDA (ZP)
            case 0xa1: // (Zero Page,X)
            case 0xa5: // Zero Page
            case 0xad: // Absolute
            case 0xb1: // (Zero Page),Y
            case 0xb5: // Zero Page,X
            case 0xb9: // Absolute,Y
            case 0xbd: // Absolute,X
            case 0xa3: // stk,S
            case 0xb3: // (stk,S),Y
            case 0xa7: // r,R
            case 0xb7: // (r,R),Y
                this.state.a = readMemory(effectiveAddress, false);
                setArithmeticFlags(this.state.a, false);
                break;


            /** CPY - Compare Y Register ********************************************/
            case 0xc0: // #Immediate
                cmp(this.state.y, immediateArgs(true), true);
                break;
            case 0xc4: // Zero Page
            case 0xcc: // Absolute
                cmp(this.state.y, readMemory(effectiveAddress, true), true);
                break;


            /** CMP - Compare Accumulator *******************************************/
            case 0xc9: // #Immediate
                cmp(this.state.a, immediateArgs(false), false);
                break;
            case 0xd2: // 65C02 CMP (ZP)
            case 0xc1: // (Zero Page,X)
            case 0xc5: // Zero Page
            case 0xcd: // Absolute
            case 0xd1: // (Zero Page),Y
            case 0xd5: // Zero Page,X
            case 0xd9: // Absolute,Y
            case 0xdd: // Absolute,X
            case 0xc3: // stk,S
            case 0xd3: // (stk,S),Y
            case 0xc7: // r,R
            case 0xd7: // (r,R),Y
                cmp(this.state.a, readMemory(effectiveAddress, false), false);
                break;


            /** DEC - Decrement Memory **********************************************/
            case 0x3a: // 65C02 Immediate
                this.state.a = --this.state.a & maskMWidth();
                setArithmeticFlags(this.state.a, false);
                break;
            case 0xc6: // Zero Page
            case 0xce: // Absolute
            case 0xd6: // Zero Page,X
            case 0xde: // Absolute,X
                tmp = (readMemory(effectiveAddress, false) - 1) & maskMWidth();
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;


            /** CPX - Compare X Register ********************************************/
            case 0xe0: // #Immediate
                cmp(this.state.x, immediateArgs(true), true);
                break;
            case 0xe4: // Zero Page
            case 0xec: // Absolute
                cmp(this.state.x, readMemory(effectiveAddress, true), true);
                break;


            /** SBC - Subtract with Carry (Borrow) **********************************/
            case 0xe9: // #Immediate
                if (this.state.decimalModeFlag) {
                    this.state.a = sbcDecimal(this.state.a, immediateArgs(false));
                } else {
                    this.state.a = sbc(this.state.a, immediateArgs(false));
                }
                break;
            case 0xf2: // 65C02 SBC (ZP)
            case 0xe1: // (Zero Page,X)
            case 0xe5: // Zero Page
            case 0xed: // Absolute
            case 0xf1: // (Zero Page),Y
            case 0xf5: // Zero Page,X
            case 0xf9: // Absolute,Y
            case 0xfd: // Absolute,X
            case 0xe3: // stk,S
            case 0xf3: // (stk,S),Y
            case 0xe7: // r,R
            case 0xf7: // (r,R),Y
                if (this.state.decimalModeFlag) {
                    this.state.a = sbcDecimal(this.state.a, readMemory(effectiveAddress, false));
                } else {
                    this.state.a = sbc(this.state.a, readMemory(effectiveAddress, false));
                }
                break;


            /** INC - Increment Memory **********************************************/
            case 0x1a: // 65C02 Increment Immediate
                this.state.a = ++this.state.a & maskMWidth();
                setArithmeticFlags(this.state.a, false);
                break;
            case 0xe6: // Zero Page
            case 0xee: // Absolute
            case 0xf6: // Zero Page,X
            case 0xfe: // Absolute,X
                tmp = (readMemory(effectiveAddress, false) + 1) & maskMWidth();
                writeMemory(effectiveAddress, tmp, false);
                setArithmeticFlags(tmp, false);
                break;

            /** 65C02 TRB/TSB - Test and Reset Bit/Test and Set Bit ***************/
            case 0x14: // 65C02 TRB - Test and Reset bit - Zero Page
            case 0x1c: // 65C02 TRB - Test and Reset bit - Absolute
                tmp = readMemory(effectiveAddress, false);
                setZeroFlag((this.state.a & tmp) == 0);
                tmp = (tmp &= ~(this.state.a)) & maskMWidth();
                writeMemory(effectiveAddress, tmp, false);
                break;

            case 0x04: // 65C02 TSB - Test and Set bit - Zero Page
            case 0x0c: // 65C02 TSB - Test and Set bit - Absolute
                tmp = readMemory(effectiveAddress, false);
                setZeroFlag((this.state.a & tmp) == 0);
                tmp = (tmp |= (this.state.a)) & maskMWidth();
                writeMemory(effectiveAddress, tmp, false);
                break;

            /** Unimplemented Instructions ****************************************/
            default:
                setOpTrap();
                break;
        }

        // Peek ahead to the next insturction and arguments
        peekAhead();
    }

    private void peekAhead() {
        this.state.nextIr = this.bus.read(this.state.pc, true);
        int nextInstSize = this.state.getInstructionSize(this.state.nextIr);
        for (int i = 1; i < nextInstSize; i++) {
            int nextRead = (this.state.pc + i) & 0xffff;
            this.state.nextArgs[i-1] = this.bus.read(nextRead, true);
        }
    }

    private void handleBrk(int returnPc) {
        handleInterrupt(returnPc, -1, true);
        clearIrq();
    }

    // TODO possibly support ISR?
    private void handleIrq(int returnPc) {
//        handleInterrupt(returnPc, IRQ_VECTOR_L, IRQ_VECTOR_H, false);
        clearIrq();
    }

    private void handleNmi() {
//        handleInterrupt(this.state.pc, NMI_VECTOR_L, NMI_VECTOR_H, false);
        clearNmi();
    }

    /**
     * Handle the common behavior of BRK, /IRQ, and /NMI
     *
     * @throws MemoryAccessException
     */
    private void handleInterrupt(int returnPc, int vector, boolean isBreak)
            {

        if (isBreak) {
            // Set the break flag before pushing.
            setBreakFlag();
        } else {
            // IRQ & NMI clear break flag
            clearBreakFlag();
        }

        // Push program counter + 1 onto the stack
        stackPushWord(returnPc);
        stackPushByte(this.state.getStatusFlag());
        // Set the Interrupt Disabled flag. RTI will clear it.
        setIrqDisableFlag();

        clearDecimalModeFlag();
        // Load interrupt vector address into PC
        if (isBreak) {
            this.state.pc = this.state.brk;
        } else {
            this.state.pc = readWord(vector);
        }
    }

    /**
     * Add with Carry, used by all addressing mode implementations of ADC.
     * As a side effect, this will set the overflow and carry flags as
     * needed.
     *
     * @param acc     The current value of the accumulator
     * @param operand The operand
     * @return The sum of the accumulator and the operand
     */
    private int adc(int acc, int operand) {
        int neg = negativeMWidth();
        int mask = maskMWidth();
        int result = (operand & mask) + (acc & mask) + getCarryBit();
        int carry6 = (operand & (neg - 1)) + (acc & (neg - 1)) + getCarryBit();
        setCarryFlag((result & (mask + 1)) != 0);
        setOverflowFlag(this.state.carryFlag ^ ((carry6 & neg) != 0));
        result &= mask;
        setArithmeticFlags(result, false);
        return result;
    }

    /**
     * Add with Carry (BCD).
     */

    private int adcDecimal(int acc, int operand) {
        int l, h, result;
        l = (acc & 0x0f) + (operand & 0x0f) + getCarryBit();
        if ((l & maskMWidth()) > 9) {
            l += 6;
        }
        h = (acc >> 4) + (operand >> 4) + (l > 15 ? 1 : 0);
        if ((h & maskMWidth()) > 9) {
            h += 6;
        }
        result = (l & 0x0f) | (h << 4);
        result &= maskMWidth();
        setCarryFlag(h > 15);
        setZeroFlag(result == 0);
        setOverflowFlag(false); // BCD never sets overflow flag

        this.state.negativeFlag = (result & negativeMWidth()) != 0; // N Flag is valid on CMOS 6502/65816
        return result;
    }

    /**
     * Common code for Subtract with Carry.  Just calls ADC of the
     * one's complement of the operand.  This lets the N, V, C, and Z
     * flags work out nicely without any additional logic.
     */
    private int sbc(int acc, int operand) {
        int result;
        result = adc(acc, ~operand);
        setArithmeticFlags(result, false);
        return result;
    }

    /**
     * Subtract with Carry, BCD mode.
     */
    private int sbcDecimal(int acc, int operand) {
        int l, h, result;
        l = (acc & 0x0f) - (operand & 0x0f) - (this.state.carryFlag ? 0 : 1);
        if ((l & 0x10) != 0) {
            l -= 6;
        }
        h = (acc >> 4) - (operand >> 4) - ((l & 0x10) != 0 ? 1 : 0);
        if ((h & 0x10) != 0) {
            h -= 6;
        }
        result = (l & 0x0f) | (h << 4) & maskMWidth();
        setCarryFlag((h & maskMWidth()) < 15);
        setZeroFlag(result == 0);
        setOverflowFlag(false); // BCD never sets overflow flag

        this.state.negativeFlag = (result & negativeMWidth()) != 0; // N Flag is valid on CMOS 6502/65816
        return (result & maskMWidth());
    }

    /**
     * Compare two values, and set carry, zero, and negative flags
     * appropriately.
     */
    private void cmp(int reg, int operand, boolean x) {
        int tmp = (reg - operand) & (x ? maskXWidth() : maskMWidth());
        setCarryFlag(reg >= operand);
        setZeroFlag(tmp == 0);
        setNegativeFlag((tmp & (x ? negativeXWidth() : negativeMWidth())) != 0); // Negative bit set
    }

    /**
     * Set the Negative and Zero flags based on the current value of the
     * register operand.
     */
    private void setArithmeticFlags(int reg, boolean x) {
        this.state.zeroFlag = (reg == 0);
        this.state.negativeFlag = (reg & (x ? negativeXWidth() : negativeMWidth())) != 0;
    }

    /**
     * Shifts the given value left by one bit, and sets the carry
     * flag to the high bit of the initial value.
     *
     * @param m The value to shift left.
     * @return the left shifted value (m * 2).
     */
    private int asl(int m) {
        setCarryFlag((m & negativeMWidth()) != 0);
        return (m << 1) & maskMWidth();
    }

    /**
     * Shifts the given value right by one bit, filling with zeros,
     * and sets the carry flag to the low bit of the initial value.
     */
    private int lsr(int m) {
        setCarryFlag((m & 0x01) != 0);
        return (m & maskMWidth()) >>> 1;
    }

    /**
     * Rotates the given value left by one bit, setting bit 0 to the value
     * of the carry flag, and setting the carry flag to the original value
     * of bit 7.
     */
    private int rol(int m) {
        int result = ((m << 1) | getCarryBit()) & maskMWidth();
        setCarryFlag((m & negativeMWidth()) != 0);
        return result;
    }

    /**
     * Rotates the given value right by one bit, setting bit 7 to the value
     * of the carry flag, and setting the carry flag to the original value
     * of bit 1.
     */
    private int ror(int m) {
        int result = ((m >>> 1) | (getCarryBit() << (this.state.mWidthFlag ? 7 : 15))) & maskMWidth();
        setCarryFlag((m & 0x01) != 0);
        return result;
    }

    private void mul(int value) {
        int v;
        if (this.state.carryFlag) {
            v = (short) value * (short) this.state.a;
        } else {
            v = (value & 0xffff) * (this.state.a & 0xffff);
        }
        this.state.a = v & maskMWidth();
        this.state.d = ((v >> (this.state.mWidthFlag ? 8 : 16)) & maskMWidth());
        this.state.negativeFlag = v < 0;
        this.state.zeroFlag = v == 0;
        this.state.overflowFlag = (this.state.d != 0) && (this.state.d != maskMWidth());
    }

    private void div(int value) {
        if (value == 0) {
            this.state.a = 0;
            this.state.d = 0;
            this.state.overflowFlag = true;
            this.state.zeroFlag = false;
            this.state.negativeFlag = false;
            return;
        }
        int q;
        if (this.state.carryFlag) {
            q = (short) this.state.d << (this.state.mWidthFlag ? 8 : 16) | this.state.a;
            value = (short) value;
        } else {
            q = (this.state.d & 0xffff) << (this.state.mWidthFlag ? 8 : 16) | this.state.a;
        }
        this.state.d = q % value & maskMWidth();
        q /= value;
        this.state.a = q & maskMWidth();
        if (this.state.carryFlag) {
            this.state.overflowFlag = (q > negativeMWidth() - 1) || (q < negativeMWidth());
        } else {
            this.state.overflowFlag = q > negativeMWidth() - 1;
        }
        this.state.zeroFlag = this.state.a == 0;
        this.state.negativeFlag = q < 0;
    }

    /**
     * Return the current Cpu State.
     *
     * @return the current Cpu State.
     */
    public CpuState getCpuState() {
        return this.state;
    }

    /**
     * @return the negative flag
     */
    public boolean getNegativeFlag() {
        return this.state.negativeFlag;
    }

    /**
     * @param negativeFlag the negative flag to set
     */
    public void setNegativeFlag(boolean negativeFlag) {
        this.state.negativeFlag = negativeFlag;
    }

    public void setNegativeFlag() {
        this.state.negativeFlag = true;
    }

    public void clearNegativeFlag() {
        this.state.negativeFlag = false;
    }

    /**
     * @return the carry flag
     */
    public boolean getCarryFlag() {
        return this.state.carryFlag;
    }

    /**
     * @return 1 if the carry flag is set, 0 if it is clear.
     */
    public int getCarryBit() {
        return (this.state.carryFlag ? 1 : 0);
    }

    /**
     * @param carryFlag the carry flag to set
     */
    public void setCarryFlag(boolean carryFlag) {
        this.state.carryFlag = carryFlag;
    }

    /**
     * Sets the Carry Flag
     */
    public void setCarryFlag() {
        this.state.carryFlag = true;
    }

    /**
     * Clears the Carry Flag
     */
    public void clearCarryFlag() {
        this.state.carryFlag = false;
    }

    /**
     * @return the zero flag
     */
    public boolean getZeroFlag() {
        return this.state.zeroFlag;
    }

    /**
     * @param zeroFlag the zero flag to set
     */
    public void setZeroFlag(boolean zeroFlag) {
        this.state.zeroFlag = zeroFlag;
    }

    /**
     * Sets the Zero Flag
     */
    public void setZeroFlag() {
        this.state.zeroFlag = true;
    }

    /**
     * Clears the Zero Flag
     */
    public void clearZeroFlag() {
        this.state.zeroFlag = false;
    }

    /**
     * @return the irq disable flag
     */
    public boolean getIrqDisableFlag() {
        return this.state.irqDisableFlag;
    }

    public void setIrqDisableFlag() {
        this.state.irqDisableFlag = true;
    }

    public void clearIrqDisableFlag() {
        this.state.irqDisableFlag = false;
    }


    /**
     * @return the decimal mode flag
     */
    public boolean getDecimalModeFlag() {
        return this.state.decimalModeFlag;
    }

    /**
     * Sets the Decimal Mode Flag to true.
     */
    public void setDecimalModeFlag() {
        this.state.decimalModeFlag = true;
    }

    /**
     * Clears the Decimal Mode Flag.
     */
    public void clearDecimalModeFlag() {
        this.state.decimalModeFlag = false;
    }

    /**
     * @return the break flag
     */
    public boolean getBreakFlag() {
        return this.state.breakFlag;
    }

    /**
     * Sets the Break Flag
     */
    public void setBreakFlag() {
        this.state.breakFlag = true;
    }

    /**
     * Clears the Break Flag
     */
    public void clearBreakFlag() {
        this.state.breakFlag = false;
    }

    /**
     * @return the overflow flag
     */
    public boolean getOverflowFlag() {
        return this.state.overflowFlag;
    }

    /**
     * @param overflowFlag the overflow flag to set
     */
    public void setOverflowFlag(boolean overflowFlag) {
        this.state.overflowFlag = overflowFlag;
    }

    /**
     * Sets the Overflow Flag
     */
    public void setOverflowFlag() {
        this.state.overflowFlag = true;
    }

    /**
     * Clears the Overflow Flag
     */
    public void clearOverflowFlag() {
        this.state.overflowFlag = false;
    }

    /**
     * Set the illegal instruction trap.
     */
    public void setOpTrap() {
        this.state.opTrap = true;
    }

    /**
     * Clear the illegal instruction trap.
     */
    public void clearOpTrap() {
        this.state.opTrap = false;
    }

    public int getAccumulator() {
        return this.state.a;
    }

    public void setAccumulator(int val) {
        this.state.a = val;
    }

    public int getXRegister() {
        return this.state.x;
    }

    public void setXRegister(int val) {
        this.state.x = val;
    }

    public int getYRegister() {
        return this.state.y;
    }

    public void setYRegister(int val) {
        this.state.y = val;
    }

    public int getProgramCounter() {
        return this.state.pc;
    }

    public void setProgramCounter(int addr) {
        this.state.pc = addr;

        // As a side-effect of setting the program counter,
        // we want to peek ahead at the next state.
        peekAhead();
    }

    public int getStackPointer() {
        return this.state.sp;
    }

    public void setStackPointer(int offset) {
        this.state.sp = offset;
    }

    public int getInstruction() {
        return this.state.ir;
    }

    /**
     * @value The value of the Process Status Register bits to be set.
     */
    public void setProcessorStatus(int value) {
        if ((value & P_CARRY) != 0) {
            setCarryFlag();
        } else {
            clearCarryFlag();
        }

        if ((value & P_ZERO) != 0) {
            setZeroFlag();
        } else {
            clearZeroFlag();
        }

        if ((value & P_IRQ_DISABLE) != 0) {
            setIrqDisableFlag();
        } else {
            clearIrqDisableFlag();
        }

        if ((value & P_DECIMAL) != 0) {
            setDecimalModeFlag();
        } else {
            clearDecimalModeFlag();
        }

        if (this.state.emulationFlag) {
            this.state.indexWidthFlag = true;
            if ((value & P_BREAK_OR_X) != 0) {
                setBreakFlag();
            } else {
                clearBreakFlag();
            }
        } else {
            if ((value & P_BREAK_OR_X) != 0) {
                this.state.indexWidthFlag = true;
                this.state.x &= 0xff;
                this.state.y &= 0xff;
            } else {
                this.state.indexWidthFlag = false;
            }
        }

        if (!this.state.emulationFlag) {
            boolean was8Bit = this.state.mWidthFlag;
            if ((value & P_MFLAG) != 0) {
                this.state.mWidthFlag = true;
                if (!was8Bit) {
                    this.state.aTop = this.state.a & 0xff00;
                    this.state.a &= 0xff;
                }
            } else {
                this.state.mWidthFlag = false;
                if (was8Bit) {
                    this.state.a = this.state.aTop | (this.state.a & 0xff);
                }
            }
        } else {
            this.state.mWidthFlag = true;
        }

        if ((value & P_OVERFLOW) != 0) {
            setOverflowFlag();
        } else {
            clearOverflowFlag();
        }

        if ((value & P_NEGATIVE) != 0) {
            setNegativeFlag();
        } else {
            clearNegativeFlag();
        }
    }

    public int getProcessorStatus() {
        return this.state.getStatusFlag();
    }

    /**
     * Simulate transition from logic-high to logic-low on the INT line.
     */
    public void assertIrq() {
       this.state.irqAsserted = true;
    }

    /**
     * Simulate transition from logic-low to logic-high of the INT line.
     */
    public void clearIrq() {
        this.state.irqAsserted = false;
    }

    /**
     * Simulate transition from logic-high to logic-low on the NMI line.
     */
    public void assertNmi() {
        this.state.nmiAsserted = true;
    }

    /**
     * Simulate transition from logic-low to logic-high of the NMI line.
     */
    public void clearNmi() {
        this.state.nmiAsserted = false;
    }

    public boolean isWaitingForInterrupt() {
        return this.state.intWait;
    }

    public boolean isStopped() {
        return this.state.signalStop;
    }

    private void stackRPush(int data, boolean x) {
        boolean flag = x ? this.state.indexWidthFlag : this.state.mWidthFlag;
        if (!this.state.emulationFlag && !flag) {
            stackRPushWord(data);
        } else {
            stackRPushByte(data);
        }
    }

    private void stackRPushByte(int data) {
        this.state.r = (this.state.r - 1) & 0xffff;
        this.bus.write(this.state.r, data);
    }

    private void stackRPushWord(int data) {
        stackRPushByte((data >> 8) & 0xff);
        stackRPushByte(data & 0xff);
    }

    private int stackRPop(boolean x) {
        boolean flag = x ? this.state.indexWidthFlag : this.state.mWidthFlag;
        if (!this.state.emulationFlag && !flag) {
            return stackRPopWord();
        } else {
            return stackRPopByte();
        }
    }

    private int stackRPopByte() {
        int val = this.bus.read(this.state.r, true);
        this.state.r = (this.state.r + 1) & 0xffff;
        return val;
    }

    private int stackRPopWord() {
        return stackRPopByte() | (stackRPopByte() << 8);
    }

    private void stackPush(int data, boolean x) {
        if (!this.state.emulationFlag && !(x ? this.state.indexWidthFlag : this.state.mWidthFlag)) {
            stackPushWord(data);
        } else {
            stackPushByte(data);
        }
    }

    /**
     * Push an item onto the stack, and decrement the stack counter.
     * Will wrap-around if already at the bottom of the stack (This
     * is the same behavior as the real 6502)
     */
    private void stackPushByte(int data) {
        if (!this.stackBug) {
            this.bus.write(this.state.sp, data);
        }
        int bottom = this.state.emulationFlag ?  S_STACK_TOP - 0x100 : 0;
        if (this.state.sp <= bottom) {
            this.state.sp = S_STACK_TOP;
        } else {
            --this.state.sp;
        }
        if (this.stackBug) {
            this.bus.write(this.state.sp, data);
        }
    }

    private void stackPushWord(int data) {
        stackPushByte((data >> 8) & 0xff);
        stackPushByte(data & 0xff);
    }

    private int stackPop(boolean x) {
        boolean word = !this.state.emulationFlag && !(x ? this.state.indexWidthFlag : this.state.mWidthFlag);
        if (word) {
            return stackPopWord();
        }
        return stackPopByte();
    }

    /**
     * Pre-increment the stack pointer, and return the top of the stack. Will wrap-around if already
     * at the top of the stack (This is the same behavior as the real 6502)
     */
    private int stackPopByte() {
        int val = 0;
        if (this.stackBug) {
            val = this.bus.read(this.state.sp, true);
        }
        if (this.state.emulationFlag && this.state.sp >= S_STACK_TOP) {
            this.state.sp = S_STACK_TOP - 0x100;
        } else {
            ++this.state.sp;
        }
        if (!this.stackBug) {
            val = this.bus.read(this.state.sp, true);
        }
        return val;
    }

    private int stackPopWord() {
        return stackPopByte() | (stackPopByte() << 8);
    }

    /*
    * Increment the PC, rolling over if necessary.
    */
    private void incrementPC() {
        if (this.state.pc == 0xffff) {
            this.state.pc = 0;
        } else {
            ++this.state.pc;
        }
    }

    /**
     * Given a hi byte and a low byte, return the Absolute,X
     * offset address.
     */
    private int xAddress(int lowByte, int hiByte) {
        return (Utils.address(lowByte, hiByte) + this.state.x) & 0xffff;
    }

    /**
     * Given a hi byte and a low byte, return the Absolute,Y
     * offset address.
     */
    private int yAddress(int lowByte, int hiByte) {
        return (Utils.address(lowByte, hiByte) + this.state.y) & 0xffff;
    }

    /**
     * Given a single byte, compute the Zero Page,X offset address.
     */
    private int zpxAddress(int zp) {
        return (zp + this.state.x) & maskXWidth();
    }

    /**
     * Given a single byte, compute the Zero Page,Y offset address.
     */
    private int zpyAddress(int zp) {
        return (zp + this.state.y) & maskXWidth();
    }

    /**
     * Given a single byte, compute the offset address.
     */
    private int relAddress(int offset) {
        // Cast the offset to a signed byte to handle negative offsets
        return (this.state.pc + (byte) offset) & 0xffff;
    }

    /**
     * Return a formatted string representing the last instruction and
     * operands that were executed.
     *
     * @return A string representing the mnemonic and operands of the instruction
     */
    public static String disassembleOp(int opCode, int[] args, int insnLen) {
        String mnemonic = opcodeNames[opCode];

        if (mnemonic == null) {
            return "???";
        }

        StringBuilder sb = new StringBuilder(mnemonic);

        switch (instructionModes[opCode]) {
            case ABS:
                sb.append(" $").append(Utils.wordToHex(Utils.address(args[0], args[1])));
                break;
            case AIX:
                sb.append(" ($").append(Utils.wordToHex(Utils.address(args[0], args[1]))).append(",X)");
            case ABX:
                sb.append(" $").append(Utils.wordToHex(Utils.address(args[0], args[1]))).append(",X");
                break;
            case ABY:
                sb.append(" $").append(Utils.wordToHex(Utils.address(args[0], args[1]))).append(",Y");
                break;
            case IMM:
                sb.append(" #$").append(insnLen > 2 ? Utils.wordToHex(Utils.address(args[0], args[1])) : Utils.byteToHex(args[0]));
                break;
            case IND:
                sb.append(" ($").append(Utils.wordToHex(Utils.address(args[0], args[1]))).append(")");
                break;
            case XIN:
                sb.append(" ($").append(Utils.byteToHex(args[0])).append(",X)");
                break;
            case INY:
                sb.append(" ($").append(Utils.byteToHex(args[0])).append("),Y");
                break;
            case REL:
            case ZPR:
            case ZPG:
                sb.append(" $").append(Utils.byteToHex(args[0]));
                break;
            case ZPX:
                sb.append(" $").append(Utils.byteToHex(args[0])).append(",X");
                break;
            case ZPY:
                sb.append(" $").append(Utils.byteToHex(args[0])).append(",Y");
                break;
            default:
                break;
        }

        return sb.toString();
    }

}
