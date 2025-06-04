package SP25_simulator;

// instruction에 따라 동작을 수행하는 메소드를 정의하는 클래스

public class InstLuncher {
    ResourceManager rMgr;

    public InstLuncher(ResourceManager resourceManager) {
        this.rMgr = resourceManager;
    }

    public void STL(int address) {
        System.out.println("[EXEC] Executing STL at address: " + String.format("%06X", address));
        int value = rMgr.register[ResourceManager.REG_L];
        rMgr.memory[address] = (char) ((value >> 16) & 0xFF);
        rMgr.memory[address + 1] = (char) ((value >> 8) & 0xFF);
        rMgr.memory[address + 2] = (char) (value & 0xFF);
        System.out.printf("[STL] Stored %06X into memory[%06X ~ %06X]\n", value, address, address + 2);
    }

    public void JSUB(int address) {
        System.out.println("[EXEC] Executing JSUB at address: " + String.format("%06X", address));
        // Save current PC to register L
        rMgr.register[ResourceManager.REG_L] = rMgr.register[ResourceManager.REG_PC];
        // Set PC to target address
        rMgr.register[ResourceManager.REG_PC] = address;
        System.out.printf("[JSUB] Saved return address %06X to register L\n", rMgr.register[ResourceManager.REG_L]);
        System.out.printf("[JSUB] Jumping to subroutine at %06X\n", address);
    }

    public void LDA(int address) {
        System.out.println("[EXEC] Executing LDA at address: " + String.format("%06X", address));
        int value = (rMgr.memory[address] & 0xFF) << 16 | (rMgr.memory[address + 1] & 0xFF) << 8 | (rMgr.memory[address + 2] & 0xFF);
        rMgr.register[ResourceManager.REG_A] = value;
        System.out.printf("[LDA] Loaded %06X into register A\n", value);
    }

    public void COMP(int address) {
        System.out.println("[EXEC] Executing COMP at address: " + String.format("%06X", address));
        int memValue = (rMgr.memory[address] & 0xFF) << 16 | (rMgr.memory[address + 1] & 0xFF) << 8 | (rMgr.memory[address + 2] & 0xFF);
        int acc = rMgr.register[ResourceManager.REG_A];
        if (acc < memValue) rMgr.register[ResourceManager.REG_SW] = -1;
        else if (acc == memValue) rMgr.register[ResourceManager.REG_SW] = 0;
        else rMgr.register[ResourceManager.REG_SW] = 1;
        System.out.printf("[COMP] Compared A(%06X) with M(%06X), SW = %d\n", acc, memValue, rMgr.register[ResourceManager.REG_SW]);
    }

    public void JEQ(int address) {
        System.out.println("[EXEC] Executing JEQ at address: " + String.format("%06X", address));
        if (rMgr.register[ResourceManager.REG_SW] == 0) {
            rMgr.register[ResourceManager.REG_PC] = address;
            System.out.printf("[JEQ] SW=0, Jumping to %06X\n", address);
        } else {
            System.out.println("[JEQ] SW!=0, No jump performed");
        }
    }

    public void J(int address) {
        System.out.println("[EXEC] Executing J at address: " + String.format("%06X", address));
        rMgr.register[ResourceManager.REG_PC] = address;
        System.out.printf("[J] Jumped to %06X\n", address);
    }

    public void STA(int address) {
        System.out.println("[EXEC] Executing STA at address: " + String.format("%06X", address));
        int value = rMgr.register[ResourceManager.REG_A];
        rMgr.memory[address] = (char) ((value >> 16) & 0xFF);
        rMgr.memory[address + 1] = (char) ((value >> 8) & 0xFF);
        rMgr.memory[address + 2] = (char) (value & 0xFF);
        System.out.printf("[STA] Stored A(%06X) into memory[%06X ~ %06X]\n", value, address, address + 2);
    }

    public void CLEAR(int address) {
        System.out.println("[EXEC] Executing CLEAR at address: " + String.format("%06X", address));
        rMgr.register[address] = 0;
        System.out.printf("[CLEAR] Cleared register #%d\n", address);
    }

    public void LDT(int address) {
        System.out.println("[EXEC] Executing LDT at address: " + String.format("%06X", address));
        int value = (rMgr.memory[address] & 0xFF) << 16 | (rMgr.memory[address + 1] & 0xFF) << 8 | (rMgr.memory[address + 2] & 0xFF);
        rMgr.register[ResourceManager.REG_T] = value;
        System.out.printf("[LDT] Loaded %06X into register T\n", value);
    }

