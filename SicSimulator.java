package SP25_simulator;

import java.io.File;

import static SP25_simulator.ResourceManager.REG_L;

/**
 * 시뮬레이터로서의 작업을 담당한다. VisualSimulator에서 사용자의 요청을 받으면 이에 따라 ResourceManager에 접근하여
 * 작업을 수행한다.
 *
 * 작성중의 유의사항 : 1) 새로운 클래스, 새로운 변수, 새로운 함수 선언은 얼마든지 허용됨. 단, 기존의 변수와 함수들을 삭제하거나
 * 완전히 대체하는 것은 지양할 것. 2) 필요에 따라 예외처리, 인터페이스 또는 상속 사용 또한 허용됨. 3) 모든 void 타입의 리턴값은
 * 유저의 필요에 따라 다른 리턴 타입으로 변경 가능. 4) 파일, 또는 콘솔창에 한글을 출력시키지 말 것. (채점상의 이유. 주석에 포함된
 * 한글은 상관 없음)
 *
 *
 *
 * + 제공하는 프로그램 구조의 개선방법을 제안하고 싶은 분들은 보고서의 결론 뒷부분에 첨부 바랍니다. 내용에 따라 가산점이 있을 수
 * 있습니다.
 */
public class SicSimulator {
	ResourceManager rMgr;

	public SicSimulator(ResourceManager resourceManager) {
		// 필요하다면 초기화 과정 추가
		this.rMgr = resourceManager;
	}

	/**
	 * 레지스터, 메모리 초기화 등 프로그램 load와 관련된 작업 수행. 단, object code의 메모리 적재 및 해석은
	 * SicLoader에서 수행하도록 한다.
	 */
	public void load(File program) {
		// 레지스터 초기화
		for (int i = 0; i < rMgr.register.length; i++) {
			rMgr.register[i] = 0;
			System.out.printf("[DEBUG] Register[%d] initialized to 0\n", i);
		}
		rMgr.register_F = 0.0;
		rMgr.register[REG_L] = 0xFFFFFF;
		System.out.println("[DEBUG] Register F initialized to 0.0");

		// 프로그램 이름, 시작 주소, 길이 설정
		rMgr.programStartAddr = 0;
		rMgr.firstInstructionAddr = 0;
		rMgr.memoryStartAddr = 0;
		System.out.printf("[DEBUG] Program Name %S\n", rMgr.programName);
		System.out.printf("[DEBUG] Program Start Address: %06X\n", rMgr.programStartAddr);
		System.out.printf("[DEBUG] Program Length: %06X\n", rMgr.programLength);
		System.out.printf("[DEBUG] First Instruction Address: %06X\n", rMgr.firstInstructionAddr);
		System.out.printf("[DEBUG] Memory Start Address: %d\n", rMgr.memoryStartAddr);

		rMgr.currentInstructionIndex = -1;

		// 화면 갱신
		if (rMgr.visualSimulator != null) {
			System.out.println("[DEBUG] Calling visualSimulator.update()");
			rMgr.visualSimulator.update(); // 필요시 null 체크
		}

	}

	/**
	 * 주어진 opcode와 extended 플래그를 기준으로 명령어 형식을 판단한다.
	 *
	 * @param opcode   실제 명령어에서 ni 비트를 제외한 순수 opcode 값 (e.g., 0x14)
	 * @param extended extended format 여부 (+가 붙은 경우 true)
	 * @return 1~4 중의 형식 번호
	 */
	private int getInstructionFormat(int opcode, boolean extended) {
		// Format 1 instructions
		int[] format1Opcodes = {0xC4, 0xC0, 0xF4, 0xC8, 0xF0, 0xF8, 0xF1};
		for (int f1 : format1Opcodes) {
			if (opcode == f1) return 1;
		}

		// Format 2 instructions
		int[] format2Opcodes = {0x90, 0xB4, 0xA0, 0x9C, 0x98, 0xAC, 0xA4, 0xA8, 0xB0, 0xB8};
		for (int f2 : format2Opcodes) {
			if (opcode == f2) return 2;
		}

		// Format 4 (extended) if flag is set
		if (extended) return 4;

		// Default to Format 3
		return 3;
	}

