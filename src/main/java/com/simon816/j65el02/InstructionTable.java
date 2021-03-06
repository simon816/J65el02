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

public interface InstructionTable {

    /**
     * Enumeration of Addressing Modes.
     */
    enum Mode {
        ACC {
            @Override
            public String toString() {
                return "Accumulator";
            }
        },
        AIX {
            @Override
            public String toString() {
                return "Absolute, X-Indexed Indirect";
            }
        },
        ABS {
            @Override
            public String toString() {
                return "Absolute";
            }
        },

        ABX {
            @Override
            public String toString() {
                return "Absolute, X-indexed";
            }
        },

        ABY {
            @Override
            public String toString() {
                return "Absolute, Y-indexed";
            }
        },

        ASP {
            @Override
            public String toString() {
                return "Absolute, SP-indexed";
            }
        },

        ABR {
            @Override
            public String toString() {
                return "Absolute, R-indexed";
            }
        },


        IMM {
            @Override
            public String toString() {
                return "Immediate";
            }
        },

        IMP {
            @Override
            public String toString() {
                return "Implied";
            }
        },

        IND {
            @Override
            public String toString() {
                return "Indirect";
            }
        },

        XIN {
            @Override
            public String toString() {
                return "X-indexed Indirect";
            }
        },

        INY {
            @Override
            public String toString() {
                return "Indirect, Y-indexed";
            }
        },

        ISY {
            @Override
            public String toString() {
                return "Indirect SP, Y-indexed";
            }
        },

        IRY {
            @Override
            public String toString() {
                return "Indirect R, Y-indexed";
            }
        },

        REL {
            @Override
            public String toString() {
                return "Relative";
            }
        },

        ZPG {
            @Override
            public String toString() {
                return "Zero Page";
            }
        },

        ZPR {
            @Override
            public String toString() {
                return "Zero Page, Relative";
            }
        },

        ZPX {
            @Override
            public String toString() {
                return "Zero Page, X-indexed";
            }
        },

        ZPY {
            @Override
            public String toString() {
                return "Zero Page, Y-indexed";
            }
        },

        ZPI {
            @Override
            public String toString() {
                return "Zero Page Indirect";
            }
        },

        NUL {
            @Override
            public String toString() {
                return "NULL";
            }
        }
    }

    // 6502 opcodes.  No 65C02 opcodes implemented.