    public void TD(int address) {
        System.out.println("[EXEC] Executing TD at address: " + String.format("%06X", address));
        // Assume all devices are always ready
        rMgr.register[ResourceManager.REG_SW] = 0;
        System.out.printf("[TD] Test Device at address %06X, setting SW=0 (ready)\n", address);
    }

    public void RD(int address) {
        System.out.println("[EXEC] Executing RD at address: " + String.format("%06X", address));
        int value = address & 0xFF;
        rMgr.register[ResourceManager.REG_A] = value;
        System.out.printf("[RD] Read char %02X from device, loaded into register A\n", value);
    }

    public void COMPR(int address) {
        System.out.println("[EXEC] Executing COMPR at address: " + String.format("%06X", address));
        int r1 = (address >> 4) & 0xF;
        int r2 = address & 0xF;
        if (rMgr.register[r1] < rMgr.register[r2]) rMgr.register[ResourceManager.REG_SW] = -1;
        else if (rMgr.register[r1] == rMgr.register[r2]) rMgr.register[ResourceManager.REG_SW] = 0;
        else rMgr.register[ResourceManager.REG_SW] = 1;
        System.out.printf("[COMPR] Compared R%d(%06X) with R%d(%06X), SW = %d\n",
            r1, rMgr.register[r1], r2, rMgr.register[r2], rMgr.register[ResourceManager.REG_SW]);
    }

    public void STCH(int address) {
        System.out.println("[EXEC] Executing STCH at address: " + String.format("%06X", address));
        int value = rMgr.register[ResourceManager.REG_A] & 0xFF;
        rMgr.memory[address] = (char) value;
        System.out.printf("[STCH] Stored lowest byte of A(%02X) into memory[%06X]\n", value, address);
    }

    public void TIXR(int address) {
        System.out.println("[EXEC] Executing TIXR at address: " + String.format("%06X", address));
        rMgr.register[ResourceManager.REG_X]++;
        int xVal = rMgr.register[ResourceManager.REG_X];
        int tVal = rMgr.register[address];
        if (xVal < tVal) rMgr.register[ResourceManager.REG_SW] = -1;
        else if (xVal == tVal) rMgr.register[ResourceManager.REG_SW] = 0;
        else rMgr.register[ResourceManager.REG_SW] = 1;
        System.out.printf("[TIXR] Incremented X to %06X, compared with R%d(%06X), SW = %d\n",
            xVal, address, tVal, rMgr.register[ResourceManager.REG_SW]);
    }

    public void JLT(int address) {
        System.out.println("[EXEC] Executing JLT at address: " + String.format("%06X", address));
        if (rMgr.register[ResourceManager.REG_SW] < 0) {
            rMgr.register[ResourceManager.REG_PC] = address;
            System.out.printf("[JLT] SW<0, Jumping to %06X\n", address);
        } else {
            System.out.println("[JLT] SW>=0, No jump performed");
        }
    }

    public void STX(int address) {
        System.out.println("[EXEC] Executing STX at address: " + String.format("%06X", address));
        int value = rMgr.register[ResourceManager.REG_X];
        rMgr.memory[address] = (char) ((value >> 16) & 0xFF);
        rMgr.memory[address + 1] = (char) ((value >> 8) & 0xFF);
        rMgr.memory[address + 2] = (char) (value & 0xFF);
        System.out.printf("[STX] Stored X(%06X) into memory[%06X ~ %06X]\n", value, address, address + 2);
    }

    public void LDCH(int address) {
        System.out.println("[EXEC] Executing LDCH at address: " + String.format("%06X", address));
        int value = rMgr.memory[address] & 0xFF;
        rMgr.register[ResourceManager.REG_A] = (rMgr.register[ResourceManager.REG_A] & 0xFFFF00) | value;
        System.out.printf("[LDCH] Loaded char %02X from memory[%06X] into lowest byte of register A\n", value, address);
    }

    public void WD(int address) {
        System.out.println("[EXEC] Executing WD at address: " + String.format("%06X", address));
        int value = rMgr.register[ResourceManager.REG_A] & 0xFF;
        System.out.printf("[WD] Wrote char %02X to device at address %06X\n", value, address);
    }

    public void RSUB(int address) {
        System.out.println("[EXEC] Executing RSUB at address: " + String.format("%06X", address));
        rMgr.register[ResourceManager.REG_PC] = rMgr.register[ResourceManager.REG_L];
        System.out.printf("[RSUB] Returning to address %06X from register L\n", rMgr.register[ResourceManager.REG_PC]);
    }

    // instruction 별로 동작을 수행하는 메소드를 정의
    // ex) public void add(){...}
}