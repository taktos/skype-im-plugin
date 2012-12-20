package hudson.plugins.skype.im.transport;

import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import java.util.logging.Logger;

import com.skype.ChatMessage;
import com.skype.ChatMessageListener;
import com.skype.SkypeException;


/**
 * Wraps an {@link IMMessageListener} in a Smack {@link PacketListener}.
 *
 * @author kutzi
 */
public class SkypeMessageListenerAdapter implements ChatMessageListener {
    private static final Logger LOGGER = Logger.getLogger(SkypeMessageListenerAdapter.class.getName());

    private final IMMessageListener listener;


    public SkypeMessageListenerAdapter(IMMessageListener listener) {
        this.listener = listener;
    }



    public void chatMessageReceived(ChatMessage receivedChatMessage) throws SkypeException {
        IMMessage imMessage = new IMMessage(receivedChatMessage.getSenderId(),
        		receivedChatMessage.getId(), receivedChatMessage.getContent(),
        		true);

        listener.onMessage(imMessage);
    }

    public void chatMessageSent(ChatMessage sentChatMessage) throws SkypeException {
        LOGGER.info("Sent " + sentChatMessage);
    }
}
