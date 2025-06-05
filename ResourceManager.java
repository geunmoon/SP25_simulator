package SP25_simulator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * ResourceManager는 컴퓨터의 가상 리소스들을 선언하고 관리하는 클래스이다. 크게 네가지의 가상 자원 공간을 선언하고, 이를
 * 관리할 수 있는 함수들을 제공한다.
 *
 *
 * 1) 입출력을 위한 외부 장치 또는 device 2) 프로그램 로드 및 실행을 위한 메모리 공간. 여기서는 64KB를 최대값으로 잡는다.
 * 3) 연산을 수행하는데 사용하는 레지스터 공간. 4) SYMTAB 등 simulator의 실행 과정에서 사용되는 데이터들을 위한 변수들.
 *
 * 2번은 simulator위에서 실행되는 프로그램을 위한 메모리공간인 반면, 4번은 simulator의 실행을 위한 메모리 공간이라는 점에서
 * 차이가 있다.
 */
public class ResourceManager {
	public ArrayList<String> executionLog = new ArrayList<>();
	public int currentInstructionIndex = -1;
	public int lastExecutedAddress = -1;
	public Integer lastDeviceAddress = null;
	public Integer lastEffectiveAddress=null;
	public String lastUsedDeviceName = "";
	public SicLoader sicLoader; // will be assigned externally
	public HashMap<Integer, String> instructionTable = new HashMap<>();
	public HashMap<String, FileInputStream> rdDeviceStreams = new HashMap<>();
	public HashMap<String, FileOutputStream> wrDeviceStreams = new HashMap<>();

	public static class InstructionEntry {
		public int address;
		public String hexCode;
		public String mnemonic;
		public int opcode;
		public int locctr;
		public int nixbpe;

		public InstructionEntry(int address, String hexCode, String mnemonic, int opcode, int locctr, int nixbpe) {
			this.address = address;
			this.hexCode = hexCode;
			this.mnemonic = mnemonic;
			this.opcode = opcode;
			this.locctr = locctr;
			this.nixbpe = nixbpe;
		}

		@Override
		public String toString() {
			return String.format("@%06X : %s | %-6s (opcode=%02X, locctr=%06X)", address, hexCode, mnemonic, opcode, locctr);
		}
	}

	public ArrayList<InstructionEntry> debugInstructionList = new ArrayList<>();
	public javax.swing.JTextArea logArea;
	public String programName;
	public int programStartAddr;
	public int programLength;
	public int firstInstructionAddr;
	public int memoryStartAddr;
	public VisualSimulator visualSimulator;
	public javax.swing.DefaultListModel<String> instructionListModel = new javax.swing.DefaultListModel<>();
	/**
	 * 디바이스는 원래 입출력 장치들을 의미 하지만 여기서는 파일로 디바이스를 대체한다. 즉, 'F1'이라는 디바이스는 'F1'이라는 이름의
	 * 파일을 의미한다. deviceManager는 디바이스의 이름을 입력받았을 때 해당 이름의 파일 입출력 관리 클래스를 리턴하는 역할을 한다.
	 * 예를 들어, 'A1'이라는 디바이스에서 파일을 read모드로 열었을 경우, hashMap에 <"A1", scanner(A1)> 등을
	 * 넣음으로서 이를 관리할 수 있다.
	 * <p>
	 * 변형된 형태로 사용하는 것 역시 허용한다. 예를 들면 key값으로 String대신 Integer를 사용할 수 있다. 파일 입출력을 위해
	 * 사용하는 stream 역시 자유로이 선택, 구현한다.
	 * <p>
	 * 이것도 복잡하면 알아서 구현해서 사용해도 괜찮습니다.
	 */
	HashMap<String, Object> deviceManager = new HashMap<String, Object>();
	char[] memory = new char[65536]; // String으로 수정해서 사용하여도 무방함.
	int[] register = new int[10];
	double register_F;

	public static final int REG_A = 0;
	public static final int REG_X = 1;
	public static final int REG_L = 2;
	public static final int REG_B = 3;
	public static final int REG_S = 4;
	public static final int REG_T = 5;
	public static final int REG_PC = 8;
	public static final int REG_SW = 9;

	SymbolTable symtabList;
	// 이외에도 필요한 변수 선언해서 사용할 것.

