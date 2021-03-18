package es.alba.sweet.server;

import java.awt.TrayIcon.MessageType;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

import es.alba.sweet.base.ObservableProperty;
import es.alba.sweet.base.communication.ReadingLineException;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.communication.command.CommandStreamNullException;
import es.alba.sweet.base.communication.command.FunctionSimulationArgument;
import es.alba.sweet.base.communication.command.JsonException;
import es.alba.sweet.base.communication.command.Name;
import es.alba.sweet.base.communication.command.ScanSimulationParameter;
import es.alba.sweet.base.constant.Application;
import es.alba.sweet.base.output.Message;
import es.alba.sweet.base.output.Output;

public class Client extends ObservableProperty implements Runnable {

	private String			name;

	private Socket			clientSocket;
	private String			clientName;

	PrintWriter				toClient;
	BufferedReader			fromClient;

	private CommandStream	command;

	private boolean			running	= true;

	public Client(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;
		this.clientName = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
		this.name = this.clientName;

		fromClient = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
		toClient = new PrintWriter(this.clientSocket.getOutputStream(), true);

	}

	public void setCommand(CommandStream command) {
		firePropertyChange("command", this.command, this.command = command);
	}

	@Override
	public void run() {

		// Create the streams
		try {

			// Tell the client that he/she has connected
			Message message = new Message(MessageType.INFO, Application.SERVER, "es.alba.sweet.server.Client.run", this.name + " is now connected");
			CommandStream commandConnect = new CommandStream(CommandName.MESSAGE, message.toJson());
			this.sendMessage(commandConnect);

			while (running) {
				// This will wait until a line of text has been sent
				CommandStream command = new CommandStream(readLine());

				Output.MESSAGE.info("es.alba.sweet.server.Client.run", "RECEIVED from " + this.name + " - " + command.toString());
				CommandName commandName = command.getCommandName();
				System.out.println(commandName);
				switch (commandName) {
				case NAME:
					Name name = new Name(command.getCommandArgument());
					this.name = name.get();

					message = new Message(MessageType.INFO, Application.SERVER, "es.alba.sweet.server.Client.run", this.name + " is now connected");
					CommandStream commandInformation = new CommandStream(CommandName.MESSAGE, message.toJson());
					setCommand(commandInformation);
					break;
				case STOP_CLIENT:
					CommandStream commandStop = new CommandStream(CommandName.STOP_CLIENT, null);
					setCommand(commandStop);
					break;
				case SCAN_SIMULATION:
					ScanSimulationParameter parameter = new ScanSimulationParameter(command.getCommandArgument());
					ScanSimulation scanSimulation = new ScanSimulation(parameter.getFilename(), parameter.getDiagnostics());
					scanSimulation.addPropertyChangeListener(new ScanSimulationPropertyListener());
					Thread threadSim = new Thread(scanSimulation);
					threadSim.setName(ScanSimulation.class.toString());
					threadSim.start();
					break;
				case SCAN_FUNCTION_SIMULATION:
					FunctionSimulationArgument argument = new FunctionSimulationArgument(command.getCommandArgument());
					FunctionSimulation functionSimulation = new FunctionSimulation(argument);
					functionSimulation.addPropertyChangeListener(new FunctionSimulationPropertyListener());
					Thread threadFunc = new Thread(functionSimulation);
					threadFunc.setName(FunctionSimulation.class.toString());
					threadFunc.start();
					break;
				case SCAN_STOPPED:
					closeSimulationThread();
					break;
				default:
					break;
				}
			}

		} catch (CommandStreamNullException e) {
			try {
				this.close();
				Output.MESSAGE.warning("es.alba.sweet.server.Client.run", "The client" + " " + this.name + " is disconnected ");
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		} catch (ReadingLineException e) {
			this.close();
			Name name = new Name();
			name.setName(this.name);
			CommandStream commandStop = new CommandStream(CommandName.STOP_CLIENT, name.toJson());
			setCommand(commandStop);
			// e.printStackTrace();
		} catch (JsonException e) {
			e.printStackTrace();
		}

	}

	public String getName() {
		return this.name;
	}

	/*
	 * Write a String to the Client output stream
	 */
	public boolean sendMessage(CommandStream command) {
		// if Client is still connected send the message to it
		if (!this.clientSocket.isConnected()) {
			close();
			return false;
		}
		Output.MESSAGE.info("es.alba.sweet.server.Client.sendMessage", " SENDING to " + this.name + " - " + command.toString());
		toClient.println(command.toString());

		return true;
	}

	private void closeSimulationThread() {
		List<String> simulationNames = SimulationName.get();
		Thread.getAllStackTraces().keySet().stream().filter(p -> simulationNames.contains(p.getName())).forEach(a -> a.interrupt());
	}

	public void close() {
		// try to close the connection
		try {
			closeSimulationThread();
			if (this.clientSocket != null) {
				this.clientSocket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Thread getThread() {
		return Thread.currentThread();
	}

	private String readLine() throws ReadingLineException {
		try {
			String line = fromClient.readLine();
			return line;
		} catch (IOException e) {
			throw new ReadingLineException("Error reading data from " + this.name);
		}
	}

	private class ScanSimulationPropertyListener implements PropertyChangeListener {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			CommandStream commandStream = (CommandStream) evt.getNewValue();
			CommandName command = commandStream.getCommandName();
			switch (command) {
			case SCAN_STOPPED:
				Name name;
				try {
					name = new Name(commandStream.getCommandArgument());
					String threadName = name.get();
					Thread thread = Thread.getAllStackTraces().keySet().stream().filter(p -> p.getName().equals(threadName)).findFirst().orElse(null);
					if (thread != null) thread.interrupt();
				} catch (JsonException e) {
					e.printStackTrace();
				}
			default:
				sendMessage(commandStream);
				break;
			}
		}
	}

	private class FunctionSimulationPropertyListener implements PropertyChangeListener {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			CommandStream commandStream = (CommandStream) evt.getNewValue();
			CommandName command = commandStream.getCommandName();
			switch (command) {
			case SCAN_STOPPED:
				Name name;
				try {
					name = new Name(commandStream.getCommandArgument());
					String threadName = name.get();
					Thread thread = Thread.getAllStackTraces().keySet().stream().filter(p -> p.getName().equals(threadName)).findFirst().orElse(null);
					if (thread != null) thread.interrupt();
				} catch (JsonException e) {
					e.printStackTrace();
				}
			default:
				sendMessage(commandStream);
				break;
			}
		}
	}

}
