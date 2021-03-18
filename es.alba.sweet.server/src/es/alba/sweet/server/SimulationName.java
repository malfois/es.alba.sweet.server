package es.alba.sweet.server;

import java.util.ArrayList;
import java.util.List;

public class SimulationName {

	private static List<String> list = create();

	private static List<String> create() {
		List<String> list = new ArrayList<>();
		list.add(ScanSimulation.class.toString());
		list.add(FunctionSimulation.class.toString());
		return list;
	}

	public static List<String> get() {
		return list;
	}
}
