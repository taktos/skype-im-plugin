package hudson.plugins.skype.im.transport;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionProvider;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.slaves.ComputerListener;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Jabber implementation of an {@link IMConnectionProvider}.
 *
 * @author Uwe Schaefer
 * @author kutzi
 */
final class SkypeIMConnectionProvider extends IMConnectionProvider {
    private static final Logger LOGGER = Logger.getLogger(SkypeIMConnectionProvider.class.getName());

    private SkypeIMConnectionProvider() {
        super();
        init();
    }

    private static final SkypeIMConnectionProvider INSTANCE = new SkypeIMConnectionProvider();

    static SkypeIMConnectionProvider getInstance() {
        return INSTANCE;
    }

    static void setDesc(IMPublisherDescriptor desc) throws IMException {
        synchronized (INSTANCE) {
            INSTANCE.setDescriptor(desc);
        }
    }

    @Override
    public IMConnection createConnection() throws IMException {
        synchronized (INSTANCE) {
            if (getDescriptor() == null) {
                throw new RuntimeException("No descriptor");
            }
            IMConnection imConnection = new SkypeIMConnection((SkypePublisherDescriptor) getDescriptor());
            if (imConnection.connect()) {
                return imConnection;
            }
        }
        throw new IMException("Connection failed");
    }

    @Extension
    public static class SkypeComputerListener extends ComputerListener {
        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (c.getNode().getLabelString().contains("skype")) {
                SkypeIMConnectionProvider.getInstance().connectionBroken(null);
                LOGGER.fine("Node " + c.getName() + " came online, retry");
            }
        }
    }
}