	/**
	 * 메모리, 레지스터등 가상 리소스들을 초기화한다.
	 */
	public void initializeResource() {
		Arrays.fill(memory, (char) 0xFF);
		Arrays.fill(register, 0);
		register[REG_L] = 0xFFFFFF;
		register_F = 0.0;

		instructionTable.clear();
		instructionTable.put(0x18, "ADD");
		instructionTable.put(0x58, "ADDF");
		instructionTable.put(0x90, "ADDR");
		instructionTable.put(0x40, "AND");
		instructionTable.put(0xB4, "CLEAR");
		instructionTable.put(0x28, "COMP");
		instructionTable.put(0x88, "COMPF");
		instructionTable.put(0xA0, "COMPR");
		instructionTable.put(0x24, "DIV");
		instructionTable.put(0x64, "DIVF");
		instructionTable.put(0x9C, "DIVR");
		instructionTable.put(0xC4, "FIX");
		instructionTable.put(0xC0, "FLOAT");
		instructionTable.put(0xF4, "HIO");
		instructionTable.put(0x3C, "J");
		instructionTable.put(0x30, "JEQ");
		instructionTable.put(0x34, "JGT");
		instructionTable.put(0x38, "JLT");
		instructionTable.put(0x48, "JSUB");
		instructionTable.put(0x00, "LDA");
		instructionTable.put(0x68, "LDB");
		instructionTable.put(0x50, "LDCH");
		instructionTable.put(0x70, "LDF");
		instructionTable.put(0x08, "LDL");
		instructionTable.put(0x6C, "LDS");
		instructionTable.put(0x74, "LDT");
		instructionTable.put(0x04, "LDX");
		instructionTable.put(0xD0, "LPS");
		instructionTable.put(0x20, "MUL");
		instructionTable.put(0x60, "MULF");
		instructionTable.put(0x98, "MULR");
		instructionTable.put(0xC8, "NORM");
		instructionTable.put(0x44, "OR");
		instructionTable.put(0xD8, "RD");
		instructionTable.put(0xAC, "RMO");
		instructionTable.put(0x4C, "RSUB");
		instructionTable.put(0xA4, "SHIFTL");
		instructionTable.put(0xA8, "SHIFTR");
		instructionTable.put(0xF0, "SIO");
		instructionTable.put(0xEC, "SSK");
		instructionTable.put(0x0C, "STA");
		instructionTable.put(0x78, "STB");
		instructionTable.put(0x54, "STCH");
		instructionTable.put(0x80, "STF");
		instructionTable.put(0xD4, "STI");
		instructionTable.put(0x14, "STL");
		instructionTable.put(0x7C, "STS");
		instructionTable.put(0xE8, "STSW");
		instructionTable.put(0x84, "STT");
		instructionTable.put(0x10, "STX");
		instructionTable.put(0x1C, "SUB");
		instructionTable.put(0x5C, "SUBF");
		instructionTable.put(0x94, "SUBR");
		instructionTable.put(0xB0, "SVC");
		instructionTable.put(0xE0, "TD");
		instructionTable.put(0xF8, "TIO");
		instructionTable.put(0x2C, "TIX");
		instructionTable.put(0xB8, "TIXR");
		instructionTable.put(0xDC, "WD");

		if (visualSimulator != null) {
			visualSimulator.update();
		}
	}

	/**
	 * deviceManager가 관리하고 있는 파일 입출력 stream들을 전부 종료시키는 역할. 프로그램을 종료하거나 연결을 끊을 때
	 * 호출한다.
	 */
	public void closeDevice() {

	}

	/**
	 * 디바이스를 사용할 수 있는 상황인지 체크. TD명령어를 사용했을 때 호출되는 함수. 입출력 stream을 열고 deviceManager를
	 * 통해 관리시킨다.
	 *
	 * @param devName 확인하고자 하는 디바이스의 번호,또는 이름
	 */
	public void testDevice(String devName) {
		File deviceFile = new File(devName);
		if (deviceFile.exists()) {
			System.out.printf("[TD] Device file '%s' found → SW = 1\n", devName);
			register[REG_SW] = 1;
		} else {
			System.out.printf("[TD] Device file '%s' not found → SW = 0\n", devName);
			register[REG_SW] = 0;
		}
	}

