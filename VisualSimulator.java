package SP25_simulator;

import javax.swing.*;
import java.awt.EventQueue;
import java.io.File;
import java.awt.FileDialog;

/**
 * VisualSimulator는 사용자와의 상호작용을 담당한다. 즉, 버튼 클릭등의 이벤트를 전달하고 그에 따른 결과값을 화면에 업데이트
 * 하는 역할을 수행한다.
 *
 * 실제적인 작업은 SicSimulator에서 수행하도록 구현한다.
 */
public class VisualSimulator {
    // GUI fields to resolve "Cannot resolve symbol" errors
    private javax.swing.JTextField fileField;
    private javax.swing.JTextField progNameField;
    private javax.swing.JTextField startAddrField;
    private javax.swing.JTextField lengthField;
    private javax.swing.JTextField endField;
	private javax.swing.JTextField memStartField;

    private javax.swing.JTextField[] regDecFields = new javax.swing.JTextField[10];
    private javax.swing.JTextField[] regHexFields = new javax.swing.JTextField[10];
    private javax.swing.JList<String> instructionList = new javax.swing.JList<>();
    // logArea will be set via resourceManager
	ResourceManager resourceManager = new ResourceManager();
	SicLoader sicLoader = new SicLoader(resourceManager);
	SicSimulator sicSimulator = new SicSimulator(resourceManager);

	public VisualSimulator() {
		resourceManager.visualSimulator = this;
		initializeGUI();
	}

	/**
	 * 프로그램 로드 명령을 전달한다.
	 */
	public void load(File program) {
		// Reset instruction highlight
		resourceManager.currentInstructionIndex = -1;
		sicLoader.load(program);           // T 레코드로 메모리 초기화
		sicLoader.modification(program);  // M 레코드로 메모리 수정
		sicSimulator.load(program);       // 수정된 메모리 기반으로 시뮬레이터 준비
		update();
		dumpMemory();
	}

	/**
	 * 하나의 명령어만 수행할 것을 SicSimulator에 요청한다.
	 */
	public void oneStep() {
	    // Save the last executed location counter before executing the next step
	    resourceManager.lastExecutedAddress = resourceManager.register[ResourceManager.REG_PC];
	    sicSimulator.oneStep();
	    update(); // 화면 갱신
	}

	/**
	 * 남아있는 모든 명령어를 수행할 것을 SicSimulator에 요청한다.
	 */
	public void allStep() {
	    sicSimulator.allStep();
	    update(); // 화면 갱신
	}

	/**
	 * Adds a log string to the execution log and updates the UI.
	 */
	public void addLog(String log) {
		resourceManager.executionLog.add(log);
		update(); // Refresh the UI to show the new log
		resourceManager.visualSimulator.updateLogDisplay();
	}

	/**
	 * 화면을 최신값으로 갱신하는 역할을 수행한다.
	 */
	public void updateLogDisplay() {
	    if (resourceManager.logArea != null && resourceManager.executionLog != null) {
	        StringBuilder logContent = new StringBuilder();
	        for (String line : resourceManager.executionLog) {
	            logContent.append(line).append("\n");
	        }
	        resourceManager.logArea.setText(logContent.toString());
	    }
	}

	public void update() {
		for (int i = 0; i < regDecFields.length; i++) {
			if (i == 6 || i == 9) { // F, SW → only hex
				if (regHexFields[i] != null) {
					if (i == 6)
						regHexFields[i].setText(String.format("%X", Double.doubleToRawLongBits(resourceManager.register_F)));
					else
						regHexFields[i].setText(String.format("%X", resourceManager.register[i]));
				}
			} else {
				if (regDecFields[i] != null)
					regDecFields[i].setText(String.valueOf(resourceManager.register[i]));
				if (regHexFields[i] != null)
					regHexFields[i].setText(String.format("%X", resourceManager.register[i]));
			}
		}

		if (progNameField != null)
		    progNameField.setText(resourceManager.programName);
		if (startAddrField != null)
		    startAddrField.setText(String.format("%06X", resourceManager.programStartAddr));
		if (lengthField != null)
		    lengthField.setText(String.format("%06X", resourceManager.programLength));
		if (endField != null)
		    endField.setText(String.format("%06X", resourceManager.firstInstructionAddr));
		// Update start address in memory text field if available
		if (memStartField != null)
			memStartField.setText(String.format("%d", resourceManager.memoryStartAddr));

        // Update execution log area
        if (resourceManager.logArea != null && resourceManager.executionLog != null) {
            StringBuilder logContent = new StringBuilder();
            for (String line : resourceManager.executionLog) {
                logContent.append(line).append("\n");
            }
            resourceManager.logArea.setText(logContent.toString());
        }

        // Ensure instruction list model updates the visible list
        if (instructionList != null && resourceManager.instructionListModel != null) {
            instructionList.setModel(resourceManager.instructionListModel);
            // Highlight: select instruction based on lastExecutedAddress
            int lastExecuted = resourceManager.lastExecutedAddress;
            int matchIndex = -1;
            for (int i = 0; i < resourceManager.debugInstructionList.size(); i++) {
                ResourceManager.InstructionEntry e = resourceManager.debugInstructionList.get(i);
                if (e.address == lastExecuted) {
                    matchIndex = i;
                    break;
                }
            }

            if (matchIndex == -1) {
                instructionList.clearSelection(); // prevent highlight if PC doesn't match any instruction
            } else {
                instructionList.setSelectedIndex(matchIndex);
                instructionList.ensureIndexIsVisible(matchIndex);
            }
        }
	}

