/*
 * Copyright (c) 2008-2014 Seth J. Morabito <web@loomcom.com>
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

package com.simon816.j65el02;

/**
 * A compact, struct-like representation of CPU state.
 */
public class CpuState {
    /**
     * Accumulator
     */
    public int a;

    public int aTop; // logical register for hi bits of A

    /**
     * X index regsiter
     */
    public int x;

    /**
     * Y index register
     */
    public int y;

    /**
     * Stack Pointer
     */
    public int sp;

    /**
     * Program Counter
     */
    public int pc;

    /**
     * Last Loaded Instruction Register
     */
    public int ir;

    public int i; // 65el02 I register

    public int r; // 65el02 R stack pointer

    public int d; // 65el02 D register

    public int brk; // 65el02 BRK address

    public int por = 0x400; // 65el02 POR address

    /**
     * Peek-Ahead to next IR
     */
    public int nextIr;
    public int[] args = new int[2];
    public int[] nextArgs = new int[2];
    public int instSize;
    public boolean opTrap;
    public boolean irqAsserted;
    public boolean nmiAsserted;
    public int lastPc;
    public boolean intWait;
    public boolean signalStop;

    /* Status Flag Register bits */
    public boolean carryFlag;
    public boolean negativeFlag;
    public boolean zeroFlag;
    public boolean irqDisableFlag;
    public boolean decimalModeFlag;
    public boolean breakFlag;
    public boolean overflowFlag;
    public boolean emulationFlag = true;
    public boolean mWidthFlag = true;
    public boolean indexWidthFlag = true;
    public long stepCounter = 0L;


    public CpuState() {}

    /**
     * Snapshot a copy of the CpuState.
     *
     * (This is a copy constructor rather than an implementation of <code>Cloneable</code>
     * based on Josh Bloch's recommendation)
     *
     * @param s The CpuState to copy.
     */
    public CpuState(CpuState s) {
        this.a = s.a;
        this.x = s.x;
        this.y = s.y;
        this.sp = s.sp;
        this.pc = s.pc;
        this.ir = s.ir;
        this.i = s.i;
        this.r = s.r;
        this.d = s.d;
        this.por = s.por;
        this.brk = s.brk;
        this.nextIr = s.nextIr;
        this.lastPc = s.lastPc;
        this.args[0] = s.args[0];
        this.args[1] = s.args[1];
        this.nextArgs[0] = s.nextArgs[0];
        this.nextArgs[1] = s.nextArgs[1];
        this.instSize = s.instSize;
        this.opTrap = s.opTrap;
        this.irqAsserted = s.irqAsserted;
        this.intWait = s.intWait;
        this.signalStop = s.signalStop;
        this.carryFlag = s.carryFlag;
        this.negativeFlag = s.negativeFlag;
        this.zeroFlag = s.zeroFlag;
        this.irqDisableFlag = s.irqDisableFlag;
        this.decimalModeFlag = s.decimalModeFlag;
        this.breakFlag = s.breakFlag;
        this.overflowFlag = s.overflowFlag;
        this.emulationFlag = s.emulationFlag;
        this.stepCounter = s.stepCounter;
    }

    /**
     * Returns a string formatted for the trace log.
     *
     * @return a string formatted for the trace log.
     */
    public String toTraceEvent() {
        String opcode = Cpu.disassembleOp(this.ir, this.args, getInstructionSize(this.ir));
        return getInstructionByteStatus() + "  " +
                opcode.substring(0, 3).toLowerCase() + "  " +
                "A:" + (this.mWidthFlag ? "00" + Utils.byteToHex(this.a) : Utils.wordToHex(this.a)) + " " +
                "B:" + Utils.byteToHex(this.aTop >> 8) + " " +
                "X:" + (this.indexWidthFlag ? "00" + Utils.byteToHex(this.x) : Utils.wordToHex(this.x)) + " " +
                "Y:" + (this.indexWidthFlag ? "00" + Utils.byteToHex(this.y) : Utils.wordToHex(this.y)) + " " +
                "I:" + Utils.wordToHex(this.i) + " " +
                "D:" + Utils.wordToHex(this.d) + " " +
                "F:" + Utils.byteToHex(getStatusFlag()) + " " +
                "S:" + Utils.wordToHex(this.sp) + " " +
                "R:" + Utils.wordToHex(this.r);// + " " +
//                getProcessorStatusString() + "\n";
    }

