package co.watly.server.rest.api;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

import org.apache.log4j.Logger;

import co.watly.server.rest.events.Event;
import co.watly.server.rest.events.MultipleRfidDetected;
import co.watly.server.rest.events.RfidDetected;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.spi.resource.Singleton;

@Path("/events")
@Singleton
public class EventsAPI {

	static Logger logger = Logger.getLogger(EventsAPI.class);

	DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	Properties properties;
	BufferedWriter out;

	public EventsAPI() {
		properties = new Properties();
		try {
			properties.load(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("/watly.properties"));
			out = new BufferedWriter(new FileWriter(
					(String) properties.get("access.log"), true));
		} catch (IOException e) {
			logger.fatal("unable to laod the properties file");
		}
	}

	@POST
	@Path("/{Name}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postEvent(@PathParam("Name") String name, String json) {
		ObjectMapper mapper = new ObjectMapper();
		Event event = null;
		try {
			Class evtClass = Class.forName("co.watly.server.rest.events."
					+ name);
			event = (Event) mapper.readValue(json, evtClass);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		String output = "Received : " + event.getName() + " on " + new Date();
		logger.info(output);
		return Response.status(200).entity(output).build();
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postEvent(String json) {
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> values = null;
		String name = null;
		Event event = null;
		try {
			values = mapper.readValue(json, Map.class);
			name = (String) values.get("Name");
			Class evtClass = Class.forName("co.watly.server.rest.events."
					+ name);
			event = (Event) mapper.readValue(json, evtClass);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		String output = "Received : " + event.getName();
		logger.info(output);
		return Response.status(200).entity(output).build();
	}

	@POST
	@Path("/RfidDetected")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postRfidDetected(String json) {
		ObjectMapper mapper = new ObjectMapper();
		RfidDetected event = null;
		String output;
		try {
			event = (RfidDetected) mapper.readValue(json, RfidDetected.class);
			output = format.format(event.getDetectionTime())
					+ " - RFID tag detected: " + event.getTitle();
			out.write(output + "\n");
			out.flush();
			logger.info(output);
//			controlLeds("G");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return Response.status(200).entity(output).build();
	}

	@POST
	@Path("/MultipleRfidDetected")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postMultipleRfid(String json) {
		ObjectMapper mapper = new ObjectMapper();
		MultipleRfidDetected event = null;
		String output;
		try {
			event = (MultipleRfidDetected) mapper.readValue(json,
					MultipleRfidDetected.class);
			output = format.format(event.getDetectionTime())
					+ " - Double RFID detection within last 12 hours: "
					+ event.getTitle();
			logger.info(output);
			out.write(output + "\n");
			out.flush();
//			controlLeds("R");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return Response.status(200).entity(output).build();
	}

	private void controlLeds(final String state) {
		new Thread() {
			public void run() {
				SSHClient ssh = null;
				Session session = null;
				try {
					ssh = new SSHClient();
					ssh.loadKnownHosts();
					ssh.connect(properties.getProperty("rpi.ip"));
					ssh.authPassword(properties.getProperty("rpi.username"), properties.getProperty("rpi.password").toCharArray());
					session = ssh.startSession();
					String updatecmd = properties.getProperty("rpi.updatecmd");
					updatecmd = MessageFormat.format(updatecmd, state);
					Command cmd = session.exec(updatecmd);
				} catch (IOException e) {
					throw new RuntimeException(e);
				} finally {
					try {
						if(session != null) {
							session.close();
						}
						if(ssh != null) {
							if(ssh.isConnected()) {
								ssh.disconnect();
							}
							ssh.close(); 
						}
					} catch(IOException e) {
						logger.error(e);
					}
				}
			}
		}.start();
	}
}