    /**
     * Instruction opcode names.
     */
    String[] opcodeNames = {
        "BRK", "ORA", "NXT", "ORA", "TSB", "ORA", "ASL",  "ORA",  // 0x00-0x07
        "PHP", "ORA", "ASL", "RHI", "TSB", "ORA", "ASL",  "MUL",  // 0x08-0x0f
        "BPL", "ORA", "ORA", "ORA", "TRB", "ORA", "ASL",  "ORA",  // 0x10-0x17
        "CLC", "ORA", "INC", "RHX", "TRB", "ORA", "ASL",  "MUL",  // 0x18-0x1f
        "JSR", "AND", "ENT", "AND", "BIT", "AND", "ROL",  "AND",  // 0x20-0x27
        "PLP", "AND", "ROL", "RLI", "BIT", "AND", "ROL",  "MUL",  // 0x28-0x2f
        "BMI", "AND", "AND", "AND", "BIT", "AND", "ROL",  "AND",  // 0x30-0x37
        "SEC", "AND", "DEC", "RLX", "BIT", "AND", "ROL",  "MUL",  // 0x38-0x3f
        "RTI", "EOR", "NXA", "EOR", "REA", "EOR", "LSR",  "EOR",  // 0x40-0x47
        "PHA", "EOR", "LSR", "RHA", "JMP", "EOR", "LSR",  "DIV",  // 0x48-0x4f
        "BVC", "EOR", "EOR", "EOR", "REI", "EOR", "LSR",  "EOR",  // 0x50-0x57
        "CLI", "EOR", "PHY", "RHY", "TXI", "EOR", "LSR",  "DIV",  // 0x58-0x5f
        "RTS", "ADC", "PER", "ADC", "STZ", "ADC", "ROR",  "ADC",  // 0x60-0x67
        "PLA", "ADC", "ROR", "RLA", "JMP", "ADC", "ROR",  "DIV",  // 0x68-0x6f
        "BVS", "ADC", "ADC", "ADC", "STZ", "ADC", "ROR",  "ADC",  // 0x70-0x77
        "SEI", "ADC", "PLY", "RLY", "JMP", "ADC", "ROR",  "DIV",  // 0x78-0x7f
        "BRA", "STA", "RER", "STA", "STY", "STA", "STX",  "STA",  // 0x80-0x87
        "DEY", "BIT", "TXA", "TXR", "STY", "STA", "STX",  "ZEA",  // 0x88-0x8f
        "BCC", "STA", "STA", "STA", "STY", "STA", "STX",  "STA",  // 0x90-0x97
        "TYA", "STA", "TXS", "TXY", "STZ", "STA", "STZ",  "SEA",  // 0x98-0x9f
        "LDY", "LDA", "LDX", "LDA", "LDY", "LDA", "LDX",  "LDA",  // 0xa0-0xa7
        "TAY", "LDA", "TAX", "TRX", "LDY", "LDA", "LDX",  "TDA",  // 0xa8-0xaf
        "BCS", "LDA", "LDA", "LDA", "LDY", "LDA", "LDX",  "LDA",  // 0xb0-0xb7
        "CLV", "LDA", "TSX", "TYX", "LDY", "LDA", "LDX",  "TAD",  // 0xb8-0xbf
        "CPY", "CMP", "REP", "CMP", "CPY", "CMP", "DEC",  "CMP",  // 0xc0-0xc7
        "INY", "CMP", "DEX", "WAI", "CPY", "CMP", "DEC",  "PLD",  // 0xc8-0xcf
        "BNE", "CMP", "CMP", "CMP", "PEI", "CMP", "DEC",  "CMP",  // 0xd0-0xd7
        "CLD", "CMP", "PHX", "STP", "TIX", "CMP", "DEC",  "PHD",  // 0xd8-0xdf
        "CPX", "SBC", "SEP", "SBC", "CPX", "SBC", "INC",  "SBC",  // 0xe0-0xe7
        "INX", "SBC", "NOP", "XBA", "CPX", "SBC", "INC",  "MMU",  // 0xe8-0xef
        "BEQ", "SBC", "SBC", "SBC", "PEA", "SBC", "INC",  "SBC",  // 0xf0-0xf7
        "SED", "SBC", "PLX", "XCE", "JSR", "SBC", "INC",  "NUL"   // 0xf8-0xff
    };