    /**
     * @return The value of the Process Status Register, as a byte.
     */
    public int getStatusFlag() {
        int status = 0;
        if (this.carryFlag) {
            status |= Cpu.P_CARRY;
        }
        if (this.zeroFlag) {
            status |= Cpu.P_ZERO;
        }
        if (this.irqDisableFlag) {
            status |= Cpu.P_IRQ_DISABLE;
        }
        if (this.decimalModeFlag) {
            status |= Cpu.P_DECIMAL;
        }
        if (this.emulationFlag) {
            if (this.breakFlag) {
                status |= Cpu.P_BREAK_OR_X;
            }
        } else {
            if (this.indexWidthFlag) {
                status |= Cpu.P_BREAK_OR_X;
            }
        }
        if (this.emulationFlag) {
            status |= 0x20;
        } else {
            if (this.mWidthFlag) {
                status |= Cpu.P_MFLAG;
            }
        }
        if (this.overflowFlag) {
            status |= Cpu.P_OVERFLOW;
        }
        if (this.negativeFlag) {
            status |= Cpu.P_NEGATIVE;
        }
        return status;
    }

    public int getInstructionSize(int insn) {
        int m = this.mWidthFlag ? 1 : 0;
        int x = this.indexWidthFlag ? 1 : 0;
        switch (insn) {
            case 0x69: // ADC IMM
                return 3 - m;
            case 0xe9: // SBC IMM
                return 3 - m;
            case 0xc9: // CMP IMM
                return 3 - m;
            case 0xe0: // CPX IMM
                return 3 - x;
            case 0xc0: // CPY IMM
                return 3 - x;
            case 0x29: // AND IMM
                return 3 - m;
            case 0x49: // EOR IMM
                return 3 - m;
            case 0x09: // ORA IMM
                return 3 - m;
            case 0x89: // BIT IMM
                return 3 - m;
            case 0xa9: // LDA IMM
                return 3 - m;
            case 0xa2: // LDX IMM
                return 3 - x;
            case 0xa0: // LDY IMM
                return 3 - x;
            default:
                return Cpu.instructionSizes[insn];
        }
    }

    public String getInstructionByteStatus() {
        switch (getInstructionSize(this.ir)) {
            case 0:
            case 1:
                return Utils.wordToHex(this.lastPc) + "  " +
                       Utils.byteToHex(this.ir) + "      ";
            case 2:
                return Utils.wordToHex(this.lastPc) + "  " +
                       Utils.byteToHex(this.ir) + " " +
                       Utils.byteToHex(this.args[0]) + "   ";
            case 3:
                return Utils.wordToHex(this.lastPc) + "  " +
                       Utils.byteToHex(this.ir) + " " +
                       Utils.byteToHex(this.args[0]) + " " +
                       Utils.byteToHex(this.args[1]);
            default:
                return null;
        }
    }

    /**
     * @return A string representing the current status register state.
     */
    public String getProcessorStatusString() {
        return "[" + (this.negativeFlag ? 'N' : '.') +
                (this.overflowFlag ? 'V' : '.') +
                (this.emulationFlag ? "-" : (this.mWidthFlag ? 'M' : '.')) +
                (this.emulationFlag ? (this.breakFlag ? 'B' : '.') : (this.indexWidthFlag ? 'X' : '.')) +
                (this.decimalModeFlag ? 'D' : '.') +
                (this.irqDisableFlag ? 'I' : '.') +
                (this.zeroFlag ? 'Z' : '.') +
                (this.carryFlag ? 'C' : '.') +
                "]";
    }

    @Override
    public String toString() {
        return toTraceEvent();
    }
}
