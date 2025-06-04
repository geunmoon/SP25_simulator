package SP25_simulator;

import java.io.File;
import java.util.Set;
import java.util.HashSet;

/**
 * SicLoader는 프로그램을 해석해서 메모리에 올리는 역할을 수행한다. 이 과정에서 linker의 역할 또한 수행한다.
 *
 * SicLoader가 수행하는 일을 예를 들면 다음과 같다. - program code를 메모리에 적재시키기 - 주어진 공간만큼 메모리에 빈
 * 공간 할당하기 - 과정에서 발생하는 symbol, 프로그램 시작주소, control section 등 실행을 위한 정보 생성 및 관리
 */
public class SicLoader {
	ResourceManager rMgr;

	public SicLoader(ResourceManager resourceManager) {
		// 필요하다면 초기화
		setResourceManager(resourceManager);
	}

	/**
	 * Loader와 프로그램을 적재할 메모리를 연결시킨다.
	 *
	 * @param rMgr
	 */
	public void setResourceManager(ResourceManager resourceManager) {
		this.rMgr = resourceManager;
	}

	/**
	 * object code를 읽어서 load과정을 수행한다. load한 데이터는 resourceManager가 관리하는 메모리에 올라가도록
	 * 한다. load과정에서 만들어진 symbol table 등 자료구조 역시 resourceManager에 전달한다.
	 *
	 * @param objectCode 읽어들인 파일
	 */
	public void load(File objectCode) {
		rMgr.initializeResource();
		int sectionStartAddr = 0;
		int currentAddr = 0; // Track the next available memory address for each new control section
		java.util.List<String> refSymbols = new java.util.ArrayList<>();
		try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(objectCode))) {
			String line;
			// Add EOF/RSUB flags
			boolean seenEOF = false;
			boolean seenRSUB = false;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("H")) {
					// Reset flags for new section
					seenEOF = false;
					seenRSUB = false;
					if (line.length() < 19) {
						System.out.println("[H] 레코드가 너무 짧습니다: " + line);
						continue;
					}
					String progName = line.substring(1, 7).trim();
					int startAddr = Integer.parseInt(line.substring(7, 13).trim(), 16);
					int length = Integer.parseInt(line.substring(13, 19).trim(), 16);
					System.out.printf("[H] Program: %s, Start Address: %06X, Length: %06X%n", progName, startAddr, length);
					sectionStartAddr = currentAddr;
					currentAddr += length;
					System.out.printf("[H] Section loaded at physical address: %06X ~ %06X%n", sectionStartAddr, currentAddr - 1);
					// Set starting address and initialize memory block (if needed)
					// Placeholder: memory start, length — user can extend
					// No built-in support in ResourceManager for this yet
					if (rMgr.symtabList == null)
						rMgr.symtabList = new SymbolTable();
					rMgr.symtabList.putSymbol(progName, sectionStartAddr);
					System.out.printf("[SYM] %s → %06X\n", progName, sectionStartAddr);
					if (rMgr.programName == null || rMgr.programName.isEmpty()) {
					    rMgr.programName = progName;
						rMgr.programStartAddr = startAddr;
						rMgr.firstInstructionAddr = startAddr;
					}
					rMgr.programLength += length;
				} else if (line.startsWith("D")) {
					if (line.length() < 14) {
						System.out.println("[D] 레코드가 너무 짧습니다: " + line);
						continue;
					}
					for (int i = 1; i + 12 <= line.length(); i += 12) {
						String sym = line.substring(i, i + 6).trim();
						int addr = Integer.parseInt(line.substring(i + 6, i + 12).trim(), 16);
						if (rMgr.symtabList == null)
							rMgr.symtabList = new SymbolTable();
						rMgr.symtabList.putSymbol(sym, addr);
						System.out.printf("[D] Symbol: %-6s → %06X%n", sym, addr);
					}
				} else if (line.startsWith("R")) {
					refSymbols.clear(); // clear previous section's refs
					System.out.print("[R] Reference symbols:");
					for (int i = 1; i + 6 <= line.length(); i += 6) {
						String ref = line.substring(i, i + 6).trim();
						refSymbols.add(ref);
						System.out.print(" " + ref);
					}
					System.out.println();
				} else if (line.startsWith("T")) {
					if (line.length() < 12) {
						System.out.println("[T] 레코드가 너무 짧습니다: " + line);
						continue;
					}
					int loc = Integer.parseInt(line.substring(1, 7).trim(), 16);
					int length = Integer.parseInt(line.substring(7, 9).trim(), 16);
					String objCodes = line.substring(9).trim();

					for (int i = 0; i < objCodes.length();) {
					    if (i + 2 > objCodes.length()) break;

					    int opcodeRaw = Integer.parseInt(objCodes.substring(i, i + 2), 16);
					    boolean extended = false;
					    if (i + 3 <= objCodes.length()) {
					        extended = (Integer.parseInt(objCodes.substring(i + 2, i + 3), 16) & 0x1) == 1;
					    }
					    int opcode = opcodeRaw & 0xFC;  // Remove ni bits (last 2 bits)
					    int format = getInstructionFormat(opcode, extended);
					    System.out.printf("[T][DEBUG] @%06X: rawOpcode=%02X opcode=%02X format=%d extended=%b\n",
					            sectionStartAddr + loc + (i / 2), opcodeRaw, opcode, format, extended);
					    // Warn if opcode is 00, which might indicate WORD/BYTE data, not an instruction
					    if (opcode == 0x00) {
					        System.out.printf("[T][WARN] opcode=00 at @%06X → 이건 명령어가 아니라 WORD나 BYTE일 수 있음\n", loc + (i / 2));
					    }
					    int byteLen = switch (format) {
					        case 1 -> 1;
					        case 2 -> 2;
					        case 3 -> 3;
					        case 4 -> 4;
					        default -> 3;
					    };
					    int totalHexChars = byteLen * 2;

					    if (i + totalHexChars > objCodes.length()) {
					        System.out.printf("[T][WARN] Incomplete instruction at offset %d: expected %d chars, only %d remaining. Skipping.\n", i, totalHexChars, objCodes.length() - i);
					        //break;
					    }

					    for (int j = 0; j < totalHexChars; j += 2) {
					        if (i + j + 2 > objCodes.length()) break;
					        char[] byteData = new char[1];
					        byteData[0] = (char) Integer.parseInt(objCodes.substring(i + j, i + j + 2), 16);
					        int addr = sectionStartAddr + loc + (i / 2) + (j / 2);
					        rMgr.setMemory(addr, byteData, 1);
					        System.out.printf("[T] Memory[%06X] ← %02X%n", addr, (int) byteData[0]);
					    }
					    i += totalHexChars;
					}
				} else if (line.startsWith("M")) {
					// Do nothing here; modification records should be processed after symbol table creation.
					System.out.println("[M] 레코드 읽음: " + line);
				} else if (line.startsWith("E")) {
					if (line.trim().length() == 1) {
						System.out.println("[E] 실행 시작 주소 생략됨 (기본값 사용 안함)");
						continue;
					}
					int execAddr = Integer.parseInt(line.substring(1).trim(), 16);
					rMgr.setRegister(8, execAddr);
					System.out.printf("[E] Start execution at address: %06X%n", execAddr);
				}
			}
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
		rMgr.memoryStartAddr = 0;
		if (rMgr.visualSimulator != null) {
		    rMgr.visualSimulator.update();
		}
		// 심볼 테이블 출력 확인용
		System.out.println("[Symbol Table]");
		for (int i = 0; rMgr.symtabList != null && i < rMgr.symtabList.symbolList.size(); i++) {
			String sym = rMgr.symtabList.symbolList.get(i);
			int addr = rMgr.symtabList.addressList.get(i);
			System.out.printf("[SYM] %s → %06X\n", sym, addr);
		}
	}

	private int getInstructionFormat(int opcode, boolean extended) {
		// Format 1 opcodes
		switch (opcode) {
			case 0xC4: // FIX
			case 0xC0: // FLOAT
			case 0xF4: // HIO
			case 0xC8: // NORM
			case 0xF0: // SIO
			case 0xF8: // TIO
				return 1;
		}

		// Format 2 opcodes
		switch (opcode) {
			case 0x90: // ADDR
			case 0x9C: // DIVR
			case 0xA0: // COMPR
			case 0xA4: // SHIFTL
			case 0xA8: // SHIFTR
			case 0xAC: // RMO
			case 0xB0: // SVC
			case 0xB4: // CLEAR
			case 0xB8: // TIXR
				return 2;
		}

		// Format 3/4 opcodes (extended determines final format)
		if (extended) return 4;
		return 3;
	}
	/**
	 * 심볼 테이블 생성 이후에만 modification(M) 레코드를 처리한다.
	 */
	public void modification(File objectCode) {
		if (rMgr.debugInstructionList == null)
		    rMgr.debugInstructionList = new java.util.ArrayList<>();
		try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(objectCode))) {
			String line;
			int sectionStartAddr = 0;
			StringBuilder sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("H")) {
					String name = line.substring(1, 7).trim();
					sectionStartAddr = rMgr.symtabList.search(name);
				} else if (line.startsWith("M")) {
					int addr = Integer.parseInt(line.substring(1, 7).trim(), 16);
					int len = Integer.parseInt(line.substring(7, 9).trim(), 16); // in half-bytes
					char sign = line.charAt(9);
					String symbol = line.substring(10).trim();

					int modAddr = sectionStartAddr + addr;
					int modLen = (len + 1) / 2;  // in bytes

					// read original value
					byte[] bbuf = new byte[modLen];
					for (int i = 0; i < modLen; i++) {
						bbuf[i] = (byte) rMgr.memory[modAddr + i];
					}
					int original = 0;
					for (int i = 0; i < modLen; i++) {
						original = (original << 8) | (bbuf[i] & 0xFF);
					}

					System.out.print("[M] bbuf (raw bytes): ");
					for (int i = 0; i < bbuf.length; i++) {
						System.out.printf("%02X ", bbuf[i]);
					}
					System.out.println();
					System.out.printf("[M] original int from bbuf: %08X\n", original);

					int symbolValue = rMgr.symtabList.search(symbol);
					if (symbolValue == -1) {
						System.err.printf("[M] ERROR: Undefined symbol '%s'%n", symbol);
						continue;
					}

					// Debug output for original value and symbol address
					System.out.printf("[M] original value @%06X (%d bytes): %08X\n", modAddr, modLen, original);
					System.out.printf("[M] symbol '%s' address: %06X\n", symbol, symbolValue);

					int result = (sign == '+') ? original + symbolValue : original - symbolValue;

					// write result, and record modified instructions
					sb.setLength(0); // reset
					int modVal = result;
					for (int i = 0; i < modLen; i++) {
						int byteVal = (modVal >> ((modLen - 1 - i) * 8)) & 0xFF;
						char[] oneByte = new char[1];
						oneByte[0] = (char) byteVal;
						rMgr.setMemory(modAddr + i, oneByte, 1);
						sb.append(String.format("%02X ", byteVal));
						System.out.printf("[M] Memory[%06X] ← %02X\n", modAddr + i, byteVal);
					}
					// Debug print for 3-byte triplet after modification
					System.out.printf("[MOD][DEBUG] Memory[%06X] ← %02X %02X %02X (after modification)%n",
						modAddr,
						(int) rMgr.memory[modAddr] & 0xFF,
						(int) rMgr.memory[modAddr + 1] & 0xFF,
						(int) rMgr.memory[modAddr + 2] & 0xFF);

					// --- Insert instruction format logic and memory byte extraction, similar to load() ---
					int opcodeRaw = rMgr.memory[modAddr] & 0xFF;
					boolean extended = false;
					// Heuristic: if length is 4 bytes, treat as format 4 (for extended flag)
					int opcode = opcodeRaw & 0xFC;
					int format = 3;
					if (modLen == 4) extended = true;
					format = getInstructionFormat(opcode, extended);
					int byteLen = switch (format) {
						case 1 -> 1;
						case 2 -> 2;
						case 3 -> 3;
						case 4 -> 4;
						default -> 3;
					};

					StringBuilder sbInstr = new StringBuilder();
				}
			}
			// After all modification records have been applied, append all valid instructions to instructionListModel
			if (rMgr.instructionListModel != null) {
			    Set<Integer> seen = new HashSet<>();
			    for (int i = 0; i < 0x1100;) {
			        int byte1 = rMgr.memory[i] & 0xFF;
			        if (byte1 == 0xFF || byte1 == 0xF1 || byte1 == 0x05 || seen.contains(i)) {
			            i++;
			            continue;
			        }
			        if (i + 2 < 0x1100 &&
			            (rMgr.memory[i] & 0xFF) == 0x00 &&
			            (rMgr.memory[i + 1] & 0xFF) == 0x10 &&
			            (rMgr.memory[i + 2] & 0xFF) == 0x00) {
			            i += 3;
			            continue;
			        }
			        if (i + 2 < 0x1100 &&
			            (rMgr.memory[i] & 0xFF) == 0x45 &&
			            (rMgr.memory[i + 1] & 0xFF) == 0x4F &&
			            (rMgr.memory[i + 2] & 0xFF) == 0x46) {
			            i += 3;
			            continue;
			        }

			        int strippedOpcode = byte1 & 0xFC;
			        boolean extended = (i + 1 < 0x1100) && ((rMgr.memory[i + 1] & 0x10) != 0);
			        int format = getInstructionFormat(strippedOpcode, extended);
			        int length = switch (format) {
			            case 1 -> 1;
			            case 2 -> 2;
			            case 3 -> 3;
			            case 4 -> 4;
			            default -> 3;
			        };

			        StringBuilder sb2 = new StringBuilder();
			        for (int j = 0; j < length; j++) {
			            int addr = i + j;
			            if (addr >= 0x1100 || rMgr.memory[addr] == (char) 0xFF) break;
			            sb2.append(String.format("%02X ", (int) rMgr.memory[addr] & 0xFF));
			            seen.add(addr);
			        }

			        String mnemonic = rMgr.getMnemonic(strippedOpcode);
			        ResourceManager.InstructionEntry debugEntry = new ResourceManager.InstructionEntry(i, sb2.toString().replace(" ", ""), mnemonic, strippedOpcode, i);
			        rMgr.debugInstructionList.add(debugEntry);
			        ResourceManager.InstructionEntry entry = new ResourceManager.InstructionEntry(i, sb2.toString().replace(" ", ""), mnemonic, strippedOpcode, i);
			        System.out.printf("[MOD][DEBUG] %04X : %s | %-6s (opcode=%02X)%n",
			            i, entry.hexCode, entry.mnemonic, entry.opcode);
			        String formatted = String.format("%04X : %s", i, sb2.toString().replace(" ", ""));
			        rMgr.instructionListModel.addElement(formatted);
			        i += length;
			    }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Utility for modification: fixed-size int to char array (big-endian, sign-extended)
	private char[] intToCharFixed(int value, int size) {
		char[] arr = new char[size];
		for (int i = size - 1; i >= 0; i--) {
			arr[i] = (char) (value & 0xFF);
			value >>= 8;
		}
		return arr;
	}

	public static class InstructionEntry {
	    public int address;
	    public String hexCode;
	    public String mnemonic;
	    public int opcode;
	    public int locctr;

	    public InstructionEntry(int address, String hexCode, String mnemonic, int opcode, int locctr) {
	        this.address = address;
	        this.hexCode = hexCode;
	        this.mnemonic = mnemonic;
	        this.opcode = opcode;
	        this.locctr = locctr;
	    }
	}
}