	/**
	 * 디바이스로부터 원하는 개수만큼의 글자를 읽어들인다. RD명령어를 사용했을 때 호출되는 함수.
	 *
	 * @param devName 디바이스의 이름
	 * @param num     가져오는 글자의 개수
	 * @return 가져온 데이터
	 */
	public char[] readDevice(String devName, int num) {
		File file = new File(devName);
		if (!file.exists() || !file.isFile()) {
			System.out.printf("[RD] Device file '%s' not found.\n", devName);
			return new char[0];
		}

		char[] buffer = new char[num];
		try (java.io.FileReader fr = new java.io.FileReader(file)) {
			int readCount = fr.read(buffer, 0, num);
			if (readCount < num) {
				buffer = java.util.Arrays.copyOf(buffer, readCount);
			}
			System.out.printf("[RD] Read %d characters from device '%s': %s\n", buffer.length, devName, new String(buffer));
		} catch (java.io.IOException e) {
			System.out.printf("[RD] Error reading device '%s': %s\n", devName, e.getMessage());
			return new char[0];
		}

		return buffer;
	}

	/**
	 * 디바이스로 원하는 개수 만큼의 글자를 출력한다. WD명령어를 사용했을 때 호출되는 함수.
	 *
	 * @param devName 디바이스의 이름
	 * @param data    보내는 데이터
	 * @param num     보내는 글자의 개수
	 */
	public void writeDevice(String devName, char[] data, int num) {

	}

	/**
	 * 메모리의 특정 위치에서 원하는 개수만큼의 글자를 가져온다.
	 *
	 * @param location 메모리 접근 위치 인덱스
	 * @param num      데이터 개수
	 * @return 가져오는 데이터
	 */
	public char[] getMemory(int location, int num) {
		char[] result = new char[num];
		for (int i = 0; i < num; i++) {
			result[i] = memory[location + i];
		}
		return result;
	}

	/**
	 * 메모리의 특정 위치에 원하는 개수만큼의 데이터를 저장한다.
	 *
	 * @param locate 접근 위치 인덱스
	 * @param data   저장하려는 데이터
	 * @param num    저장하는 데이터의 개수
	 */
	public void setMemory(int locate, char[] data, int num) {
		for (int i = 0; i < num; i++) {
			memory[locate + i] = data[i];
		}
	}

	/**
	 * 번호에 해당하는 레지스터가 현재 들고 있는 값을 리턴한다. 레지스터가 들고 있는 값은 문자열이 아님에 주의한다.
	 *
	 * @param regNum 레지스터 분류번호
	 * @return 레지스터가 소지한 값
	 */
	public int getRegister(int regNum) {
		return 0;

	}

	/**
	 * 번호에 해당하는 레지스터에 새로운 값을 입력한다. 레지스터가 들고 있는 값은 문자열이 아님에 주의한다.
	 *
	 * @param regNum 레지스터의 분류번호
	 * @param value  레지스터에 집어넣는 값
	 */
	public void setRegister(int regNum, int value) {
		register[regNum] = value;
	}

	/**
	 * 주로 레지스터와 메모리간의 데이터 교환에서 사용된다. int값을 char[]형태로 변경한다.
	 *
	 * @param data
	 * @return
	 */
	public char[] intToChar(int data) {
		return null;
	}

	/**
	 * 주로 레지스터와 메모리간의 데이터 교환에서 사용된다. char[]값을 int형태로 변경한다.
	 *
	 * @param data
	 * @return
	 */
	public int byteToInt(byte[] data) {
		return 0;
	}

	/**
	 * 주어진 opcode 값에 해당하는 mnemonic 문자열을 반환한다.
	 *
	 * @param opcode 명령어 코드 (ni 비트 제거된 상태)
	 * @return mnemonic 문자열, 없으면 "UNKNOWN"
	 */
	public String getMnemonic(int opcode) {
		return instructionTable.getOrDefault(opcode, "UNKNOWN");
	}

	/**
	 * 로그를 추가하고 logArea에도 출력한다.
	 */
	public void addLog(String log) {
		if (executionLog == null) {
			executionLog = new ArrayList<>();
		}
		executionLog.add(log);
		if (logArea != null) {
			logArea.append(log + "\n");
			if (visualSimulator != null) {
				visualSimulator.updateLogDisplay();
			}
		}
	}
}