    /**
     * Instruction addressing modes.
     */
    Mode[] instructionModes = {
        Mode.IMP, Mode.XIN, Mode.NUL, Mode.ASP,   // 0x00-0x03
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0x04-0x07
        Mode.IMP, Mode.IMM, Mode.ACC, Mode.NUL,   // 0x08-0x0b
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.ZPG,   // 0x0c-0x0f
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0x10-0x13
        Mode.ZPG, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0x14-0x17
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0x18-0x1b
        Mode.ABS, Mode.ABX, Mode.ABX, Mode.ZPX,   // 0x1c-0x1f
        Mode.ABS, Mode.XIN, Mode.NUL, Mode.ASP,   // 0x20-0x23
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0x24-0x27
        Mode.IMP, Mode.IMM, Mode.ACC, Mode.NUL,   // 0x28-0x2b
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.ABS,   // 0x2c-0x2f
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0x30-0x33
        Mode.ZPX, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0x34-0x37
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0x38-0x3b
        Mode.NUL, Mode.ABX, Mode.ABX, Mode.ABX,   // 0x3c-0x3f
        Mode.IMP, Mode.XIN, Mode.NUL, Mode.ASP,   // 0x40-0x43
        Mode.ABS, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0x44-0x47
        Mode.IMP, Mode.IMM, Mode.ACC, Mode.NUL,   // 0x48-0x4b
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.ZPG,   // 0x4c-0x4f
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0x50-0x53
        Mode.ZPG, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0x54-0x57
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0x58-0x5b
        Mode.NUL, Mode.ABX, Mode.ABX, Mode.ZPX,   // 0x5c-0x5f
        Mode.IMP, Mode.XIN, Mode.IMM, Mode.ASP,   // 0x60-0x63
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0x64-0x67
        Mode.IMP, Mode.IMM, Mode.ACC, Mode.NUL,   // 0x68-0x6b
        Mode.IND, Mode.ABS, Mode.ABS, Mode.ABS,   // 0x6c-0x6f
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0x70-0x73
        Mode.ZPX, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0x74-0x77
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0x78-0x7b
        Mode.AIX, Mode.ABX, Mode.ABX, Mode.ABX,   // 0x7c-0x7f
        Mode.REL, Mode.XIN, Mode.REL, Mode.ASP,   // 0x80-0x83
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0x84-0x87
        Mode.IMP, Mode.IMM, Mode.IMP, Mode.NUL,   // 0x88-0x8b
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.NUL,   // 0x8c-0x8f
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0x90-0x93
        Mode.ZPX, Mode.ZPX, Mode.ZPY, Mode.IRY,   // 0x94-0x97
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0x98-0x9b
        Mode.ABS, Mode.ABX, Mode.ABX, Mode.NUL,   // 0x9c-0x9f
        Mode.IMM, Mode.XIN, Mode.IMM, Mode.ASP,   // 0xa0-0xa3
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0xa4-0xa7
        Mode.IMP, Mode.IMM, Mode.IMP, Mode.NUL,   // 0xa8-0xab
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.NUL,   // 0xac-0xaf
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0xb0-0xb3
        Mode.ZPX, Mode.ZPX, Mode.ZPY, Mode.IRY,   // 0xb4-0xb7
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0xb8-0xbb
        Mode.ABX, Mode.ABX, Mode.ABY, Mode.NUL,   // 0xbc-0xbf
        Mode.IMM, Mode.XIN, Mode.IMM, Mode.ASP,   // 0xc0-0xc3
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0xc4-0xc7
        Mode.IMP, Mode.IMM, Mode.IMP, Mode.NUL,   // 0xc8-0xcb
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.NUL,   // 0xcc-0xcf
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0xd0-0xd3
        Mode.IND, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0xd4-0xd7
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0xd8-0xdb
        Mode.NUL, Mode.ABX, Mode.ABX, Mode.NUL,   // 0xdc-0xdf
        Mode.IMM, Mode.XIN, Mode.IMM, Mode.ASP,   // 0xe0-0xe3
        Mode.ZPG, Mode.ZPG, Mode.ZPG, Mode.ABR,   // 0xe4-0xe7
        Mode.IMP, Mode.IMM, Mode.IMP, Mode.NUL,   // 0xe8-0xeb
        Mode.ABS, Mode.ABS, Mode.ABS, Mode.IMM,   // 0xec-0xef
        Mode.REL, Mode.INY, Mode.ZPI, Mode.ISY,   // 0xf0-0xf3
        Mode.IMM, Mode.ZPX, Mode.ZPX, Mode.IRY,   // 0xf4-0xf7
        Mode.IMP, Mode.ABY, Mode.IMP, Mode.NUL,   // 0xf8-0xfb
        Mode.AIX, Mode.ABX, Mode.ABX, Mode.NUL    // 0xfc-0xff
    };


    /**
     * Size, in bytes, required for each instruction. The 65el02 supports 16 bit mode, therefore the
     * size of immediate instruction operands vary. use {@link CpuState#getInstructionSize} instead.
     */
    int[] instructionSizes = {
        1, 2, 1, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 2,   // 0x00-0x0f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 2,   // 0x10-0x1f
        3, 2, 1, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 3,   // 0x20-0x2f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 3,   // 0x30-0x3f
        1, 2, 1, 2, 3, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 2,   // 0x40-0x4f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 1, 3, 3, 2,   // 0x50-0x5f
        1, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 3,   // 0x60-0x6f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 3,   // 0x70-0x7f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 1,   // 0x80-0x8f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 1,   // 0x90-0x9f
        2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 1,   // 0xa0-0xaf
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 1,   // 0xb0-0xbf
        2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 1,   // 0xc0-0xcf
        2, 2, 2, 2, 2, 2, 2, 2, 1, 3, 1, 1, 1, 3, 3, 1,   // 0xd0-0xdf
        2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 1, 3, 3, 3, 2,   // 0xe0-0xef
        2, 2, 2, 2, 3, 2, 2, 2, 1, 3, 1, 1, 3, 3, 3, 0    // 0xf0-0xff
    };


}