	private void initializeGUI() {
		// GUI layout as specified
		javax.swing.SwingUtilities.invokeLater(() -> {
			javax.swing.JFrame frame = new javax.swing.JFrame("SIC/XE Simulator");
			frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
			frame.setSize(780, 720);
			frame.setLayout(null);

			javax.swing.JLabel fileLabel = new javax.swing.JLabel("FileName :");
			fileLabel.setBounds(20, 10, 80, 25);
			frame.add(fileLabel);

			fileField = new javax.swing.JTextField();
			fileField.setBounds(100, 10, 200, 25);
			frame.add(fileField);

			javax.swing.JButton openBtn = new javax.swing.JButton("open");
			openBtn.setBounds(310, 10, 80, 25);
			frame.add(openBtn);

			openBtn.addActionListener(e -> {
				FileDialog fd = new FileDialog((java.awt.Frame) null, "Object 파일 열기", FileDialog.LOAD);
				fd.setVisible(true);
				if (fd.getFile() != null) {
					File selectedFile = new File(fd.getDirectory(), fd.getFile());
					fileField.setText(selectedFile.getName());
					load(selectedFile);
 				}
			});

			javax.swing.JPanel headerPanel = new javax.swing.JPanel(null);
			headerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("H (Header Record)"));
			headerPanel.setBounds(20, 45, 350, 120);
			frame.add(headerPanel);

			javax.swing.JLabel progLabel = new javax.swing.JLabel("Program name :");
			progLabel.setBounds(10, 20, 120, 20);
			headerPanel.add(progLabel);

			// progNameField is assumed to be a class field
			progNameField = new javax.swing.JTextField();
			progNameField.setBounds(140, 20, 180, 20);
			progNameField.setEditable(false);
			headerPanel.add(progNameField);

			javax.swing.JLabel startLabel = new javax.swing.JLabel("Start Address of Object Program :");
			startLabel.setBounds(10, 50, 250, 20);
			headerPanel.add(startLabel);

			startAddrField = new javax.swing.JTextField();
			startAddrField.setBounds(220, 50, 100, 20);
			startAddrField.setEditable(false);
			headerPanel.add(startAddrField);

			javax.swing.JLabel lengthLabel = new javax.swing.JLabel("Length of Program :");
			lengthLabel.setBounds(10, 80, 150, 20);
			headerPanel.add(lengthLabel);

			lengthField = new javax.swing.JTextField();
			lengthField.setBounds(160, 80, 160, 20);
			lengthField.setEditable(false);
			headerPanel.add(lengthField);

			javax.swing.JPanel endPanel = new javax.swing.JPanel(null);
			endPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("E (End Record)"));
			endPanel.setBounds(390, 45, 350, 80);
			frame.add(endPanel);

			javax.swing.JLabel endLabel = new javax.swing.JLabel("Address of First instruction in Object Program :");
			endLabel.setBounds(10, 20, 330, 20);
			endPanel.add(endLabel);

			endField = new javax.swing.JTextField();
			endField.setBounds(10, 40, 320, 20);
			endField.setEditable(false);
			endPanel.add(endField);

			javax.swing.JLabel startMemLabel = new javax.swing.JLabel("Start Address in Memory");
			startMemLabel.setBounds(390, 130, 200, 20);
			frame.add(startMemLabel);

			memStartField = new javax.swing.JTextField();
			memStartField.setBounds(590, 130, 100, 20);
			memStartField.setEditable(false);
			frame.add(memStartField);

			javax.swing.JLabel targetLabel = new javax.swing.JLabel("Target Address :");
			targetLabel.setBounds(390, 160, 120, 20);
			frame.add(targetLabel);

			javax.swing.JTextField targetField = new javax.swing.JTextField();
			targetField.setBounds(590, 160, 100, 20);
			targetField.setEditable(false);
			frame.add(targetField);

			javax.swing.JPanel regPanel = new javax.swing.JPanel(null);
			regPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Register"));
			regPanel.setBounds(20, 175, 350, 230);
			frame.add(regPanel);

			javax.swing.JLabel decLabel = new javax.swing.JLabel("Dec");
			decLabel.setBounds(120, 20, 50, 20);
			regPanel.add(decLabel);

			javax.swing.JLabel hexLabel = new javax.swing.JLabel("Hex");
			hexLabel.setBounds(210, 20, 50, 20);
			regPanel.add(hexLabel);

			String[] labels = {"A (#0)", "X (#1)", "L (#2)", "B (#3)", "S (#4)", "T (#5)", "F (#6)", "PC (#8)", "SW (#9)"};
			int[] regIndices = {0, 1, 2, 3, 4, 5, 6, 8, 9};

			// regDecFields and regHexFields are assumed to be class fields, initialized for at least 10 entries
			for (int i = 0; i < labels.length; i++) {
				javax.swing.JLabel rLabel = new javax.swing.JLabel(labels[i]);
				rLabel.setBounds(10, 45 + i * 20, 80, 20);
				regPanel.add(rLabel);

				if (regIndices[i] == 6 || regIndices[i] == 9) {
					// F (#6) and SW (#9): only one hex field
					regHexFields[regIndices[i]] = new javax.swing.JTextField();
					regHexFields[regIndices[i]].setBounds(100, 45 + i * 20, 170, 20);
					regHexFields[regIndices[i]].setEditable(false);
					regPanel.add(regHexFields[regIndices[i]]);
				} else {
					// A, X, L, B, S, T, PC: Dec + Hex
					regDecFields[regIndices[i]] = new javax.swing.JTextField();
					regDecFields[regIndices[i]].setBounds(100, 45 + i * 20, 80, 20);
					regDecFields[regIndices[i]].setEditable(false);
					regPanel.add(regDecFields[regIndices[i]]);

					regHexFields[regIndices[i]] = new javax.swing.JTextField();
					regHexFields[regIndices[i]].setBounds(190, 45 + i * 20, 80, 20);
					regHexFields[regIndices[i]].setEditable(false);
					regPanel.add(regHexFields[regIndices[i]]);
				}
			}

			javax.swing.JLabel instructionLabel = new javax.swing.JLabel("Instructions :");
			instructionLabel.setBounds(390, 210, 120, 20);
			frame.add(instructionLabel);

			// instructionList is assumed to be a class field (javax.swing.JList)
			instructionList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			javax.swing.JScrollPane instructionScroll = new javax.swing.JScrollPane(instructionList);
			instructionScroll.setBounds(390, 230, 180, 200);
			frame.add(instructionScroll);

			javax.swing.JLabel deviceLabel = new javax.swing.JLabel("사용중인 장치");
			deviceLabel.setBounds(580, 230, 120, 20);
			frame.add(deviceLabel);

			javax.swing.JTextField deviceField = new javax.swing.JTextField();
			deviceField.setBounds(580, 255, 120, 25);
			deviceField.setEditable(false);
			frame.add(deviceField);

			javax.swing.JButton stepBtn = new javax.swing.JButton("실행(1step)");
			stepBtn.setBounds(580, 300, 120, 25);
			frame.add(stepBtn);
			stepBtn.addActionListener(e -> {
			    oneStep();
			});

			javax.swing.JButton allBtn = new javax.swing.JButton("실행(all)");
			allBtn.setBounds(580, 335, 120, 25);
			frame.add(allBtn);
			allBtn.addActionListener(e -> {
				allStep();
			});

			javax.swing.JButton exitBtn = new javax.swing.JButton("종료");
			exitBtn.setBounds(580, 370, 120, 25);
			frame.add(exitBtn);

			javax.swing.JLabel logLabel = new javax.swing.JLabel("Log (명령어 수행 관련) :");
			logLabel.setBounds(20, 420, 250, 20);
			frame.add(logLabel);

			javax.swing.JTextArea logArea = new javax.swing.JTextArea();
			resourceManager.logArea = logArea;
			javax.swing.JScrollPane logScroll = new javax.swing.JScrollPane(logArea);
			logScroll.setBounds(20, 440, 720, 200);
			frame.add(logScroll);

			frame.setVisible(true);
		});
	}

	/**
	 * 메모리의 0x0000 ~ 0x03FF까지 16바이트씩 헥사 덤프 출력
	 */
	public void dumpMemory() {
		try {
			for (int i = 0; i < 0x1100; i += 16) {
				System.out.printf("%04X : ", i);
				for (int j = 0; j < 16; j++) {
					System.out.printf("%02X ", (int) resourceManager.memory[i + j] & 0xFF);
				}
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		VisualSimulator vs = new VisualSimulator(); // GUI will handle file loading
	}
}
