/*
 * Created on 06.03.2007
 */
package hudson.plugins.skype.im.transport;

import hudson.model.Label;
import hudson.model.Node;
import hudson.plugins.im.AbstractIMConnection;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionListener;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMPresence;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.plugins.skype.im.transport.callables.SkypeChatCallable;
import hudson.plugins.skype.im.transport.callables.SkypeGroupChatCallable;
import hudson.plugins.skype.im.transport.callables.SkypeMoodCallable;
import hudson.plugins.skype.im.transport.callables.SkypeSetupCallable;
import hudson.plugins.skype.im.transport.callables.SkypeVerifyUserCallable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.event.ConnectionListener;

import jenkins.model.Jenkins;

import org.springframework.util.Assert;

import com.skype.Profile;

/**
 * Smack-specific implementation of {@link IMConnection}.
 *
 * @author kutzi
 * @author Uwe Schaefer (original author)
 */
class SkypeIMConnection extends AbstractIMConnection {

    private static final Logger LOGGER = Logger.getLogger(SkypeIMConnection.class.getName());
    private final String botCommandPrefix;
    private final String[] groupChats;
    private IMPresence impresence;
    private String imStatusMessage;
    private final SkypePublisherDescriptor desc;
    private Node skypeSlave = null;

    SkypeIMConnection(SkypePublisherDescriptor desc) throws IMException {
        super(desc);
        Assert.notNull(desc, "Parameter 'desc' must not be null.");
        this.desc = desc;

        this.botCommandPrefix = desc.getCommandPrefix();
        if (desc.getInitialGroupChats() != null) {
            this.groupChats = desc.getInitialGroupChats().trim().split("\\s");
        } else {
            this.groupChats = new String[0];
        }
        this.impresence = desc.isExposePresence() ? IMPresence.AVAILABLE : IMPresence.UNAVAILABLE;
    }

    public boolean connect() {
        lock();
        boolean connected = false;
        try {
            try {
                if (!isConnected()) {
                    if (createConnection()) {
                        LOGGER.info("Connected to Skype");

                        // I've read somewhere that status must be set, before one can do anything other
                        // Don't know if it's true, but can't hurt, either.
                        sendPresence();
                        connected = true;

                    } else {
                        LOGGER.warning("Cannot connect to Skype");
                    }
                }

            } catch (final Exception e) {
                e.printStackTrace();
                LOGGER.warning(ExceptionHelper.dump(e));
            }
        } finally {
            unlock();
        }
        return connected;
    }

    public void close() {
        lock();
        try {
        } finally {
            unlock();
        }
    }

