package es.alba.sweet.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import es.alba.sweet.base.ObservableProperty;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.scan.Header;
import es.alba.sweet.base.scan.ScanFileException;

public class ScanSimulation extends ObservableProperty implements Runnable {

	private String			filename;
	private String			diagnostic;

	private CommandStream	command;

	public ScanSimulation(String filename, String diagnostic) {
		this.filename = filename;
		this.diagnostic = diagnostic;
	}

	private void setCommand(CommandStream command) {
		firePropertyChange("command", this.command, this.command = command);
	}

	@Override
	public void run() {
		try {
			readFile();
		} catch (IOException | ScanFileException e) {
			e.printStackTrace();
			return;
		}
	}

	private void readFile() throws IOException, ScanFileException {
		Path path = Paths.get(this.filename);
		List<String> lines = Files.lines(path).collect(Collectors.toList());

		List<String> headerFile = new ArrayList<>();
		lines.stream().filter(line -> line.startsWith("#") && !line.startsWith("#C")).forEach(a -> headerFile.add(a));

		String commandLine = lines.stream().filter(line -> line.startsWith("#S")).findFirst().orElse("");
		if (commandLine.length() == 0) throw new ScanFileException("Problem reading the header of " + this.filename + ". Cannot find the tag #S");

		String[] words = commandLine.split(" ");

		int scanID = Integer.parseInt(words[1]);
		String motor = words[3];
		int numberOfPoints = Integer.parseInt(words[6]) + 1;

		String diagnosticLine = lines.stream().filter(p -> p.startsWith("#L")).findFirst().orElse("");
		if (commandLine.length() == 0) throw new ScanFileException("Problem reading the header of " + this.filename + ". Cannot find the tag #L");
		List<String> diagnostics = List.of(diagnosticLine.substring(2).split(" ")).stream().filter(p -> p.trim().length() != 0).collect(Collectors.toList());

		String[] commands = Arrays.copyOfRange(words, 2, words.length);
		String command = String.join(" ", commands);

		Header scanHeader = new Header();
		scanHeader.setCommand(command);
		scanHeader.setDiagnostics(diagnostics);
		scanHeader.setHeaderFile(headerFile);
		scanHeader.setMotor(motor);
		scanHeader.setNumberOfPoints(numberOfPoints);
		scanHeader.setScanID(scanID);
		scanHeader.setSelectedDiagnostic(diagnostic);
		scanHeader.setFilename(this.filename);

		CommandStream commandStream = new CommandStream(CommandName.SCAN_HEADER, scanHeader.toJson());
		setCommand(commandStream);

		List<String> dataLines = new ArrayList<>();
		lines.stream().filter(line -> !line.startsWith("#")).forEach(a -> dataLines.add(a));
		double[][] data = new double[diagnostics.size()][numberOfPoints];
		for (int i = 0; i < numberOfPoints; i++) {
			String[] dataLine = dataLines.get(i).split(" ");
			for (int j = 0; j < data.length; j++) {
				data[j][i] = Double.parseDouble(dataLine[j].trim());
			}
		}

		for (int i = 0; i < diagnostics.size(); i++) {
			for (int j = 0; j < numberOfPoints; j++) {
				System.out.print(data[i][j] + " ");
			}
			System.out.println();
		}
	}
}
