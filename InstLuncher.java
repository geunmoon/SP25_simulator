package SP25_simulator;

// instruction에 따라 동작을 수행하는 메소드를 정의하는 클래스

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;

public class InstLuncher {
    ResourceManager rMgr;
    // Maintain open device streams for RD instruction
    public HashMap<String, FileInputStream> rdDeviceStreams = new HashMap<>();
    public HashMap<String, FileOutputStream> wrDeviceStreams = new HashMap<>();
    public InstLuncher(ResourceManager resourceManager) {
        this.rMgr = resourceManager;
    }

    // Format 1
    public void RSUB(ResourceManager.InstructionEntry entry) {
        // Format 1: RSUB
        // Return to the address stored in register L
        rMgr.register[ResourceManager.REG_PC] = rMgr.register[ResourceManager.REG_L];
        rMgr.lastUsedDeviceName = null;
        System.out.printf("[RSUB] Returned to address %06X from register L\n", rMgr.register[ResourceManager.REG_L]);
    }

    // Format 2
    public void CLEAR(ResourceManager.InstructionEntry entry) {
        // Format 2: CLEAR r1
        // The second byte holds the register number to clear
        int r1 = Integer.parseInt(entry.hexCode.substring(2, 3), 16);
        rMgr.register[r1] = 0;
        System.out.printf("[CLEAR] Cleared register %d → 0\n", r1);
    }

