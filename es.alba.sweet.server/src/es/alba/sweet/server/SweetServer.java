package es.alba.sweet.server;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import es.alba.sweet.base.communication.Communication;
import es.alba.sweet.base.communication.command.CommandName;
import es.alba.sweet.base.communication.command.CommandStream;
import es.alba.sweet.base.communication.command.JsonException;
import es.alba.sweet.base.communication.command.Name;
import es.alba.sweet.base.configuration.Json;
import es.alba.sweet.base.constant.Application;
import es.alba.sweet.base.constant.Directory;
import es.alba.sweet.base.logger.LogFile;
import es.alba.sweet.base.output.Output;

public class SweetServer {

	private List<Client>			clients			= new ArrayList<>();

	private CommandPropertyListener	commandListener	= new CommandPropertyListener();

	public static void main(String[] args) {
		new SweetServer();
	}

	public SweetServer() {
		Runtime.getRuntime().addShutdownHook(new ExitServer());

		Output.MESSAGE.setApplication(Application.SERVER);

		LogFile.create(this.getClass().getName(), Directory.SERVER);

		Json<Communication> configuration = new Json<>(new Communication());

		ServerSocket serverSocket;
		// We need a try-catch because lots of errors can be thrown
		try {

			InetAddress ip = InetAddress.getLocalHost();
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Your current IP address : " + ip);
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Your current Host Name : " + ip.getHostAddress());
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Your current Local Host : " + InetAddress.getLocalHost().getHostName());

			File file = new File(".");
			String path = file.getCanonicalPath();
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Your current path : " + path);

			int port = this.findFreePort();

			configuration.getConfiguration().setPort(port);
			configuration.getConfiguration().setHostName(InetAddress.getLocalHost().getHostName());
			configuration.write();
			// configuration.changeFilePermission();
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Configuration file written : " + configuration.getFile().toString());

			serverSocket = new ServerSocket(port);
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "started at: " + new Date());
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer", "Waiting for client on port " + port);

			// Loop that runs server functions
			boolean listeningSocket = true;
			while (listeningSocket) {
				// Wait for a client to connect
				Socket clientSocket = serverSocket.accept();
				Output.MESSAGE.info("es.alba.sweet.server.SweetServer.SweetServer",
						"The client" + " " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " is connected ");

				Client client = new Client(clientSocket);
				client.addPropertyChangeListener(commandListener);
				Thread connectionThread = new Thread(client);
				connectionThread.setDaemon(true);
				connectionThread.start();
				this.clients.add(client);

			}
		} catch (IOException exception) {
			Output.MESSAGE.error("es.alba.sweet.server.SweetServer.SweetServer", exception.getMessage());
		}
	}

	private int findFreePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			socket.setReuseAddress(true);
			int port = socket.getLocalPort();
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore IOException on close()
			}
			return port;
		} catch (IOException e) {
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		throw new IllegalStateException("Could not find a free port");
	}

	/*
	 * 
	 * to broadcast a message to all Clients
	 */
	public synchronized void broadcast(CommandStream command) {
		// we loop in reverse order in case we would have to remove a Client because it
		// has disconnected
		for (int i = this.clients.size(); --i >= 0;) {
			Client client = this.clients.get(i);
			// try to write to the Client if it fails remove it from the list
			if (!client.sendMessage(command)) {
				this.clients.remove(i);
				client.close();
			}
		}
	}

	private class CommandPropertyListener implements PropertyChangeListener {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			CommandStream commandStream = (CommandStream) evt.getNewValue();
			CommandName command = commandStream.getCommandName();
			switch (command) {
			case MESSAGE:
				broadcast(commandStream);
				break;
			case STOP_CLIENT:
				Name name;
				try {
					name = new Name(commandStream.getCommandArgument());
					String clientName = name.get();
					List<Client> removedClients = new ArrayList<>();
					clients.stream().filter(p -> p.getName().equals(clientName)).forEach(a -> {
						a.close();
						a.getThread().interrupt();
						removedClients.add(a);
						Output.MESSAGE.info("es.alba.sweet.server.SweetServer.CommandPropertyListener.propertyChange", "The client" + " " + a.getName() + " is disconnected ");
					});
					clients.removeAll(removedClients);
					Output.MESSAGE.info("es.alba.sweet.server.SweetServer.CommandPropertyListener.propertyChange", clients.size() + " clients left");
				} catch (JsonException e) {
					e.printStackTrace();
				}
				break;
			case EXIT_SERVER:
				broadcast(commandStream);
				break;
			default:
				break;
			}
		}
	}

	private class ExitServer extends Thread {

		public void run() {
			Output.MESSAGE.info("es.alba.sweet.server.SweetServer.ExitServer.run", "Exiting");
			for (Client client : clients) {
				client.close();
			}
			clients.clear();

			Json<Communication> configuration = new Json<>(new Communication());
			try {
				Output.MESSAGE.info("es.alba.sweet.server.SweetServer.ExitServer.run", "Deleting the file " + configuration.getFile().toPath());
				Files.delete(configuration.getFile().toPath());
			} catch (IOException e) {
				Output.MESSAGE.info("es.alba.sweet.server.SweetServer.ExitServer.run", "Error deleting file " + configuration.getFile().toPath() + " " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
}