	/**
	 * 1개의 instruction이 수행된 모습을 보인다.
	 */
	public void oneStep() {
		// If next PC is FFFFFF, log that the simulation is ending
		if (rMgr.register[ResourceManager.REG_PC] == 0xFFFFFF) {
			rMgr.lastEffectiveAddress = null; // clear target address display
			addLog("Simulation finished. No more instructions to execute.");
		}
		int pc = rMgr.register[ResourceManager.REG_PC];
		ResourceManager.InstructionEntry entry = null;
		for (ResourceManager.InstructionEntry e : rMgr.debugInstructionList) {
			if (e.address == pc) {
				entry = e;
				break;
			}
		}
		if (entry == null) {
			System.out.printf("[DEBUG] No instruction found at PC = %06X\n", pc);
			return;
		}

		int increment = switch (entry.hexCode.replaceAll(" ", "").length() / 2) {
			case 1 -> 1;
			case 2 -> 2;
			case 3 -> 3;
			case 4 -> 4;
			default -> 3;
		};
		rMgr.register[ResourceManager.REG_PC] = entry.address + increment; // Update PC before logging

		System.out.printf(
				"[DEBUG] Executing Instruction[%d]: LOCCTR=%04X, PC=%06X : %s | %-6s (opcode=%02X)\n",
				rMgr.debugInstructionList.indexOf(entry),
				entry.address,
				rMgr.register[ResourceManager.REG_PC],
				entry.hexCode,
				entry.mnemonic,
				entry.opcode
		);

		InstLuncher inst = new InstLuncher(rMgr);
		// Determine instruction format
		boolean extended = false;
		if (entry.hexCode.length() >= 3) {
			int flagByte = Integer.parseInt(entry.hexCode.substring(2, 3), 16);
			extended = (flagByte & 0x1) == 1;
		}
		int format = getInstructionFormat(entry.opcode, extended);
		System.out.printf("[DEBUG] Determined format = %d for opcode=%02X (extended=%b)\n", format, entry.opcode, extended);

		// Parse nixbpe flags from instruction bytes if format >= 3
		int nixbpe = -1;
		if (format >= 3 && entry.hexCode.length() >= 4) {
			String hex = entry.hexCode.replaceAll(" ", "");
			int byte1 = Integer.parseInt(hex.substring(0, 2), 16);  // first byte
			int byte2 = Integer.parseInt(hex.substring(2, 4), 16);  // second byte

			int n = (byte1 >> 1) & 0x1;
			int i = byte1 & 0x1;
			int x = (byte2 >> 7) & 0x1;
			int b = (byte2 >> 6) & 0x1;
			int p = (byte2 >> 5) & 0x1;
			int e = (byte2 >> 4) & 0x1;

			System.out.printf("[DEBUG] n=%d i=%d x=%d b=%d p=%d e=%d\n", n, i, x, b, p, e);
			// Optionally: you can still pack these bits into nixbpe if needed
			entry.nixbpe = (n << 5) | (i << 4) | (x << 3) | (b << 2) | (p << 1) | e;
		}

		// entry.address is effectively the LOCCTR (Location Counter) for this instruction
		// LOCCTR = entry.address
		// PC = LOCCTR + format length

		// Dispatch instruction based on mnemonic and format
		switch (entry.mnemonic.toUpperCase()) {
			// Format 1
			case "RSUB" -> inst.RSUB(entry);

			// Format 2
			case "CLEAR" -> inst.CLEAR(entry);
			case "COMPR" -> inst.COMPR(entry);
			case "TIXR" -> inst.TIXR(entry);

			// Format 3/4
			case "STL" -> inst.STL(entry);
			case "JSUB" -> inst.JSUB(entry);
			case "LDA" -> inst.LDA(entry);
			case "COMP" -> inst.COMP(entry);
			case "JEQ" -> inst.JEQ(entry);
			case "J" -> inst.J(entry);
			case "STA" -> inst.STA(entry);
			case "LDT" -> inst.LDT(entry);
			case "TD" -> inst.TD(entry);
			case "RD" -> inst.RD(entry);
			case "STCH" -> inst.STCH(entry);
			case "JLT" -> inst.JLT(entry);
			case "STX" -> inst.STX(entry);
			case "LDCH" -> inst.LDCH(entry);
			case "WD" -> inst.WD(entry);

			default -> System.out.printf("[DEBUG] Unknown mnemonic '%s' at %04X\n", entry.mnemonic, entry.address);
		}

		// Update lastEffectiveAddress using computed target address
		if (format >= 3 && entry.hexCode.length() >= 6) {
			int targetAddr = calculateTargetAddress(entry);
			int nFlag = (entry.nixbpe >> 5) & 0x1;
			int iFlag = (entry.nixbpe >> 4) & 0x1;
			if (nFlag == 0 && iFlag == 1) {
				rMgr.lastEffectiveAddress = null; // 즉시 상수이면 유효 주소 없음
			} else {
				rMgr.lastEffectiveAddress = targetAddr;
			}
		} else {
			rMgr.lastEffectiveAddress = null; // Format 1 or 2인 경우 유효 주소 없음
		}

		// For TD, RD, WD: set lastDeviceAddress using lastEffectiveAddress (computed by InstLuncher)
		if (entry.mnemonic.equalsIgnoreCase("TD") || entry.mnemonic.equalsIgnoreCase("RD") || entry.mnemonic.equalsIgnoreCase("WD")) {
			int targetAddr = rMgr.lastEffectiveAddress;
			rMgr.lastDeviceAddress = targetAddr;
		}

		addLog(String.format("Executed: %s at %06X", entry.mnemonic, entry.address));

		if (rMgr.visualSimulator != null) {
			rMgr.visualSimulator.update();
		}

	}