    public void COMPR(ResourceManager.InstructionEntry entry) {
        // Format 2: COMPR r1, r2
        // The second byte: high nibble = r1, low nibble = r2
        try {
            int byte2 = Integer.parseInt(entry.hexCode.substring(2, 4), 16);
            int r1 = (byte2 >> 4) & 0xF;
            int r2 = byte2 & 0xF;
            int val1 = rMgr.register[r1];
            int val2 = rMgr.register[r2];
            if (val1 < val2) {
                rMgr.register[ResourceManager.REG_SW] = -1;
            } else if (val1 == val2) {
                rMgr.register[ResourceManager.REG_SW] = 0;
            } else {
                rMgr.register[ResourceManager.REG_SW] = 1;
            }
            System.out.printf("[COMPR] Compared register %d (value=0x%X) with register %d (value=0x%X) → SW = %d\n",
                    r1, val1, r2, val2, rMgr.register[ResourceManager.REG_SW]);
        } catch (Exception e) {
            System.out.printf("[COMPR][ERROR] Failed to decode hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void TIXR(ResourceManager.InstructionEntry entry) {
        try {
            if (entry.hexCode.length() < 4) {
                System.out.printf("[TIXR] Invalid hexCode length for format 2: %s\n", entry.hexCode);
                return;
            }

            int byte2 = Integer.parseInt(entry.hexCode.substring(2, 4), 16);
            int r1 = (byte2 >> 4) & 0xF;

            rMgr.register[ResourceManager.REG_X]++;
            int valX = rMgr.register[ResourceManager.REG_X];
            int valR1 = rMgr.register[r1];

            if (valX < valR1) {
                rMgr.register[ResourceManager.REG_SW] = -1;
            } else if (valX == valR1) {
                rMgr.register[ResourceManager.REG_SW] = 0;
            } else {
                rMgr.register[ResourceManager.REG_SW] = 1;
            }

            System.out.printf("[TIXR] Incremented register X → 0x%X; Compared with register %d (value=0x%X) → SW = %d\n",
                    valX, r1, valR1, rMgr.register[ResourceManager.REG_SW]);
        } catch (Exception e) {
            System.out.printf("[TIXR][ERROR] Failed to execute with hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    // Format 3/4
    public void STL(ResourceManager.InstructionEntry entry) {
        // [STL] Store the contents of L register to the effective address
        int targetAddr = calculateEffectiveAddress(entry);
        int val = rMgr.register[ResourceManager.REG_L];
        rMgr.memory[targetAddr] = (char) ((val >> 16) & 0xFF);
        rMgr.memory[targetAddr + 1] = (char) ((val >> 8) & 0xFF);
        rMgr.memory[targetAddr + 2] = (char) (val & 0xFF);
        System.out.printf("[STL] Stored register L value 0x%06X into memory at %06X\n", val, targetAddr);
        System.out.printf("[STL] Memory at %06X: %02X %02X %02X\n", targetAddr,
            (int) rMgr.memory[targetAddr], (int) rMgr.memory[targetAddr + 1], (int) rMgr.memory[targetAddr + 2]);
    }

    public void JSUB(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        if ((entry.nixbpe & 0x10) != 0) { // extended format: e = 1
            targetAddr = Integer.parseInt(entry.hexCode.substring(3, 8), 16); // 5 hex digits for 20-bit address
        } else {
            targetAddr = Integer.parseInt(entry.hexCode.substring(3, 6), 16); // 3 hex digits for 12-bit address
        }

        rMgr.register[ResourceManager.REG_L] = rMgr.register[ResourceManager.REG_PC]; // Save return address
        rMgr.register[ResourceManager.REG_PC] = targetAddr; // Jump to target address

        System.out.printf("[JSUB] Jumping to address %06X (from hexCode: %s, e=%d)\n",
                targetAddr, entry.hexCode, ((entry.nixbpe & 0x10) != 0) ? 1 : 0);

    }

    public void LDA(ResourceManager.InstructionEntry entry) {
        // Handle LDA with correct immediate addressing based on nixbpe flags
        boolean[] nixbpe = new boolean[6];
        for (int i = 0; i < 6; i++) {
            nixbpe[5 - i] = (entry.nixbpe & (1 << i)) != 0;
        }
        boolean isImmediate = (nixbpe[0] == false && nixbpe[1] == true);

        int val;
        try {
            if (isImmediate) {
                // Immediate value is the displacement part (12-bit or 20-bit)
                if (entry.hexCode.length() == 6) { // Format 3
                    val = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                } else if (entry.hexCode.length() == 8) { // Format 4
                    val = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                } else {
                    System.out.printf("[LDA] Invalid hexCode length for immediate mode: %s\n", entry.hexCode);
                    return;
                }
                rMgr.register[ResourceManager.REG_A] = val;
                System.out.printf("[LDA] Loaded immediate value 0x%06X into register A\n", val);
            } else {
                int targetAddr = calculateEffectiveAddress(entry);
                val = readBytesFromMemory(targetAddr, 3);
                rMgr.register[ResourceManager.REG_A] = val;
                System.out.printf("[LDA] Loaded value 0x%06X from address %06X into register A\n", val, targetAddr);
            }
        } catch (Exception e) {
            System.out.printf("[LDA][ERROR] Failed to decode or load from hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    // Helper to calculate the effective address for format 3/4 instructions, considering n, i, x, b, p, e flags
    private int calculateEffectiveAddress(ResourceManager.InstructionEntry entry) {
        boolean[] nixbpe = new boolean[6];
        for (int i = 0; i < 6; i++) {
            nixbpe[5 - i] = (entry.nixbpe & (1 << i)) != 0;
        }
        String hexCode = entry.hexCode;
        boolean isExtended = nixbpe[5];
        int disp;
        int addr = 0;
        if (isExtended) {
            disp = Integer.parseInt(hexCode.substring(3, 8), 16);
            addr = disp;
        } else {
            disp = Integer.parseInt(hexCode.substring(3, 6), 16);
            if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
            boolean p = nixbpe[4];
            boolean b = nixbpe[3];
            if (p) {
                addr = rMgr.register[ResourceManager.REG_PC] + disp;
            } else if (b) {
                addr = rMgr.register[ResourceManager.REG_B] + disp;
            } else {
                addr = disp;
            }
        }
        // x flag
        if (nixbpe[2]) {
            addr += rMgr.register[ResourceManager.REG_X];
        }
        return addr;
    }

    // Helper to read n bytes from memory and return as int (big-endian)
    private int readBytesFromMemory(int addr, int n) {
        int val = 0;
        for (int i = 0; i < n; i++) {
            val = (val << 8) | (rMgr.memory[addr + i] & 0xFF);
        }
        return val;
    }

    public void COMP(ResourceManager.InstructionEntry entry) {
        String hex = entry.hexCode;
        int bits = entry.nixbpe;
        boolean[] flags = new boolean[6]; // n, i, x, b, p, e
        for (int i = 0; i < 6; i++) {
            flags[5 - i] = (bits & (1 << i)) != 0;
        }
        boolean isExtended = flags[5];

        int val = 0;

        try {
            if (flags[0] && flags[1]) {
                // Simple addressing
                int targetAddr = calculateTargetAddress(entry.address, hex, flags);
                val = getWordFromMemory(targetAddr);
                System.out.printf("[COMP] Compared A (0x%06X) with value from memory[0x%06X] = 0x%06X → ", rMgr.register[ResourceManager.REG_A], targetAddr, val);
            } else if (flags[1]) {
                // Immediate addressing
                int disp = getDispOrAddr(hex, isExtended);
                val = disp;
                System.out.printf("[COMP] Compared A (0x%06X) with immediate value 0x%06X → ", rMgr.register[ResourceManager.REG_A], val);
            } else if (flags[0]) {
                // Indirect addressing
                int targetAddr = calculateTargetAddress(entry.address, hex, flags);
                int pointer = getWordFromMemory(targetAddr);
                val = getWordFromMemory(pointer);
                System.out.printf("[COMP] Compared A (0x%06X) with value from indirect address [0x%06X] = 0x%06X → ", rMgr.register[ResourceManager.REG_A], pointer, val);
            }

            int acc = rMgr.register[ResourceManager.REG_A];

            if (acc < val) rMgr.register[ResourceManager.REG_SW] = -1;
            else if (acc == val) rMgr.register[ResourceManager.REG_SW] = 0;
            else rMgr.register[ResourceManager.REG_SW] = 1;

            System.out.printf("SW = %d\n", rMgr.register[ResourceManager.REG_SW]);
        } catch (Exception e) {
            System.out.printf("[COMP][ERROR] Failed to execute COMP for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    // Helper to get 3-byte word from memory at addr
    private int getWordFromMemory(int addr) {
        char[] mem = rMgr.memory;
        return ((mem[addr] & 0xFF) << 16) | ((mem[addr + 1] & 0xFF) << 8) | (mem[addr + 2] & 0xFF);
    }

    // Helper to calculate the target address for format 3/4 instructions, considering n, i, x, b, p, e flags
    private int calculateTargetAddress(int pc, String hex, boolean[] flags) {
        int disp = getDispOrAddr(hex, flags[5]);
        int addr = 0;
        boolean x = flags[2];
        boolean b = flags[3];
        boolean p = flags[4];
        boolean e = flags[5];
        if (e) {
            addr = disp;
        } else {
            // format 3: 12-bit displacement, sign-extend if needed
            if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
            if (p) {
                addr = rMgr.register[ResourceManager.REG_PC] + disp;
            } else if (b) {
                addr = rMgr.register[ResourceManager.REG_B] + disp;
            } else {
                addr = disp;
            }
        }
        if (x) {
            addr += rMgr.register[ResourceManager.REG_X];
        }
        return addr;
    }

    // Helper to extract displacement or address from hex string, depending on format 3 or 4
    private int getDispOrAddr(String hex, boolean isExtended) {
        if (isExtended) {
            return Integer.parseInt(hex.substring(3, 8), 16);
        } else {
            int disp = Integer.parseInt(hex.substring(3, 6), 16);
            return disp;
        }
    }

    public void JEQ(ResourceManager.InstructionEntry entry) {
        if (rMgr.register[ResourceManager.REG_SW] == 0) {
            int targetAddr;
            boolean isExtended = (entry.nixbpe & 0x01) != 0;

            try {
                int disp;
                if (isExtended) {
                    if (entry.hexCode.length() < 8) {
                        System.out.printf("[JEQ] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                        return;
                    }
                    disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                    targetAddr = disp;
                } else {
                    if (entry.hexCode.length() < 6) {
                        System.out.printf("[JEQ] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                        return;
                    }
                    disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                    if ((disp & 0x800) != 0) disp |= 0xFFFFF000; // Sign-extend for negative displacement
                    boolean p = (entry.nixbpe & 0x02) != 0;
                    boolean b = (entry.nixbpe & 0x04) != 0;
                    if (p) {
                        targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                    } else if (b) {
                        targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                    } else {
                        targetAddr = disp;
                    }
                }

                rMgr.register[ResourceManager.REG_PC] = targetAddr;
                System.out.printf("[JEQ] Jumped to address %06X (SW=0)\n", targetAddr);
            } catch (Exception e) {
                System.out.printf("[JEQ][ERROR] Failed to decode hexCode %s: %s\n", entry.hexCode, e.getMessage());
            }
        } else {
            System.out.printf("[JEQ] Condition not met (SW=%d), no jump\n", rMgr.register[ResourceManager.REG_SW]);
        }
    }

    public void J(ResourceManager.InstructionEntry entry) {
        String hexCode = entry.hexCode;
        int pc = rMgr.register[ResourceManager.REG_PC];
        // Use nixbpe flags: n, i, x, b, p, e (from high to low bit: 0x20 to 0x01)
        boolean[] flags = new boolean[6]; // n, i, x, b, p, e
        for (int i = 0; i < 6; i++) {
            flags[5 - i] = (entry.nixbpe & (1 << i)) != 0;
        }
        boolean n = flags[0];
        boolean iFlag = flags[1];
        boolean isExtended = flags[5];

        int targetAddr;
        if (isExtended) {
            if (hexCode.length() < 8) {
                System.out.printf("[J] Invalid hexCode length for format 4: %s\n", hexCode);
                return;
            }
            targetAddr = Integer.parseInt(hexCode.substring(3, 8), 16);
        } else {
            if (hexCode.length() < 6) {
                System.out.printf("[J] Invalid hexCode length for format 3: %s\n", hexCode);
                return;
            }
            int disp = Integer.parseInt(hexCode.substring(3, 6), 16);
            if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
            if ((entry.nixbpe & 0x02) != 0) {
                targetAddr = pc + disp;
            } else if ((entry.nixbpe & 0x04) != 0) {
                targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
            } else {
                targetAddr = disp;
            }
        }

        // Handle indirect addressing: if n=1 and i=0, fetch address stored at targetAddr
        if (n && !iFlag) {
            // Try to read the address from memory; if out-of-bounds or uninitialized, fallback to 0x000000
            try {
                targetAddr = ((rMgr.memory[targetAddr] & 0xFF) << 16) |
                             ((rMgr.memory[targetAddr + 1] & 0xFF) << 8) |
                             (rMgr.memory[targetAddr + 2] & 0xFF);
            } catch (Exception e) {
                targetAddr = 0x000000;
            }
        }

        rMgr.register[ResourceManager.REG_PC] = targetAddr;

        System.out.printf("[J] Jumped to address %06X (from hexCode: %s, e=%d)\n", targetAddr, hexCode, isExtended ? 1 : 0);
    }

    public void STA(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[STA] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[STA] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            if ((entry.nixbpe & 0x08) != 0) {
                targetAddr += rMgr.register[ResourceManager.REG_X];
            }

            int val = rMgr.register[ResourceManager.REG_A];
            rMgr.memory[targetAddr] = (char) ((val >> 16) & 0xFF);
            rMgr.memory[targetAddr + 1] = (char) ((val >> 8) & 0xFF);
            rMgr.memory[targetAddr + 2] = (char) (val & 0xFF);

            System.out.printf("[STA] Stored register A value 0x%06X into memory at %06X\n", val, targetAddr);
            // Hex dump of 16 bytes from targetAddr
            System.out.print("[STA] Memory Dump @ " + String.format("%06X", targetAddr) + " : ");
            for (int i = 0; i < 16; i++) {
                int dumpAddr = targetAddr + i;
                if (dumpAddr < rMgr.memory.length) {
                    System.out.printf("%02X ", rMgr.memory[dumpAddr] & 0xFF);
                } else {
                    System.out.print("?? ");
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.printf("[STA][ERROR] Failed to execute STA for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void LDT(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0; // e 플래그는 맨 아래 비트

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[LDT] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16); // 20-bit
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[LDT] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16); // 12-bit
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000; // Sign-extend for negative displacement
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            char[] mem = rMgr.memory;
            int val = ((mem[targetAddr] & 0xFF) << 16) | ((mem[targetAddr + 1] & 0xFF) << 8) | (mem[targetAddr + 2] & 0xFF);
            rMgr.register[ResourceManager.REG_T] = val;

            System.out.printf("[LDT] Loaded value 0x%06X from address %06X into register T\n", val, targetAddr);
        } catch (Exception e) {
            System.out.printf("[LDT][ERROR] Failed to load from hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void TD(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[TD] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[TD] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000; // sign-extend
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            char[] mem = rMgr.memory;
            // Read 1 byte and interpret as ASCII from two 4-bit nibbles for device name
            int byteVal = mem[targetAddr] & 0xFF;
            char high = Character.forDigit((byteVal >> 4) & 0xF, 16);
            char low = Character.forDigit(byteVal & 0xF, 16);
            String deviceName = "" + Character.toUpperCase(high) + Character.toUpperCase(low);

            java.io.File devFile = new java.io.File(deviceName);
            if (!deviceName.isEmpty() && devFile.exists()) {
                rMgr.register[ResourceManager.REG_SW] = 1;
            } else {
                rMgr.register[ResourceManager.REG_SW] = 0;
            }
            rMgr.lastUsedDeviceName = deviceName;
            // Always print the result, not inside any conditional
            System.out.printf("[TD] Device file (ASCII from nibbles) '%s' %s → SW = %d\n",
                deviceName,
                devFile.exists() ? "exists" : "not found",
                rMgr.register[ResourceManager.REG_SW]);
        } catch (Exception e) {
            System.out.printf("[TD][ERROR] Failed to decode address from hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void RD(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[RD] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[RD] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            // New RD logic: keep stream open for device
            int byteVal = rMgr.memory[targetAddr] & 0xFF;
            String deviceName = String.format("%02X", byteVal);
            java.io.File file = new java.io.File(deviceName);
            if (!file.exists()) {
                System.out.printf("[RD] Device file '%s' not found → A not updated\n", deviceName);
                return;
            }

            try {
                java.io.FileInputStream fis = rMgr.rdDeviceStreams.get(deviceName);
                if (fis == null) {
                    fis = new java.io.FileInputStream(file);
                    rMgr.rdDeviceStreams.put(deviceName, fis);
                }
                int read = fis.read();
                if (read == -1) {
                    rMgr.register[ResourceManager.REG_A] = 0;
                    System.out.printf("[RD] Device file '%s' is empty or EOF reached → A set to 0x00\n", deviceName);
                    return;
                }
                rMgr.register[ResourceManager.REG_A] = read & 0xFF;
                // Advance the stream manually by reading and discarding the next byte
                // This enforces reading one character at a time consistently on repeated RD
                rMgr.rdDeviceStreams.put(deviceName, fis);
                System.out.printf("[RD] Read byte 0x%02X ('%c') from device '%s' into register A\n",
                        read, (char) read, deviceName);
            } catch (Exception e) {
                System.out.printf("[RD][ERROR] Failed to read from device '%s': %s\n", deviceName, e.getMessage());
            }
        } catch (Exception e) {
            System.out.printf("[RD][ERROR] Failed to execute RD for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void STCH(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[STCH] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[STCH] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            // Apply X register indexing if x-bit is set
            if ((entry.nixbpe & 0x08) != 0) {
                targetAddr += rMgr.register[ResourceManager.REG_X];
            }

            int val = rMgr.register[ResourceManager.REG_A] & 0xFF;
            rMgr.memory[targetAddr] = (char) val;

            System.out.printf("[STCH] Stored lowest byte of register A (0x%02X) into memory at %06X\n", val, targetAddr);

            // Hex dump of 16 bytes from targetAddr
            System.out.print("[STCH] Memory Dump @ " + String.format("%06X", targetAddr-rMgr.register[ResourceManager.REG_X]) + " : ");
            for (int i = 0; i < 16; i++) {
                int dumpAddr = targetAddr + i-rMgr.register[ResourceManager.REG_X];
                if (dumpAddr < rMgr.memory.length) {
                    System.out.printf("%02X ", rMgr.memory[dumpAddr] & 0xFF);
                } else {
                    System.out.print("?? ");
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.printf("[STCH][ERROR] Failed to execute STCH for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void JLT(ResourceManager.InstructionEntry entry) {
        if (rMgr.register[ResourceManager.REG_SW] < 0) {
            int targetAddr;
            boolean isExtended = (entry.nixbpe & 0x01) != 0;

            try {
                int disp;
                if (isExtended) {
                    if (entry.hexCode.length() < 8) {
                        System.out.printf("[JLT] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                        return;
                    }
                    disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                    targetAddr = disp;
                } else {
                    if (entry.hexCode.length() < 6) {
                        System.out.printf("[JLT] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                        return;
                    }
                    disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                    if ((disp & 0x800) != 0) disp |= 0xFFFFF000; // sign-extend
                    boolean p = (entry.nixbpe & 0x02) != 0;
                    boolean b = (entry.nixbpe & 0x04) != 0;
                    if (p) {
                        targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                    } else if (b) {
                        targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                    } else {
                        targetAddr = disp;
                    }
                }

                rMgr.register[ResourceManager.REG_PC] = targetAddr;
                System.out.printf("[JLT] Jumped to address %06X (SW<0)\n", targetAddr);
            } catch (Exception e) {
                System.out.printf("[JLT][ERROR] Failed to decode hexCode %s: %s\n", entry.hexCode, e.getMessage());
            }
        } else {
            System.out.printf("[JLT] Condition not met (SW=%d), no jump\n", rMgr.register[ResourceManager.REG_SW]);
        }
    }

    public void STX(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[STX] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[STX] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            int val = rMgr.register[ResourceManager.REG_X];
            rMgr.memory[targetAddr] = (char) ((val >> 16) & 0xFF);
            rMgr.memory[targetAddr + 1] = (char) ((val >> 8) & 0xFF);
            rMgr.memory[targetAddr + 2] = (char) (val & 0xFF);

            System.out.printf("[STX] Stored register X value 0x%06X into memory at %06X\n", val, targetAddr);
            // Hex dump of 16 bytes from targetAddr
            System.out.print("[STX] Memory Dump @ " + String.format("%06X", targetAddr) + " : ");
            for (int i = 0; i < 16; i++) {
                int dumpAddr = targetAddr + i;
                if (dumpAddr < rMgr.memory.length) {
                    System.out.printf("%02X ", rMgr.memory[dumpAddr] & 0xFF);
                } else {
                    System.out.print("?? ");
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.printf("[STX][ERROR] Failed to execute STX for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void LDCH(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[LDCH] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[LDCH] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            if ((entry.nixbpe & 0x08) != 0) {
                targetAddr += rMgr.register[ResourceManager.REG_X];
            }

            int val = rMgr.memory[targetAddr] & 0xFF;
            rMgr.register[ResourceManager.REG_A] = val;

            System.out.printf("[LDCH] Loaded byte 0x%02X from memory[%06X] into register A\n", val, targetAddr);
        } catch (Exception e) {
            System.out.printf("[LDCH][ERROR] Failed to execute LDCH for hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }

    public void WD(ResourceManager.InstructionEntry entry) {
        int targetAddr;
        boolean isExtended = (entry.nixbpe & 0x01) != 0;

        try {
            int disp;
            if (isExtended) {
                if (entry.hexCode.length() < 8) {
                    System.out.printf("[WD] Invalid hexCode length for format 4: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 8), 16);
                targetAddr = disp;
            } else {
                if (entry.hexCode.length() < 6) {
                    System.out.printf("[WD] Invalid hexCode length for format 3: %s\n", entry.hexCode);
                    return;
                }
                disp = Integer.parseInt(entry.hexCode.substring(3, 6), 16);
                if ((disp & 0x800) != 0) disp |= 0xFFFFF000;
                boolean p = (entry.nixbpe & 0x02) != 0;
                boolean b = (entry.nixbpe & 0x04) != 0;
                if (p) {
                    targetAddr = rMgr.register[ResourceManager.REG_PC] + disp;
                } else if (b) {
                    targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
                } else {
                    targetAddr = disp;
                }
            }

            int byteVal = rMgr.memory[targetAddr] & 0xFF;
            String deviceName = String.format("%02X", byteVal);
            java.io.File file = new java.io.File(deviceName);
            try {
                java.io.FileOutputStream fos = rMgr.wrDeviceStreams.get(deviceName);
                if (fos == null) {
                    fos = new java.io.FileOutputStream(file, true); // append mode
                    rMgr.wrDeviceStreams.put(deviceName, fos);
                }
                int data = rMgr.register[ResourceManager.REG_A] & 0xFF;
                fos.write(data);
                fos.flush();
                System.out.printf("[WD] Wrote byte 0x%02X ('%c') from register A to device '%s'\n", data, (char) data, deviceName);
            } catch (Exception e) {
                System.out.printf("[WD][ERROR] Failed to write to device '%s': %s\n", deviceName, e.getMessage());
            }
        } catch (Exception e) {
            System.out.printf("[WD][ERROR] Failed to decode hexCode %s: %s\n", entry.hexCode, e.getMessage());
        }
    }
}