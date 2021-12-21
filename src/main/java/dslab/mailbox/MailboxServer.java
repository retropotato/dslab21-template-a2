package dslab.mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.entity.Mail;
import dslab.util.Config;
import dslab.util.UserMailBox;


public class MailboxServer implements IMailboxServer, Runnable {

    private Map<String, UserMailBox> userMailBoxMap;
    private BlockingQueue<Mail> mailQueue;

    private final Config config;
    private final Config users;
    private final ExecutorService executor;
    private final Shell shell;

    private ServerSocket maServer;
    private ServerSocket mtServer;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param users       the component users
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, Config users, InputStream in, PrintStream out) {

        this.config = config;
        this.users = users;

        // instantiate HashMap for user-list and Blocked Queue
        this.userMailBoxMap = new ConcurrentHashMap<>();
        for (String username : users.listKeys()) {
            userMailBoxMap.put(username,new UserMailBox());
        }

        this.mailQueue = new LinkedBlockingDeque<>();

        // instantiate ThreadPoolExecutor
        this.executor = Executors.newFixedThreadPool(10);

        // instantiate and setup I/O-Shell
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");

    }

    @Override
    public void run() {


        // run MtServerListener in new Thread
        executor.execute(() -> {
            try {

                mtServer = new ServerSocket(config.getInt("dmtp.tcp.port"));

                while (true) {
                    // run new MaClientSocket in new Thread
                    Socket maSocket = mtServer.accept();
                    executor.execute(new DmtpMailThreadListener(maSocket,config,users,userMailBoxMap));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        // run MaServerListener in new Thread
        executor.execute(() -> {
            try {

                maServer = new ServerSocket(config.getInt("dmap.tcp.port"));

                while (true) {
                    // run new MaClientSocket in new Thread
                    Socket maSocket = maServer.accept();
                    executor.execute(new DmapThreadListener(maSocket,users,userMailBoxMap));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // run Shell
        shell.run();

    }

    private void persistMail(Mail mail) {
    }

    @Command
    @Override
    public void shutdown() {

        // closing MTServer
        if (mtServer != null) {
            try {
                mtServer.close();
            } catch (IOException e) {
                System.err.println("Error while closing message transfer server socket: " + e.getMessage());
            }
        }

        // closing MAServer
        if (maServer != null) {
            try {
                maServer.close();
            } catch (IOException e) {
                System.err.println("Error while closing message access server socket: " + e.getMessage());
            }
        }

        executor.shutdown();
        // closing Shell by throwing Exception
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }

}