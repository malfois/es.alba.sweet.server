package es.alba.sweet.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import es.alba.sweet.base.ObservableProperty;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.communication.command.FunctionSimulationArgument;
import es.alba.sweet.base.communication.command.Name;
import es.alba.sweet.base.maths.Format;
import es.alba.sweet.base.scan.DataPoint;
import es.alba.sweet.base.scan.Header;
import es.alba.sweet.base.scan.ScanDataSet;

public class FunctionSimulation extends ObservableProperty implements Runnable {

	private FunctionSimulationArgument	function;

	private Header						scanHeader;
	private String						xAxis;
	private List<String>				yAxis		= new ArrayList<>();
	private ScanDataSet					scanDataset;

	private List<String>				dataLines	= new ArrayList<>();

	private CommandStream				command;

	public FunctionSimulation(FunctionSimulationArgument function) {
		this.function = function;
		this.xAxis = "x";
		this.yAxis.add("y");

	}

	private void setCommand(CommandStream command) {
		firePropertyChange("command", this.command, this.command = command);
	}

	@Override
	public void run() {
		try {
			List<String> diagnostics = createHeader();
			readData(diagnostics);
			scanDataPoint();
			stopScan();

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

	public void readData(List<String> diagnostics) {
		this.scanDataset = new ScanDataSet(diagnostics);

		int numberOfPoints = this.scanHeader.getNumberOfPoints();
		this.function.calculate(numberOfPoints);

		String yName = this.yAxis.get(0);

		for (int i = 0; i < numberOfPoints; i++) {
			Map<String, DataPoint> points = new HashMap<>();

			DataPoint point = this.function.getValue(i);
			points.put(yName, point);
			scanDataset.addPoint(points);
			dataLines.add(String.valueOf(point.getX()) + " " + String.valueOf(point.getY()));
		}
	}

	private List<String> createHeader() {
		int scanID = 1;
		int numberOfPoints = 31;

		List<String> diagnostics = new ArrayList<>();
		diagnostics.add(xAxis);
		diagnostics.addAll(yAxis);

		String command = this.function.toString();

		scanHeader = new Header();
		scanHeader.setCommand(command);
		scanHeader.setDiagnostics(diagnostics);
		scanHeader.setHeaderFile(new ArrayList<>());
		scanHeader.setMotor(this.xAxis);
		scanHeader.setNumberOfPoints(numberOfPoints);
		scanHeader.setScanID(scanID);
		scanHeader.setSelectedDiagnostic(yAxis.get(0));
		scanHeader.setFilename("No filename");
		scanHeader.getPlotDiagnostics().add(yAxis.get(0));

		CommandStream commandStream = new CommandStream(CommandName.SCAN_HEADER, scanHeader.toJson());
		setCommand(commandStream);

		return diagnostics;

	}

	public void stopScan() {
		Name name = new Name();
		name.setName(this.getClass().toString());
		CommandStream commandStream = new CommandStream(CommandName.SCAN_STOPPED, name.toJson());
		setCommand(commandStream);
	}

}
