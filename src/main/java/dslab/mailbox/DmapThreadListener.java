package dslab.mailbox;

import dslab.entity.Mail;
import dslab.util.Config;
import dslab.util.UserMailBox;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

public class DmapThreadListener extends Thread {

    private final Map<String, UserMailBox> mailBoxes;

    private final Config users;
    private final Socket socket;

    private String username = null;
    private boolean protocolError = false;

    public DmapThreadListener(Socket socket, Config users, Map<String, UserMailBox> mailBoxes) {

        this.socket = socket;
        this.users = users;
        this.mailBoxes = mailBoxes;

    }

    public void run() {

        while (true) {
            try {

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream());

                // response for valid connection
                writer.println("ok DMAP");
                writer.flush();

                String request;
                while ((request = reader.readLine()) != null) {

                    String[] parts = request.split("\\s");
                    String response = "ok";

                    if (parts.length == 0) continue;

                    switch (parts[0]) {

                        case "login":
                            if (parts.length != 3) protocolError = true;
                            else {
                                if (users.containsKey(parts[1]))
                                        if (users.getString(parts[1]).equals(parts[2]))
                                            username = parts[1];
                                        else response = "error wrong password";
                                else response = "error unknown user";
                            }
                            break;
                        case "list":
                            if (parts.length != 1) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else response = mailBoxes.get(username).listMailsAsString();
                            break;
                        case "show":
                            if (parts.length != 2) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else {
                                try{
                                    int id = Integer.parseInt(parts[1]);
                                    Mail mail = mailBoxes.get(username).getMail(id);
                                    if (mail == null) response = "error unknown message id";
                                    else response = mail.toString();
                                    break;
                                } catch (NumberFormatException e){
                                    protocolError = true; break;
                                }
                            }
                            break;
                        case "delete":
                            try {
                                if (parts.length != 2) protocolError = true;
                                else if (username == null) response = "error not logged in";
                                else {
                                    int id = Integer.parseInt(parts[1]);
                                    if (!mailBoxes.get(username).deleteMail(id))
                                        response = "error unknown message id";
                                }
                                break;
                            } catch (NumberFormatException e){
                                protocolError = true; break;
                            }
                        case "logout":
                            if (parts.length != 1) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else username = null;
                            break;
                        case "quit":
                            writer.println("ok bye");
                            writer.flush();
                            socket.close();
                            break;
                        default:
                            protocolError = true;
                            break;
                    }

                    if(protocolError){
                        writer.println("error protocol error");
                        writer.flush();
                        socket.close();
                    }

                    writer.println(response);
                    writer.flush();

                }

            } catch (SocketException e) {
                System.out.println("SocketException while handling socket: " + e.getMessage());
                break;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Ignored because we cannot handle it
                    }
                }

            }

        }
    }
}