	/**
	 * 남은 모든 instruction이 수행된 모습을 보인다.
	 */
	public void allStep() {
		if (rMgr.visualSimulator == null) {
			System.err.println("[WARN] visualSimulator is null in ResourceManager before allStep(). GUI log updates may fail.");
		}
		while (true) {
			int pc = rMgr.register[ResourceManager.REG_PC];
			ResourceManager.InstructionEntry entry = null;
			for (ResourceManager.InstructionEntry e : rMgr.debugInstructionList) {
				if (e.address == pc) {
					entry = e;
					break;
				}
			}
			if (entry == null) {
				System.out.printf("[DEBUG] No instruction found at PC = %06X\n", pc);
				break;
			}

			// 종료 조건: 주소가 0xFFFFFF인 명령어를 실행하면 종료
			if (entry.address == 0xFFFFFF) {
				System.out.println("[ALLSTEP] Termination condition met at address FFFFFF.");
				break;
			}

			oneStep(); // Already prints and logs instruction execution
		}
	}

	/**
	 * 각 단계를 수행할 때 마다 관련된 기록을 남기도록 한다.
	 */
	public void addLog(String log) {
		rMgr.executionLog.add(log);
		if (rMgr.visualSimulator != null) {
			rMgr.visualSimulator.updateLogDisplay();
		}

	}

	/**
	 * 현재 실행 중인 명령어의 target address를 계산한다.
	 * 포맷 3/4 기준이며, format과 nixbpe 정보를 이용해 유효 주소를 해석한다.
	 */
	private int calculateTargetAddress(ResourceManager.InstructionEntry entry) {
		String hex = entry.hexCode.replaceAll(" ", "");
		int format = switch (hex.length() / 2) {
			case 4 -> 4;
			case 3 -> 3;
			default -> 3;
		};
		if (format < 3 || hex.length() < 6) return -1;

		int byte1 = Integer.parseInt(hex.substring(0, 2), 16);
		int byte2 = Integer.parseInt(hex.substring(2, 4), 16);
		int byte3 = Integer.parseInt(hex.substring(4, 6), 16);

		int n = (byte1 >> 1) & 0x1;
		int i = byte1 & 0x1;
		int x = (byte2 >> 7) & 0x1;
		int b = (byte2 >> 6) & 0x1;
		int p = (byte2 >> 5) & 0x1;
		int e = (byte2 >> 4) & 0x1;

		// No target address if this is an immediate constant (except when n==1) or RSUB
		if (((n == 0 && i == 1 && n != 1)) || entry.mnemonic.equalsIgnoreCase("RSUB")) {
			System.out.println("[DEBUG] No target address (immediate constant or RSUB)");
			return Integer.MIN_VALUE; // Indicate no address, to be handled by caller
		}

		int disp;
		if (e == 1 && hex.length() >= 8) {
			// format 4 uses 20-bit address
			disp = Integer.parseInt(hex.substring(3, 8), 16) & 0xFFFFF;
		} else {
			// format 3 uses 12-bit address (signed)
			disp = ((byte2 & 0xF) << 8) | byte3;
			if ((disp & 0x800) != 0) disp |= 0xFFFFF000; // sign-extend negative
		}

		int targetAddr = 0;
		if (e == 1) {
			targetAddr = disp;
		} else if (b == 1) {
			targetAddr = rMgr.register[ResourceManager.REG_B] + disp;
		} else if (p == 1) {
			int formatLength = (format == 4 ? 4 : 3);
			int pc = entry.address + formatLength; // determine PC from instruction location
			targetAddr = pc + disp;
		} else {
			targetAddr = disp;
		}

		if (x == 1) {
			targetAddr += rMgr.register[ResourceManager.REG_X];
		}

		System.out.printf("[DEBUG] Format: %d, e=%d, b=%d, p=%d, x=%d\n", format, e, b, p, x);
		System.out.printf("[DEBUG] disp: %06X (after sign-extension if any)\n", disp);
		System.out.printf("[DEBUG] targetAddr: %06X\n", targetAddr);

		return targetAddr & 0xFFFFFF;
	}
    /**
     * 프로그램이 종료되었는지 여부를 반환한다.
     * PC가 0xFFFFFF이면 종료 상태로 간주한다.
     */
    public boolean isHalted() {
        return rMgr.register[ResourceManager.REG_PC] == 0xFFFFFF;
    }
}
