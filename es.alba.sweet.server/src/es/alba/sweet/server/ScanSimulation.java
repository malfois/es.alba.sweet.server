package es.alba.sweet.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import es.alba.sweet.base.ObservableProperty;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.communication.command.Name;
import es.alba.sweet.base.maths.Format;
import es.alba.sweet.base.output.Output;
import es.alba.sweet.base.scan.DataPoint;
import es.alba.sweet.base.scan.Header;
import es.alba.sweet.base.scan.ScanDataSet;
import es.alba.sweet.base.scan.ScanFileException;

public class ScanSimulation extends ObservableProperty implements Runnable {

	private String			filename;

	private Header			scanHeader;
	private String			xAxis;
	private List<String>	yAxis		= new ArrayList<>();
	private ScanDataSet		scanDataset;

	private List<String>	dataLines	= new ArrayList<>();

	private CommandStream	command;

	public ScanSimulation(String filename, String diagnostic) {
		this.filename = filename;
		this.yAxis.add(diagnostic);
	}

	private void setCommand(CommandStream command) {
		firePropertyChange("command", this.command, this.command = command);
	}

	@Override
	public void run() {
		try {
			List<String> diagnostics = readFile();
			readData(diagnostics);
			scanDataPoint();
			stopScan();
		} catch (IOException e) {
			Output.MESSAGE.error("es.alba.sweet.server.ScanSimulation.run", e.getMessage());
		} catch (ScanFileException e) {
			Output.MESSAGE.error("es.alba.sweet.server.ScanSimulation.run", e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			Output.MESSAGE.error("es.alba.sweet.server.ScanSimulation.run", e.getMessage());
		}
	}

	private List<String> readFile() throws IOException, ScanFileException {
		Path path = Paths.get(this.filename);
		List<String> lines = Files.lines(path).collect(Collectors.toList());

		List<String> headerFile = new ArrayList<>();
		lines.stream().filter(line -> line.startsWith("#") && !line.startsWith("#C")).forEach(a -> headerFile.add(a));

		String commandLine = lines.stream().filter(line -> line.startsWith("#S")).findFirst().orElse("");
		if (commandLine.length() == 0) throw new ScanFileException("Problem reading the header of " + this.filename + ". Cannot find the tag #S");

		String[] words = commandLine.split(" ");

		int scanID = Integer.parseInt(words[1]);
		this.xAxis = words[3];
		int numberOfPoints = Integer.parseInt(words[6]) + 1;

		String diagnosticLine = lines.stream().filter(p -> p.startsWith("#L")).findFirst().orElse("");
		if (commandLine.length() == 0) throw new ScanFileException("Problem reading the header of " + this.filename + ". Cannot find the tag #L");
		List<String> diagnostics = List.of(diagnosticLine.substring(2).split(" ")).stream().filter(p -> p.trim().length() != 0).collect(Collectors.toList());

		String[] commands = Arrays.copyOfRange(words, 2, words.length);
		String command = String.join(" ", commands);

		scanHeader = new Header();
		scanHeader.setCommand(command);
		scanHeader.setDiagnostics(diagnostics);
		scanHeader.setHeaderFile(headerFile);
		scanHeader.setMotor(this.xAxis);
		scanHeader.setNumberOfPoints(numberOfPoints);
		scanHeader.setScanID(scanID);
		scanHeader.setSelectedDiagnostic(yAxis.get(0));
		scanHeader.setFilename(this.filename);
		scanHeader.getPlotDiagnostics().add(yAxis.get(0));

		CommandStream commandStream = new CommandStream(CommandName.SCAN_HEADER, scanHeader.toJson());
		setCommand(commandStream);

		return diagnostics;

	}

	public void readData(List<String> diagnostics) throws IOException {
		Path path = Paths.get(this.filename);
		List<String> lines = Files.lines(path).collect(Collectors.toList());
		dataLines = new ArrayList<>();
		lines.stream().filter(line -> !line.startsWith("#")).forEach(a -> dataLines.add(a));

		int motorIndex = diagnostics.indexOf(this.xAxis);

		this.scanDataset = new ScanDataSet(diagnostics);

		for (String dataLine : dataLines) {
			String[] values = dataLine.split(" ");
			double x = Double.parseDouble(values[motorIndex]);

			Map<String, DataPoint> points = new HashMap<>();
			List<Double> data = new ArrayList<>();

			int nValues = values.length;
			for (int i = 0; i < nValues; i++) {
				double y = Double.parseDouble(values[i]);
				points.put(diagnostics.get(i), new DataPoint(x, y));
				data.add(y);
			}
			scanDataset.addPoint(points);
		}

	}

	public void scanDataPoint() throws InterruptedException {
		Format format = new Format();

		int numberOfPoints = scanHeader.getNumberOfPoints();
		for (int i = 0; i < numberOfPoints; i++) {
			ScanDataSet dataSet = this.scanDataset.sublist(yAxis, 0, i + 1);
			dataSet.derivate();
			dataSet.fit();

			List<String> stringValues = List.of(dataLines.get(i).split(" "));
			List<Double> values = stringValues.stream().map(m -> Double.parseDouble(m)).collect(Collectors.toList());
			String text = values.stream().map(m -> format.toText(m)).collect(Collectors.joining(",\t"));
			dataSet.setText(text);

			CommandStream commandStream = new CommandStream(CommandName.SCAN_DATA_POINT, dataSet.toJson());
			setCommand(commandStream);

			Thread.sleep(500);
		}
	}

	public void stopScan() {
		Name name = new Name();
		name.setName(this.getClass().toString());
		CommandStream commandStream = new CommandStream(CommandName.SCAN_STOPPED, name.toJson());
		setCommand(commandStream);
	}
}