    private synchronized boolean createConnection() throws IMException {
        boolean result = false;
        Label labelToFind = Label.get("skype");
        if (labelToFind == null || Jenkins.getInstance().getNodes() == null || labelToFind.isEmpty()) {
            LOGGER.info("Cannot find nodes with label skype. Trying to connect on master.");
            Node master = Jenkins.getInstance();
            result = verifySlave(master);
        } else {
            for (Node node : labelToFind.getNodes()) {
                if (verifySlave(node)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private boolean verifySlave(Node slave) {
        Assert.notNull(slave, "Parameter 'slave' must not be null.");
        VirtualChannel channel = slave.getChannel();
        if (channel == null) {
            LOGGER.info(slave.getDisplayName() + " is offline.");
            return false;
        }
        try {
            if (channel.call(new SkypeSetupCallable(groupChats, botCommandPrefix))) {
                if (channel instanceof Channel) {
                    ((Channel) channel).addListener(new Channel.Listener() {

                        @Override
                        public void onClosed(Channel channel, IOException cause) {
                            skypeSlave = null;
                        }
                    });
                }
                skypeSlave = slave;
                LOGGER.info("Connected to skype on " + slave.getDisplayName());
                return true;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    public void send(final IMMessageTarget target, final String text)
            throws IMException {
        Assert.notNull(target, "Parameter 'target' must not be null.");
        Assert.notNull(text, "Parameter 'text' must not be null.");
        LOGGER.info("Send to " + target + " val " + text);
        try {
            // prevent long waits for lock
            if (!tryLock(5, TimeUnit.SECONDS)) {
                return;
            }
            try {
                SkypeChatCallable msgCallable;
                if (target instanceof GroupChatIMMessageTarget) {
                    LOGGER.fine("Group:" + target);
                    msgCallable = new SkypeGroupChatCallable(target.toString(), text);
                } else {
                    verifyUser(target);
                    //final ChatMessage chat = skypeServ.chat(target.toString(), text);
                    msgCallable = new SkypeChatCallable(new String[]{target.toString()}, text);

                }
                try {
                    getChannel().call(msgCallable);
                } catch (IOException ex) {
                    throw new SkypeIMException(ex);
                } catch (InterruptedException ex) {
                    throw new SkypeIMException(ex);
                }
            } catch (final SkypeIMException e) {
                // server unavailable ? Target-host unknown ? Well. Just skip this
                // one.
                LOGGER.warning(ExceptionHelper.dump(e));
                // TODO ? tryReconnect();
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // ignore
        }
    }

    public VirtualChannel getChannel() {
        if (skypeSlave != null && skypeSlave.toComputer() != null && skypeSlave.toComputer().isOnline()) {
            return skypeSlave.getChannel();
        } else {
            return null;
        }
    }

    private void verifyUser(final IMMessageTarget target) throws SkypeIMException {
        try {
            SkypeVerifyUserCallable callable = new SkypeVerifyUserCallable(target.toString());
            String result = getChannel().call(callable);
            if (result != null) {
                throw new SkypeIMException("Could not find user " + target);
            }
        } catch (IOException ex) {
            throw new SkypeIMException(ex);
        } catch (InterruptedException ex) {
            throw new SkypeIMException(ex);
        }
    }

    /**
     * This implementation ignores the new presence if
     * {@link JabberPublisherDescriptor#isExposePresence()} is false.
     */
    public void setPresence(final IMPresence impresence, String statusMessage)
            throws IMException {
        Assert.notNull(impresence, "Parameter 'impresence' must not be null.");
        if (this.desc.isExposePresence()) {
            this.impresence = impresence;
            this.imStatusMessage = statusMessage;
            sendPresence();
        } else {
            // Ignore new presence.
            // Don't re-send presence, either. It would result in disconnecting from
            // all joined group chats
        }
    }

    private void sendPresence() {

        try {
            // prevent long waits for lock
            if (!tryLock(5, TimeUnit.SECONDS)) {
                return;
            }
            try {
                if (!isConnected()) {
                    return;
                }
                Profile.Status presence;
                switch (this.impresence) {
                    case AVAILABLE:
                        presence = Profile.Status.ONLINE;
                        break;

                    case OCCUPIED:
                        presence = Profile.Status.AWAY;
                        break;

                    case DND:
                        presence = Profile.Status.DND;
                        break;

                    case UNAVAILABLE:
                        presence = Profile.Status.OFFLINE;
                        break;

                    default:
                        presence = Profile.Status.UNKNOWN;
                }
                SkypeMoodCallable callable = new SkypeMoodCallable(this.imStatusMessage, presence);
                try {
                    getChannel().call(callable);
                } catch (IOException ex) {
                    Logger.getLogger(SkypeIMConnection.class.getName()).log(Level.SEVERE, null, ex);
                }
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // ignore
        }
    }

    public boolean isConnected() {
        lock();
        boolean conn = false;
        try {
            conn = getChannel() != null;
        } finally {
            unlock();
        }
        return conn;
    }

    private final Map<IMConnectionListener, ConnectionListener> listeners =
            new ConcurrentHashMap<IMConnectionListener, ConnectionListener>();

    public void addConnectionListener(final IMConnectionListener listener) {
        lock();
        try {
        } finally {
            unlock();
        }
    }

    public void removeConnectionListener(IMConnectionListener listener) {
        lock();
        try {
        } finally {
            unlock();
        }
    }
}
