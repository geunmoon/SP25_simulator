package SP25_simulator;

import java.io.File;

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
	 * 1개의 instruction이 수행된 모습을 보인다.
	 */
	public void oneStep() {
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

	    int increment = switch (entry.hexCode.length() / 2) {
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

	    switch (entry.mnemonic.toUpperCase()) {
	        case "STL" -> inst.STL(entry.address);
	        case "JSUB" -> inst.JSUB(entry.address);
	        case "LDA" -> inst.LDA(entry.address);
	        case "COMP" -> inst.COMP(entry.address);
	        case "JEQ" -> inst.JEQ(entry.address);
	        case "J" -> inst.J(entry.address);
	        case "STA" -> inst.STA(entry.address);
	        case "CLEAR" -> inst.CLEAR(entry.address);
	        case "LDT" -> inst.LDT(entry.address);
	        case "TD" -> inst.TD(entry.address);
	        case "RD" -> inst.RD(entry.address);
	        case "COMPR" -> inst.COMPR(entry.address);
	        case "STCH" -> inst.STCH(entry.address);
	        case "TIXR" -> inst.TIXR(entry.address);
	        case "JLT" -> inst.JLT(entry.address);
	        case "STX" -> inst.STX(entry.address);
	        case "LDCH" -> inst.LDCH(entry.address);
	        case "WD" -> inst.WD(entry.address);
	        case "RSUB" -> inst.RSUB(entry.address);
	        default -> System.out.printf("[DEBUG] Unknown instruction mnemonic '%s' at %04X\n", entry.mnemonic, entry.address);
	    }

	    // entry.address is effectively the LOCCTR (Location Counter) for this instruction
	    // LOCCTR = entry.address
	    // PC = LOCCTR + format length

//	    rMgr.currentInstructionIndex++;
//	    System.out.printf("[DEBUG] currentInstructionIndex is now %d\n", rMgr.currentInstructionIndex);
	    if (rMgr.visualSimulator != null) {
	        rMgr.visualSimulator.update();
	    }
	}

	/**
	 * 남은 모든 instruction이 수행된 모습을 보인다.
	 */
	public void allStep() {
	}

	/**
	 * 각 단계를 수행할 때 마다 관련된 기록을 남기도록 한다.
	 */
	public void addLog(String log) {
	}
}
