package es.alba.sweet.server;

import java.awt.TrayIcon.MessageType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import es.alba.sweet.base.ObservableProperty;
import es.alba.sweet.base.communication.ReadingLineException;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.communication.command.CommandStreamNullException;
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
					scanSimulation.addPropertyChangeListener(e -> sendMessage((CommandStream) e.getNewValue()));
					Thread thread = new Thread(scanSimulation);
					thread.start();
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
			e.printStackTrace();
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

	public void close() {
		// try to close the connection
		try {
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
}
